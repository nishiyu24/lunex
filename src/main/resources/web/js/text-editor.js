import {Templates} from './templates.js';

export const TextEditor = {
    editorInstance: null,
    apiSuggestions: [],
    currentFontSize: 14,
    errorDecorations: [],
    mcLang: "en_us",

    standardLuaApis: [
        { label: 'print', insertText: 'print(${1:text})', desc: 'コンソールにテキストを出力します', descEn: 'Outputs text to the console' },
        { label: 'tostring', insertText: 'tostring(${1:value})', desc: '値を文字列に変換します', descEn: 'Converts a value to a string' },
        { label: 'tonumber', insertText: 'tonumber(${1:value})', desc: '文字列を数値に変換します', descEn: 'Converts a string to a number' },
        { label: 'math.random', insertText: 'math.random(${1:min},${2:max})', desc: '乱数を生成します', descEn: 'Generates a random number' },
        { label: 'math.floor', insertText: 'math.floor(${1:value})', desc: '小数を切り捨てます', descEn: 'Rounds down a float' },
        { label: 'table.insert', insertText: 'table.insert(${1:list},${2:value})', desc: 'リストに要素を追加します', descEn: 'Inserts an element into a list' },
        { label: 'table.remove', insertText: 'table.remove(${1:list},${2:index})', desc: 'リストから要素を削除します', descEn: 'Removes an element from a list' }
    ],

    init(suggestions, mcLang = "en_us") {
        return new Promise((resolve) => {
            this.apiSuggestions = suggestions;
            this.mcLang = mcLang;

            this.buildApiSidebar();
            this.bindEvents();

            require(['vs/editor/editor.main'], () => {
                this.setupMonacoLanguage();

                const savedCode = sessionStorage.getItem('draft_code');
                const initialCode = savedCode !== null ? savedCode : Templates.defaultCode;

                this.editorInstance = monaco.editor.create(document.getElementById('editor-container'), {
                    value: initialCode,
                    language: 'lua',
                    theme: 'vs-dark',
                    automaticLayout: true,
                    fontSize: this.currentFontSize,
                    minimap: {enabled: false},
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                    parameterHints: {enabled: true},
                    formatOnPaste: true,
                    formatOnType: true,
                    mouseWheelZoom: true,
                    bracketPairColorization: {enabled: true, independentColorPoolPerBracketType: true},
                    guides: {bracketPairs: true, indentation: true, highlightActiveIndentation: true}
                });

                this.editorInstance.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
                    document.getElementById('send-btn').click();
                });

                let validationTimeout = null;
                this.editorInstance.onDidChangeModelContent(() => {
                    sessionStorage.setItem('draft_code', this.editorInstance.getValue());
                    this.clearRuntimeError();

                    if (validationTimeout) clearTimeout(validationTimeout);
                    validationTimeout = setTimeout(() => {
                        this.validateCode();
                    }, 500);
                });

                this.validateCode();
                resolve();
            });
        });
    },

    getDesc(def) {
        if (this.mcLang === "ja_jp") {
            return def.description || def.desc || def.descriptionEn || def.descEn || '';
        } else {
            return def.descriptionEn || def.descEn || def.description || def.desc || '';
        }
    },

    updateLanguage(filename) {
        if (!this.editorInstance || !filename) return;
        const model = this.editorInstance.getModel();
        let lang = 'lua';

        const lowerName = filename.toLowerCase();
        if (lowerName.endsWith('.html') || lowerName.endsWith('.htm')) lang = 'html';
        else if (lowerName.endsWith('.css')) lang = 'css';
        else if (lowerName.endsWith('.js')) lang = 'javascript';
        else if (lowerName.endsWith('.json')) lang = 'json';

        if (model.getLanguageId() !== lang) {
            monaco.editor.setModelLanguage(model, lang);
            this.validateCode();
        }
    },

    showRuntimeError(errMsg) {
        if (!this.editorInstance) return;
        const model = this.editorInstance.getModel();

        let line = 1;
        const match = errMsg.match(/\[string ".*?"\]:(\d+):/);
        if (match) {
            line = parseInt(match[1], 10);
        }

        const marker = {
            severity: monaco.MarkerSeverity.Error,
            startLineNumber: line,
            startColumn: 1,
            endLineNumber: line,
            endColumn: 1000,
            message: "Runtime Error: " + errMsg
        };

        monaco.editor.setModelMarkers(model, "lua-runtime", [marker]);
        this.highlightErrorLine(line);
    },

    clearRuntimeError() {
        if (!this.editorInstance) return;
        const model = this.editorInstance.getModel();
        monaco.editor.setModelMarkers(model, "lua-runtime", []);
        this.clearErrorHighlight();
    },

    translateError(englishMsg) {
        let msg = englishMsg;
        let hint = "";

        if (msg.includes("unexpected symbol near '<eof>'")) {
            hint = "Syntax is cut off at the end of the file.\nAn 'end' or closing parenthesis ')' is likely missing.";
        } else if (msg.includes("expected near")) {
            const match = msg.match(/'(.*?)' expected near/);
            if (match) {
                hint = `Expected '${match[1]}' near here.\nPlease check if structures like 'if' or 'for' are properly closed.`;
            } else {
                hint = "Syntax error. Please check for typos in surrounding symbols or words.";
            }
        } else if (msg.includes("unfinished string")) {
            hint = "String quotation (\" or ') is not closed.";
        } else if (msg.includes("unexpected symbol near")) {
            hint = "Invalid symbol or character found.\nPlease check for full-width spaces or incorrect punctuation.";
        } else if (msg.includes("malformed number")) {
            hint = "Malformed number format.";
        }
        return hint ? `[Error] ${msg}\n\n💡 Hint:\n${hint}` : `[Error] ${msg}`;
    },

    highlightErrorLine(lineNumber) {
        if (!this.editorInstance) return;
        this.errorDecorations = this.editorInstance.deltaDecorations(this.errorDecorations, [
            {
                range: new monaco.Range(lineNumber, 1, lineNumber, 1),
                options: {
                    isWholeLine: true,
                    className: 'error-line-highlight'
                }
            }
        ]);
    },

    clearErrorHighlight() {
        if (!this.editorInstance) return;
        this.errorDecorations = this.editorInstance.deltaDecorations(this.errorDecorations, []);
    },

    validateCode() {
        if (!this.editorInstance) return;
        const model = this.editorInstance.getModel();

        if (model.getLanguageId() !== 'lua') {
            monaco.editor.setModelMarkers(model, "lua", []);
            this.clearErrorHighlight();
            return;
        }

        const code = this.editorInstance.getValue();

        if (typeof luaparse !== 'undefined') {
            try {
                luaparse.parse(code, {luaVersion: '5.3'});
                monaco.editor.setModelMarkers(model, "lua", []);
                this.clearErrorHighlight();
            } catch (err) {
                const rawMsg = err.message.replace(/^\[\d+:\d+\]\s*/, '');
                const friendlyMsg = this.translateError(rawMsg);

                const marker = {
                    severity: monaco.MarkerSeverity.Error,
                    startLineNumber: err.line,
                    startColumn: err.column,
                    endLineNumber: err.line,
                    endColumn: err.column + 5,
                    message: friendlyMsg
                };
                monaco.editor.setModelMarkers(model, "lua", [marker]);
                this.highlightErrorLine(err.line);
            }
        }
    },

    buildMarkdownDocs(def) {
        // ★修正: event の場合に `on_` を自動付与しないように変更
        let title = def.type === 'api' ? `${def.module}.${def.name}` : def.name;

        let argsStr = (def.args && def.args.length > 0) ? def.args.join(', ') : '';
        let md = `### \`${title}(${argsStr})\`\n\n`;

        let desc = this.getDesc(def) || 'No description available.';
        md += `${desc}\n\n`;

        if (def.args && def.args.length > 0) {
            md += `**[ Arguments ]**\n`;
            def.args.forEach((arg) => {
                md += `- \`${arg}\`\n`;
            });
        }
        if (def.returnType && def.returnType !== 'void') {
            md += `\n**[ Returns ]** \`${def.returnType}\`\n`;
        }
        return {value: md};
    },

    setupMonacoLanguage() {
        monaco.languages.setLanguageConfiguration('lua', {
            brackets: [['{', '}'], ['[', ']'], ['(', ')']],
            autoClosingPairs: [
                {open: '{', close: '}'}, {open: '[', close: ']'},
                {open: '(', close: ')'}, {open: '"', close: '"'}, {open: "'", close: "'"}
            ],
            indentationRules: {
                increaseIndentPattern: /^\s*(?:local\s+)?(?:function|if|while|for|repeat|else|elseif)\b[^]*$/,
                decreaseIndentPattern: /^\s*(?:end|else|elseif|until)\b/
            }
        });

        this.registerCompletion();
        this.registerHover();
        this.registerSignatureHelp();
    },

    registerCompletion() {
        monaco.languages.registerCompletionItemProvider('lua', {
            triggerCharacters: ['.', ':'],
            provideCompletionItems: (model, position) => {
                const word = model.getWordUntilPosition(position);
                const range = {
                    startLineNumber: position.lineNumber, endLineNumber: position.lineNumber,
                    startColumn: word.startColumn, endColumn: word.endColumn
                };

                const suggestions = this.apiSuggestions.map(def => {
                    let insertText = '';
                    let label = '';
                    let detail = def.returnType && def.returnType !== 'void' ? `Returns: ${def.returnType}` : '';
                    if (def.type === 'api') {
                        label = `${def.module}.${def.name}`;
                        insertText = `${def.module}.${def.name}(${def.args.join(', ')})`;
                        if (!detail) detail = `API: ${def.module}`;
                    } else if (def.type === 'event') {
                        // ★修正: `on_` を自動付与しないように変更
                        label = def.name;
                        insertText = `function ${def.name}(${def.args.join(', ')})\n\t$0\nend`;
                        detail = 'Event Handler';
                    } else if (def.type === 'enum') {
                        label = `${def.module}.${def.name}`;
                        insertText = label;
                        detail = 'Enum';
                    }
                    return {
                        label: label,
                        kind: monaco.languages.CompletionItemKind.Function,
                        detail: detail,
                        documentation: this.buildMarkdownDocs(def),
                        insertText: insertText,
                        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                        range: range
                    };
                }).filter(s => s.label !== '');

                const stdLuaSuggestions = this.standardLuaApis.map(api => ({
                    label: api.label,
                    kind: monaco.languages.CompletionItemKind.Function,
                    detail: 'Lua Standard API',
                    documentation: {value: `### \`${api.label}\`\n\n${this.getDesc(api)}`},
                    insertText: api.insertText,
                    insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                    range: range
                }));

                const snippets = [
                    { label: 'if statement', kind: monaco.languages.CompletionItemKind.Snippet, detail: 'Conditional statement', insertText: 'if ${1:condition} then\n\t$0\nend', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: range },
                    { label: 'for loop', kind: monaco.languages.CompletionItemKind.Snippet, detail: 'Loop a specific number of times', insertText: 'for ${1:i}=${2:1},${3:10} do\n\t$0\nend', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: range },
                    { label: 'while loop', kind: monaco.languages.CompletionItemKind.Snippet, detail: 'Conditional loop', insertText: 'while ${1:condition} do\n\t$0\nend', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: range },
                    { label: 'function', kind: monaco.languages.CompletionItemKind.Snippet, detail: 'Function definition', insertText: 'function ${1:name}(${2:args})\n\t$0\nend', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: range }
                ];

                return {suggestions: [...suggestions, ...stdLuaSuggestions, ...snippets]};
            }
        });
    },

    registerHover() {
        monaco.languages.registerHoverProvider('lua', {
            provideHover: (model, position) => {
                const word = model.getWordAtPosition(position);
                if (!word) return null;
                const lineContent = model.getLineContent(position.lineNumber);

                let foundApi = this.apiSuggestions.find(def => {
                    if (def.type === 'api') {
                        return def.name === word.word && lineContent.includes(`${def.module}.${def.name}`);
                    }
                    if (def.type === 'event' || def.type === 'enum') {
                        // ★修正: 自動で `on_` を付与して判定しないように変更
                        return def.name === word.word;
                    }
                    return false;
                });

                if (foundApi) {
                    return {
                        range: new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn),
                        contents: [this.buildMarkdownDocs(foundApi)]
                    };
                }

                let foundStd = this.standardLuaApis.find(api => lineContent.includes(api.label) && api.label.includes(word.word));
                if (foundStd) {
                    return {
                        range: new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn),
                        contents: [{value: `### \`${foundStd.label}\`\n\n${this.getDesc(foundStd)}`}]
                    };
                }

                return null;
            }
        });
    },

    registerSignatureHelp() {
        monaco.languages.registerSignatureHelpProvider('lua', {
            signatureHelpTriggerCharacters: ['(', ','],
            provideSignatureHelp: (model, position) => {
                const textUntilPosition = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: 1,
                    endLineNumber: position.lineNumber,
                    endColumn: position.column
                });

                const match = textUntilPosition.match(/([a-zA-Z0-9_.]+)\s*\([^)]*$/);
                if (!match) return null;

                const funcName = match[1];
                const foundApi = this.apiSuggestions.find(def => {
                    if (def.type === 'api') {
                        return `${def.module}.${def.name}` === funcName;
                    }
                    return false;
                });

                if (foundApi) {
                    const paramIndex = textUntilPosition.split(',').length - 1;
                    return {
                        value: {
                            activeParameter: paramIndex,
                            activeSignature: 0,
                            signatures: [{
                                label: `${funcName}(${(foundApi.args || []).join(', ')})`,
                                documentation: {value: this.getDesc(foundApi)},
                                parameters: (foundApi.args || []).map(argName => ({
                                    label: argName,
                                    documentation: `Argument: ${argName}`
                                }))
                            }]
                        },
                        dispose: () => {
                        }
                    };
                }
                return null;
            }
        });
    },

    buildApiSidebar() {
        const apiListDiv = document.getElementById('api-list');
        const groups = {'events': [], 'tool': [], 'directions': []};

        this.apiSuggestions.forEach(def => {
            if (def.type === 'api') {
                if (!groups[def.module]) groups[def.module] = [];
                groups[def.module].push(def);
            } else if (def.type === 'event' && (def.name.includes('click') || def.name.includes('attack'))) {
                groups['tool'].push(def);
            } else if (def.type === 'event') {
                groups['events'].push(def);
            } else if (def.type === 'enum') {
                groups['directions'].push(def);
            }
        });

        apiListDiv.innerHTML = '';
        for (const [groupName, apis] of Object.entries(groups)) {
            if (apis.length === 0) continue;
            const groupEl = document.createElement('div');
            groupEl.className = 'api-group';
            const header = document.createElement('div');
            header.className = 'api-group-header';
            header.innerHTML = `<span>${groupName.toUpperCase()}</span><span class="count">${apis.length}</span>`;
            const content = document.createElement('div');
            content.className = 'api-group-content';
            header.onclick = () => content.classList.toggle('collapsed');

            apis.forEach(def => {
                const item = document.createElement('div');
                item.className = `api-item color-${groupName.toLowerCase()}`;

                // ★修正: event の場合に `on_` を自動付与しないように変更
                let title = def.type === 'api' ? `${def.module}.${def.name}` :
                    def.type === 'enum' ? `${def.module}.${def.name}` : def.name;

                let argsStr = (def.args && def.args.length > 0) ? `(${def.args.join(', ')})` : '()';
                if (def.type === 'enum') argsStr = '';

                let desc = this.getDesc(def);

                item.innerHTML = `
                    <div class="api-item-title">${title}</div><div class="api-item-args">${argsStr}</div>
                    <div class="api-item-desc" title="${desc}">${desc}</div>
                `;
                item.dataset.fulltext = (title + argsStr + desc).toLowerCase();

                item.onclick = () => {
                    if (!this.editorInstance) return;

                    let snippetStr = "";
                    if (def.type === 'api') {
                        if (def.args && def.args.length > 0) {
                            const snippetArgs = def.args.map((arg, index) => `\${${index + 1}:${arg}}`).join(', ');
                            snippetStr = `${title}(${snippetArgs})\n$0`;
                        } else {
                            snippetStr = `${title}()\n$0`;
                        }
                    } else if (def.type === 'event') {
                        const evtArgs = def.args ? def.args.join(', ') : '';
                        snippetStr = `function ${title}(${evtArgs})\n\t$0\nend\n`;
                    } else if (def.type === 'enum') {
                        snippetStr = `${title}$0`;
                    }

                    this.editorInstance.focus();

                    const snippetController = this.editorInstance.getContribution('snippetController2');
                    if (snippetController) {
                        snippetController.insert(snippetStr);
                    } else {
                        const position = this.editorInstance.getPosition();
                        const plainText = snippetStr.replace(/\$\{\d+:([^}]+)}/g, '$1').replace(/\$\d+/g, '');

                        this.editorInstance.executeEdits("api-sidebar", [{
                            range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
                            text: plainText,
                            forceMoveMarkers: true
                        }]);
                    }
                };
                content.appendChild(item);
            });
            groupEl.appendChild(header);
            groupEl.appendChild(content);
            apiListDiv.appendChild(groupEl);
        }
    },

    bindEvents() {
        const searchInput = document.getElementById('api-search-input');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                const query = e.target.value.toLowerCase();
                const items = document.querySelectorAll('.api-item');
                items.forEach(item => {
                    if (item.dataset.fulltext.includes(query)) item.style.display = '';
                    else item.style.display = 'none';
                });
                document.querySelectorAll('.api-group-content').forEach(c => {
                    if (query !== '') c.classList.remove('collapsed');
                });
            });
        }
    },

    getCode() {
        return this.editorInstance ? this.editorInstance.getValue() : "";
    },

    setCode(code) {
        if (this.editorInstance) {
            this.editorInstance.setValue(code);
            this.validateCode();
        }
    },

    hasErrors() {
        if (!this.editorInstance) return false;
        const model = this.editorInstance.getModel();
        const markers = monaco.editor.getModelMarkers({resource: model.uri});
        return markers.some(marker => marker.severity === monaco.MarkerSeverity.Error);
    }
};