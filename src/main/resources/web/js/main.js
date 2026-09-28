import {API} from './api.js';
import {TextEditor} from './text-editor.js';
import {Templates} from './templates.js';
import {NodeEditor} from './node-editor.js';

let currentWorkspace = "default";
let activeLockedFile = "";
let selectedFileInExplorer = null;
let isEditorInitialized = false;

// 現在のページがEntityモードかどうか
const isEntityMode = window.EDITOR_TYPE === 'entity';

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');

    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';
    if (type === 'warning') icon = '⚠️';

    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${icon}</span> <span>${message.replace(/\n/g, '<br>')}</span>`;

    container.appendChild(toast);

    setTimeout(() => toast.classList.add('show'), 10);
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

function clearSessionData() {
    sessionStorage.removeItem("currentFilePath");
    sessionStorage.removeItem("draft_code");
}

function createNewFile() {
    if (activeLockedFile) return;

    if (isEntityMode) {
        NodeEditor.loadLua("");
    } else {
        TextEditor.setCode(Templates.defaultCode);
    }

    const nameInput = document.getElementById('program-name-input');
    if (nameInput) {
        nameInput.value = "untitled";
        nameInput.focus();
        nameInput.select();
        if (!isEntityMode) TextEditor.updateLanguage(nameInput.value);
    }

    clearSessionData();
}

async function bootstrap() {
    if (!isEntityMode) setupTemplates();
    bindEditorEvents();
    bindVmEvents();
    bindExplorerEvents();

    try {
        await API.connect();
    } catch (e) {
        console.error("Connection Error:", e);
        showToast("Failed to connect to Minecraft.\nPlease check if the server is running.", "error");
        return;
    }

    const payload = await API.fetchSuggestions();
    if (payload && payload.suggestions) {
        if (isEntityMode) {
            NodeEditor.init(payload);
        } else {
            await TextEditor.init(payload.suggestions, payload.mcLang || "en_us");
        }
    }

    isEditorInitialized = true;

    const linkInfo = await API.fetchLinkInfo();
    if (linkInfo) {
        document.dispatchEvent(new CustomEvent('LinkedVmChanged', {detail: linkInfo}));
    }
}

function setupTemplates() {
    const select = document.getElementById('template-select');
    if (!select) return;
    Templates.list.forEach(tpl => {
        const option = document.createElement('option');
        option.value = tpl.id;
        option.textContent = tpl.name;
        select.appendChild(option);
    });
}

function bindEditorEvents() {
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            const sendBtn = document.getElementById('send-btn');
            if (sendBtn) sendBtn.click();
        }
    });

    const nameInput = document.getElementById('program-name-input');
    if (nameInput && !isEntityMode) {
        nameInput.addEventListener('input', (e) => {
            TextEditor.updateLanguage(e.target.value);
        });
    }

    const templateSelect = document.getElementById('template-select');
    if (templateSelect) {
        templateSelect.onchange = (e) => {
            const val = e.target.value;
            if (!val || activeLockedFile || isEntityMode) {
                e.target.value = "";
                return;
            }

            const template = Templates.list.find(t => t.id === val);
            if (template) {
                if (TextEditor.getCode().trim() !== "" && !confirm("Are you sure you want to clear the current code and load the template?")) {
                    e.target.value = "";
                    return;
                }

                TextEditor.setCode(template.code);
                if (nameInput) {
                    nameInput.value = `untitled_${val}`;
                    TextEditor.updateLanguage(nameInput.value);
                }

                clearSessionData();
                showToast("Template loaded.", "success");
            }
            e.target.value = "";
        };
    }

    const sendBtn = document.getElementById('send-btn');
    if (sendBtn) {
        sendBtn.onclick = async () => {
            const inputEl = document.getElementById('program-name-input');
            const inputVal = inputEl ? inputEl.value.trim() : "";
            const programName = inputVal ? `${currentWorkspace}/${inputVal}` : `${currentWorkspace}/boot`;

            if (!inputVal) {
                showToast("File name is not entered.\nPlease enter a name or select from Load.", "warning");
                return;
            }

            let code = "";
            if (isEntityMode) {
                try {
                    code = NodeEditor.generateLua();
                } catch (err) {
                    console.error("Node Generation Error:", err);
                    showToast(err.message, "error");
                    return;
                }
            } else {
                if (TextEditor.hasErrors()) {
                    showToast("Syntax error!\nPlease fix the red underlined/highlighted areas before saving.", "error");
                    return;
                }
                code = TextEditor.getCode();
            }

            try {
                await API.saveProgram(programName, code);
                sessionStorage.setItem("currentFilePath", programName);
                showToast(`Saved as "${programName}"!`, "success");

                const overlay = document.getElementById('file-explorer-overlay');
                if (overlay && !overlay.classList.contains('hidden')) {
                    openExplorer();
                }
            } catch (e) {
                console.error("Save Error:", e);
                showToast("Failed to save.", "error");
            }
        };
    }
}

function bindVmEvents() {
    document.addEventListener('LinkedVmChanged', async (e) => {
        while (!isEditorInitialized) {
            await new Promise(resolve => setTimeout(resolve, 50));
        }

        const data = e.detail;
        const vmLabel = document.getElementById('vm-status-label');
        const explorerBtn = document.getElementById('explorer-btn');
        const templateSelect = document.getElementById('template-select');
        const nameInput = document.getElementById('program-name-input');

        currentWorkspace = data.workspaceId || "default";
        activeLockedFile = data.lockedFile || "";
        const defaultFile = data.defaultFile || "";

        let statusText = `🖥️ VM: ${data.vmId !== "none" ? data.vmId : "Disconnected"} | 📁 ${currentWorkspace}`;
        if (isEntityMode) statusText += " (BioMob)";

        const lastVmId = sessionStorage.getItem('lastVmId');
        const isSameVm = (lastVmId === data.vmId);
        sessionStorage.setItem('lastVmId', data.vmId);

        if (!isSameVm) {
            clearSessionData();
        }

        const savedPath = sessionStorage.getItem("currentFilePath");
        const hasDraft = sessionStorage.getItem("draft_code") !== null;

        const getRelativePath = (fullPath) => {
            const prefix = currentWorkspace + "/";
            if (fullPath && fullPath.startsWith(prefix)) return fullPath.substring(prefix.length);
            return fullPath;
        };

        if (activeLockedFile) {
            statusText += ` 🔒 Locked`;
            if (explorerBtn) explorerBtn.disabled = true;
            if (templateSelect) templateSelect.disabled = true;
            if (nameInput) {
                nameInput.disabled = true;
                nameInput.value = activeLockedFile;
            }

            if (vmLabel) vmLabel.textContent = statusText;

            if (!isSameVm || !hasDraft) {
                await loadSpecificProgram(`${currentWorkspace}/${activeLockedFile}`);
            } else if (nameInput && !isEntityMode) {
                TextEditor.updateLanguage(nameInput.value);
            }
        } else {
            if (explorerBtn) explorerBtn.disabled = false;
            if (templateSelect) templateSelect.disabled = false;
            if (nameInput) nameInput.disabled = false;

            if (isSameVm && savedPath && savedPath.startsWith(`${currentWorkspace}/`)) {
                if (nameInput) nameInput.value = getRelativePath(savedPath);
            } else if (defaultFile) {
                if (nameInput) nameInput.value = defaultFile;
            } else {
                if (nameInput) nameInput.value = "";
            }

            if (vmLabel) vmLabel.textContent = statusText;

            if (!isSameVm || !hasDraft) {
                if (nameInput && nameInput.value) {
                    await loadSpecificProgram(`${currentWorkspace}/${nameInput.value}`);
                }
            } else if (nameInput && !isEntityMode) {
                TextEditor.updateLanguage(nameInput.value);
            }
        }
    });

    document.addEventListener('VmError', (e) => {
        if (isEntityMode) return;
        const data = e.detail;
        const lastVmId = sessionStorage.getItem('lastVmId');

        if (data.vmId === lastVmId) {
            console.error("[VM Runtime Error]", data.error);
            TextEditor.showRuntimeError(data.error);
            showToast("A runtime error occurred in the VM.", "error");
        }
    });
}

function bindExplorerEvents() {
    const explorerBtn = document.getElementById('explorer-btn');
    if (explorerBtn) {
        explorerBtn.onclick = async () => {
            if (activeLockedFile) return;
            openExplorer();
        };
    }

    const explorerCloseBtn = document.getElementById('explorer-close-btn');
    if (explorerCloseBtn) {
        explorerCloseBtn.onclick = () => {
            document.getElementById('file-explorer-overlay').classList.add('hidden');
            selectedFileInExplorer = null;
        };
    }

    const explorerNewBtn = document.getElementById('explorer-new-btn');
    if (explorerNewBtn) {
        explorerNewBtn.onclick = () => {
            document.getElementById('file-explorer-overlay').classList.add('hidden');
            createNewFile();
        };
    }

    const explorerDeleteBtn = document.getElementById('explorer-delete-btn');
    if (explorerDeleteBtn) {
        explorerDeleteBtn.onclick = async () => {
            if (!selectedFileInExplorer) {
                showToast("Please select a file to delete.", "warning");
                return;
            }
            if (confirm(`Are you sure you want to delete "${selectedFileInExplorer}"?`)) {
                try {
                    await API.deleteProgram(selectedFileInExplorer);
                    showToast("File deleted.", "success");

                    const currentPath = sessionStorage.getItem("currentFilePath");
                    if (currentPath === selectedFileInExplorer) {
                        createNewFile();
                    }
                    openExplorer();
                } catch (e) {
                    console.error("Delete Error:", e);
                    showToast("Failed to delete.", "error");
                }
            }
        };
    }
}

async function openExplorer() {
    const overlay = document.getElementById('file-explorer-overlay');
    const treeDiv = document.getElementById('file-tree');

    if (!overlay || !treeDiv) return;

    overlay.classList.remove('hidden');
    treeDiv.innerHTML = "<div style='color:#fff; padding:10px;'>Loading...</div>";
    selectedFileInExplorer = null;

    try {
        const filesMap = await API.fetchFiles();
        treeDiv.innerHTML = "";

        if (filesMap[currentWorkspace]) {
            const wsDiv = document.createElement('div');
            wsDiv.className = 'tree-workspace';
            wsDiv.textContent = `📁 Workspace: ${currentWorkspace}`;
            treeDiv.appendChild(wsDiv);

            const files = filesMap[currentWorkspace];
            const filteredFiles = files.filter(f => !f.endsWith('.lifespan'));

            if (filteredFiles.length === 0) {
                const emptyDiv = document.createElement('div');
                emptyDiv.className = 'tree-file empty';
                emptyDiv.textContent = "  (Empty)";
                treeDiv.appendChild(emptyDiv);
            } else {
                const fileTree = {};

                filteredFiles.sort().forEach(f => {
                    const parts = f.split('/');
                    let currentLevel = fileTree;
                    parts.forEach((part, index) => {
                        if (!currentLevel[part]) {
                            currentLevel[part] = (index === parts.length - 1) ? null : {};
                        }
                        currentLevel = currentLevel[part];
                    });
                });

                const renderTree = (treeNode, parentElement, pathPrefix = "") => {
                    for (const key of Object.keys(treeNode).sort()) {
                        const isFile = treeNode[key] === null;
                        const itemDiv = document.createElement('div');

                        if (isFile) {
                            itemDiv.className = 'tree-file';
                            itemDiv.textContent = `📄 ${key}`;

                            itemDiv.onclick = (e) => {
                                e.stopPropagation();
                                document.querySelectorAll('.tree-file').forEach(el => el.classList.remove('selected'));
                                itemDiv.classList.add('selected');

                                const relativePath = `${pathPrefix}${key}`;
                                selectedFileInExplorer = `${currentWorkspace}/${relativePath}`;
                            };

                            itemDiv.ondblclick = async (e) => {
                                e.stopPropagation();
                                overlay.classList.add('hidden');

                                const relativePath = `${pathPrefix}${key}`;
                                const fullName = `${currentWorkspace}/${relativePath}`;

                                const nameInput = document.getElementById('program-name-input');
                                if (nameInput) nameInput.value = relativePath;

                                await loadSpecificProgram(fullName);
                            };

                            parentElement.appendChild(itemDiv);
                        } else {
                            itemDiv.className = 'tree-folder closed';
                            itemDiv.textContent = `📁 ${key}`;

                            const childrenContainer = document.createElement('div');
                            childrenContainer.className = 'tree-folder-children hidden';

                            itemDiv.onclick = (e) => {
                                e.stopPropagation();
                                const isClosed = itemDiv.classList.contains('closed');
                                if (isClosed) {
                                    itemDiv.classList.remove('closed');
                                    itemDiv.classList.add('open');
                                    itemDiv.textContent = `📂 ${key}`;
                                    childrenContainer.classList.remove('hidden');
                                } else {
                                    itemDiv.classList.remove('open');
                                    itemDiv.classList.add('closed');
                                    itemDiv.textContent = `📁 ${key}`;
                                    childrenContainer.classList.add('hidden');
                                }
                            };

                            parentElement.appendChild(itemDiv);
                            parentElement.appendChild(childrenContainer);
                            renderTree(treeNode[key], childrenContainer, `${pathPrefix}${key}/`);
                        }
                    }
                };

                const rootContainer = document.createElement('div');
                rootContainer.style.marginLeft = "10px";
                renderTree(fileTree, rootContainer);
                treeDiv.appendChild(rootContainer);
            }
        } else {
            treeDiv.innerHTML = `<div style='color:#f55; padding:10px;'>Workspace "${currentWorkspace}" not found.</div>`;
        }
    } catch (e) {
        console.error("Explorer Load Error:", e);
        treeDiv.innerHTML = "<div style='color:#f55; padding:10px;'>Load failed</div>";
    }
}

async function loadSpecificProgram(fullName) {
    try {
        const data = await API.loadProgram(fullName);

        if (data.content !== undefined) {
            if (isEntityMode) {
                NodeEditor.loadLua(data.content);
            } else {
                TextEditor.setCode(data.content);
                TextEditor.updateLanguage(fullName);
            }

            sessionStorage.setItem("currentFilePath", fullName);
            console.log(`[Load] Loaded file: ${fullName}`);
            showToast(`Loaded "${fullName}".`, "success");
        } else {
            showToast(`"${fullName}" not found.`, "error");
        }
    } catch (e) {
        console.error("Program Load Error:", e);
        showToast("Failed to load file.", "error");
    }
}

document.addEventListener("DOMContentLoaded", bootstrap);