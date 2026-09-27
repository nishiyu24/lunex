export const NodeEditor = {
    actionDefs: [],
    conditionDefs: [],
    goals: {},
    nextGoalId: 1,

    container: null,

    init(payload) {
        this.actionDefs = payload.actions || [];
        this.conditionDefs = payload.conditions || [];
        this.container = document.getElementById('node-container');

        if (payload.registries) {
            this.createDataList("dl-entities", payload.registries.entities);
            this.createDataList("dl-blocks", payload.registries.blocks);
            this.createDataList("dl-items", payload.registries.items);
        }

        this.container.style.display = "flex";
        this.container.style.flexDirection = "column";
        this.container.style.gap = "24px";
        this.container.style.padding = "70px 32px 100px 32px";
        this.container.style.boxSizing = "border-box";
        this.container.style.overflowY = "auto";
        this.container.style.overflowX = "auto";
        this.container.style.alignItems = "stretch";
        this.container.style.fontFamily = "Consolas, 'Courier New', monospace";

        const toolbar = document.getElementById('node-toolbar');
        if (toolbar) {
            toolbar.innerHTML = `
                <button class="btn-primary" id="btn-add-goal" style="background:#0e639c; border:none; padding:6px 16px; border-radius:4px; color:white; cursor:pointer; font-weight:bold; font-size:13px; box-shadow:0 2px 4px rgba(0,0,0,0.2);">+ Add Branch</button>
            `;
        }

        this.bindEvents();
        this.loadLua("");
    },

    createDataList(id, items) {
        let dl = document.getElementById(id);
        if (!dl) {
            dl = document.createElement('datalist');
            dl.id = id;
            document.body.appendChild(dl);
        }
        dl.innerHTML = items.map(i => `<option value="${i}"></option>`).join("");
    },

    bindEvents() {
        const btnAddGoal = document.getElementById('btn-add-goal');
        if (btnAddGoal) {
            btnAddGoal.onclick = () => {
                this.addGoal();
                this.updateRuleIndices();
            };
        }
    },

    clear() {
        this.goals = {};
        this.nextGoalId = 1;
        this.container.innerHTML = "";
    },

    formatComboString(def) {
        let desc = def.descEn;
        if (!desc || desc.trim() === "") {
            desc = def.name;
        }
        return `${desc} [${def.name}]`;
    },

    extractName(val) {
        if (val === "None") return "None";
        const match = val.match(/\[(.*?)\]$/);
        return match ? match[1] : val;
    },

    formatArgValue(v) {
        return (isNaN(v) && v !== "true" && v !== "false") ? `"${v}"` : v;
    },

    generateOptions(defsArray, selectedName, defaultLabel = "None") {
        let options = `<option value="None">${defaultLabel}</option>`;
        options += defsArray.map(def => {
            const displayStr = this.formatComboString(def);
            const isSelected = selectedName === def.name ? "selected" : "";
            return `<option value="${displayStr}" ${isSelected}>${displayStr}</option>`;
        }).join("");
        return options;
    },

    updateRuleIndices() {
        const ruleNodes = Array.from(this.container.querySelectorAll('.rule-line'));

        ruleNodes.forEach((nodeEl, index) => {
            const label = nodeEl.querySelector('.rule-if-label');
            if (label) {
                if (index === 0) {
                    label.textContent = "if";
                    label.style.color = "#c586c0";
                } else {
                    label.textContent = "elseif";
                    label.style.color = "#c586c0";
                }
            }
        });
    },

    addGoal(x = 0, y = 0, id = null, dataOverride = null) {
        const goalId = id || `goal_${this.nextGoalId++}`;
        const goal = {
            id: goalId,
            x: 0, y: 0,
            data: {
                priority: 5,
                isTarget: false,
                conditions: [],
                actions: []
            }
        };

        if (dataOverride) {
            goal.data = JSON.parse(JSON.stringify({...goal.data, ...dataOverride}));

            if (goal.data.canUseName && goal.data.canUseName !== "None") {
                goal.data.conditions = [{
                    id: `cond_${Date.now()}_migrated`,
                    condName: goal.data.canUseName,
                    condArgs: goal.data.canUseArgs || {},
                    isNot: false
                }];
                delete goal.data.canUseName;
                delete goal.data.canUseArgs;
            }
            if (!goal.data.conditions) goal.data.conditions = [];
        }
        this.goals[goalId] = goal;

        const el = document.createElement('div');
        el.className = `rule-line`;
        el.id = `goal-${goal.id}`;

        el.style.width = "100%";
        el.style.boxSizing = "border-box";
        el.style.minWidth = "fit-content";
        el.style.marginBottom = "8px";

        el.innerHTML = `
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; box-sizing: border-box; width: 100%;">
                <span class="rule-if-label" style="font-weight: bold; width: 60px; text-align: right; font-size: 14px; flex-shrink: 0;"></span>
                
                <span style="color: #6a9955; font-size: 13px; margin-left: 8px; flex-shrink: 0;">-- Execute the following every tick</span>
                
                <div style="margin-left: auto; display: flex; gap: 6px; align-items: center; flex-shrink: 0;">
                    <div style="display: flex; align-items: center; gap: 4px;" title="AI execution lane (multitasking) settings.&#10;Target: Always actively scanning for targets in parallel.&#10;Action: Performing actual movements or attacks.">
                        <span style="color: #9cdcfe; font-size: 12px; cursor: help;">Execution Lane:</span>
                        <select class="bind-istarget" style="background: #252526; color: #ccc; border: 1px solid #3c3c3c; padding: 2px 6px; border-radius: 3px; font-size: 12px; outline: none; cursor: help;">
                            <option value="false" ${!goal.data.isTarget ? "selected" : ""}>🏃 Action</option>
                            <option value="true" ${goal.data.isTarget ? "selected" : ""}>🧠 Target</option>
                        </select>
                    </div>
                    <button class="btn-move-up" title="Move Up" style="background: none; border: none; color: #858585; cursor: pointer; font-size: 14px; padding: 2px 6px;">▲</button>
                    <button class="btn-move-down" title="Move Down" style="background: none; border: none; color: #858585; cursor: pointer; font-size: 14px; padding: 2px 6px;">▼</button>
                    <button class="node-delete" title="Delete" style="background: none; border: none; color: #f48771; cursor: pointer; margin-left: 8px; font-size: 14px; padding: 2px 6px;">✖</button>
                </div>
            </div>
            
            <div style="margin-left: 72px; padding-left: 20px; border-left: 1px solid #404040; display: flex; flex-direction: column; gap: 8px; box-sizing: border-box;">
                
                <!-- CanUse condition list -->
                <div class="cond-list-container" style="display: flex; flex-direction: column; gap: 8px; box-sizing: border-box;"></div>
                <button class="btn-add-cond" style="align-self: flex-start; background: none; border: none; color: #dcdcaa; cursor: pointer; font-size: 13px; padding: 4px 8px; margin-left: -8px;">+ Add Condition (CanUse)</button>
                
                <div style="width: 100%; height: 1px; background: #3c3c3c; margin: 4px 0;"></div>

                <!-- Action list -->
                <div class="action-list-container" style="display: flex; flex-direction: column; gap: 8px; box-sizing: border-box;"></div>
                <button class="btn-add-action" style="align-self: flex-start; background: none; border: none; color: #3794ff; cursor: pointer; font-size: 13px; padding: 4px 8px; margin-left: -8px;">+ Add Action</button>
            </div>
        `;

        this.container.appendChild(el);
        this.bindGoalEvents(goal.id, el);

        const condListContainer = el.querySelector('.cond-list-container');
        goal.data.conditions.forEach(cond => {
            this.renderConditionRow(goal.id, cond, condListContainer);
        });

        const actionListContainer = el.querySelector('.action-list-container');
        goal.data.actions.forEach(act => {
            this.renderActionRow(goal.id, act, actionListContainer);
        });

        return goal;
    },

    bindGoalEvents(goalId, el) {
        const goal = this.goals[goalId];

        el.querySelector('.node-delete').addEventListener('click', () => {
            this.removeGoal(goalId);
            this.updateRuleIndices();
        });

        el.querySelector('.btn-move-up').addEventListener('click', () => {
            if (el.previousElementSibling) {
                el.parentNode.insertBefore(el, el.previousElementSibling);
                this.updateRuleIndices();
            }
        });

        el.querySelector('.btn-move-down').addEventListener('click', () => {
            if (el.nextElementSibling) {
                el.parentNode.insertBefore(el.nextElementSibling, el);
                this.updateRuleIndices();
            }
        });

        el.querySelector('.bind-istarget').onchange = (e) => {
            goal.data.isTarget = (e.target.value === "true");
        };

        el.querySelector('.btn-add-cond').onclick = () => {
            const condId = `cond_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
            const condData = {id: condId, condName: "None", condArgs: {}, isNot: false};
            goal.data.conditions.push(condData);
            this.renderConditionRow(goalId, condData, el.querySelector('.cond-list-container'));
        };

        el.querySelector('.btn-add-action').onclick = () => {
            const actId = `act_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
            const actData = {id: actId, actName: "None", actArgs: {}, condName: "None", condArgs: {}, isNot: false};
            goal.data.actions.push(actData);
            this.renderActionRow(goalId, actData, el.querySelector('.action-list-container'));
        };
    },

    renderConditionRow(goalId, condData, listContainer) {
        const row = document.createElement('div');
        row.className = "cond-row";
        row.id = `row-${condData.id}`;
        row.style.boxSizing = "border-box";
        row.style.width = "100%";

        if (condData.isNot === undefined) condData.isNot = false;
        const condOptions = this.generateOptions(this.conditionDefs, condData.condName, "Select Condition...");

        row.innerHTML = `
            <div style="display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; background: #1e1e1e; border: 1px dashed #3c3c3c; padding: 10px 12px; border-radius: 4px; box-sizing: border-box; width: 100%; position: relative;">
                
                <button class="cond-row-delete" title="Delete" style="position: absolute; top: 10px; right: 10px; background: none; border: none; color: #f48771; cursor: pointer; padding: 2px 6px; z-index: 10;">✖</button>
                
                <span style="color: #c586c0; font-size: 13px; font-weight: bold; width: 65px; text-align: right; margin-top: 4px; flex-shrink: 0;">Require</span>
                <select class="bind-cond" style="border: 1px solid #3c3c3c; background: #252526; color: #dcdcaa; padding: 4px 8px; border-radius: 3px; font-family: inherit; font-size: 13px; outline: none; flex-shrink: 0;">${condOptions}</select>
                
                <label style="display: flex; align-items: center; gap: 4px; color: #dcdcaa; font-size: 12px; margin-top: 4px; cursor: pointer;" title="Invert condition (e.g., In water -> Not in water)">
                    <input type="checkbox" class="bind-cond-not" ${condData.isNot ? "checked" : ""}> 🚫 NOT
                </label>

                <div class="dynamic-args cond-args" style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"></div>
            </div>
        `;

        listContainer.appendChild(row);

        row.querySelector('.cond-row-delete').onclick = () => {
            const goal = this.goals[goalId];
            goal.data.conditions = goal.data.conditions.filter(c => c.id !== condData.id);
            row.remove();
        };

        row.querySelector('.bind-cond').onchange = (e) => {
            const actualName = this.extractName(e.target.value);
            condData.condName = actualName;
            condData.condArgs = {};
            this.buildArgsUI(row.querySelector('.cond-args'), this.conditionDefs, actualName, condData.condArgs);
        };

        row.querySelector('.bind-cond-not').onchange = (e) => {
            condData.isNot = e.target.checked;
        };

        this.buildArgsUI(row.querySelector('.cond-args'), this.conditionDefs, condData.condName, condData.condArgs);
    },

    renderActionRow(goalId, actData, listContainer) {
        const row = document.createElement('div');
        row.className = "action-row";
        row.id = `row-${actData.id}`;
        row.style.boxSizing = "border-box";
        row.style.width = "100%";

        if (actData.isNot === undefined) actData.isNot = false;
        const actOptions = this.generateOptions(this.actionDefs, actData.actName, "Select Action...");
        const condOptions = this.generateOptions(this.conditionDefs, actData.condName, "None (Unconditional)");

        row.innerHTML = `
            <div style="display: flex; flex-direction: column; gap: 8px; background: #1e1e1e; border: 1px solid #3c3c3c; padding: 12px; border-radius: 4px; box-sizing: border-box; width: 100%; position: relative;">
                
                <button class="action-row-delete" title="Delete" style="position: absolute; top: 12px; right: 12px; background: none; border: none; color: #f48771; cursor: pointer; padding: 2px 6px; z-index: 10;">✖</button>

                <!-- Upper: Condition -->
                <div style="display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; width: calc(100% - 30px);">
                    <span style="color: #c586c0; font-size: 13px; font-weight: bold; width: 65px; text-align: right; margin-top: 4px; flex-shrink: 0;">Condition</span>
                    <select class="bind-action-cond" style="border: 1px solid #3c3c3c; background: #252526; color: #dcdcaa; padding: 4px 8px; border-radius: 3px; font-family: inherit; font-size: 13px; outline: none; flex-shrink: 0;"></select>
                    
                    <label style="display: flex; align-items: center; gap: 4px; color: #dcdcaa; font-size: 12px; margin-top: 4px; cursor: pointer;" title="Invert condition">
                        <input type="checkbox" class="bind-action-cond-not" ${actData.isNot ? "checked" : ""}> 🚫 NOT
                    </label>

                    <div class="dynamic-args action-cond-args" style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"></div>
                </div>
                
                <div style="width: 100%; height: 1px; background: #333; margin: 4px 0;"></div>
                
                <!-- Lower: Execute -->
                <div style="display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; width: 100%;">
                    <span style="color: #569cd6; font-size: 13px; font-weight: bold; width: 65px; text-align: right; margin-top: 4px; flex-shrink: 0;">Execute</span>
                    <select class="bind-act" style="border: 1px solid #3c3c3c; background: #252526; color: #4fc1ff; padding: 4px 8px; border-radius: 3px; font-family: inherit; font-size: 13px; outline: none; flex-shrink: 0;"></select>
                    <div class="dynamic-args act-args" style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;"></div>
                </div>
                
            </div>
        `;

        row.querySelector('.bind-act').innerHTML = actOptions;
        row.querySelector('.bind-action-cond').innerHTML = condOptions;

        listContainer.appendChild(row);

        row.querySelector('.action-row-delete').onclick = () => {
            const goal = this.goals[goalId];
            goal.data.actions = goal.data.actions.filter(a => a.id !== actData.id);
            row.remove();
        };

        const setupSelect = (selector, defsArray, nameKey, argsKey, containerClass) => {
            row.querySelector(selector).onchange = (e) => {
                const actualName = this.extractName(e.target.value);
                actData[nameKey] = actualName;
                actData[argsKey] = {};
                this.buildArgsUI(row.querySelector(containerClass), defsArray, actualName, actData[argsKey]);
            };
        };

        setupSelect('.bind-act', this.actionDefs, 'actName', 'actArgs', '.act-args');
        setupSelect('.bind-action-cond', this.conditionDefs, 'condName', 'condArgs', '.action-cond-args');

        row.querySelector('.bind-action-cond-not').onchange = (e) => {
            actData.isNot = e.target.checked;
        };

        this.buildArgsUI(row.querySelector('.act-args'), this.actionDefs, actData.actName, actData.actArgs);
        this.buildArgsUI(row.querySelector('.action-cond-args'), this.conditionDefs, actData.condName, actData.condArgs);
    },

    buildArgsUI(containerEl, defsArray, targetName, targetArgsObj) {
        containerEl.innerHTML = "";
        if (targetName !== "None" && targetName !== "Select Action...") {
            const def = defsArray.find(d => d.name === targetName);
            if (def && def.args) {
                this.generateInputs(containerEl, def.args, targetArgsObj);
            }
        }
    },

    generateInputs(containerEl, argsDefList, dataObj) {
        argsDefList.forEach(argDef => {
            const parts = argDef.split(':');
            const type = parts[0];
            const name = parts[1] || parts[0];

            let val = dataObj[name];
            if (val === undefined) {
                if (name === "handType") val = "mainhand";
                else if (name === "slotType") val = "fuel";
                else if (type === 'bool' || name === "isSneaking" || name === "open") val = "false";
                else if (type === 'num') val = 0;
                else val = "";
            }
            dataObj[name] = val;

            const div = document.createElement('div');
            div.style.display = "flex";
            div.style.alignItems = "center";
            div.style.gap = "4px";

            let inputHtml = "";
            let isSelect = false;

            const width = (type === 'num') ? '60px' : '140px';
            const style = `width: ${width}; padding: 3px 6px; background: #1e1e1e; color: #d4d4d4; border: 1px solid #3c3c3c; border-radius: 2px; font-family: inherit; font-size: 13px; outline: none; transition: border-color 0.2s;`;

            if (name === "handType") {
                isSelect = true;
                inputHtml = `<select style="${style}">
                    <option value="mainhand" ${val === "mainhand" ? "selected" : ""}>Main hand</option>
                    <option value="offhand" ${val === "offhand" ? "selected" : ""}>Off hand</option>
                </select>`;
            } else if (name === "slotType") {
                isSelect = true;
                inputHtml = `<select style="${style}">
                    <option value="input" ${val === "input" ? "selected" : ""}>Ingredient (Top)</option>
                    <option value="fuel" ${val === "fuel" ? "selected" : ""}>Fuel (Bottom)</option>
                </select>`;
            } else if (type === 'bool' || name === "isSneaking" || name === "open") {
                isSelect = true;
                inputHtml = `<select style="${style}">
                    <option value="true" ${String(val) === "true" ? "selected" : ""}>Yes (true)</option>
                    <option value="false" ${String(val) === "false" ? "selected" : ""}>No (false)</option>
                </select>`;
            } else if (name === "entityId") {
                inputHtml = `<input type="text" list="dl-entities" value="${val}" style="${style}" placeholder="e.g. minecraft:player">`;
            } else if (name === "blockId") {
                inputHtml = `<input type="text" list="dl-blocks" value="${val}" style="${style}" placeholder="e.g. minecraft:stone">`;
            } else if (name === "itemId") {
                inputHtml = `<input type="text" list="dl-items" value="${val}" style="${style}" placeholder="e.g. minecraft:apple">`;
            } else {
                inputHtml = `<input type="text" value="${val}" style="${style}">`;
            }

            const labelMap = {
                "entityId": "Entity", "blockId": "Block", "itemId": "Item",
                "handType": "Hand", "slotType": "Slot", "isSneaking": "Sneaking",
                "open": "Open", "distance": "Distance", "radius": "Radius", "reach": "Reach",
                "speed": "Speed", "count": "Count", "minCount": "Min Count", "chance": "Chance",
                "percent": "Percent(%)", "ticks": "Ticks", "timerId": "Timer Name", "key": "Memory Key",
                "volume": "Volume", "pitch": "Pitch", "effectId": "Effect ID",
                "resultId": "Result", "resultCount": "Result Count", "ingredients": "Ingredients"
            };

            let label = labelMap[name] || name;

            div.innerHTML = `<span style="font-size:13px; color:#9cdcfe; margin-left:4px;">${label}=</span>${inputHtml}`;

            const inputEl = div.querySelector(isSelect ? 'select' : 'input');
            inputEl.onchange = (e) => dataObj[name] = e.target.value;
            inputEl.oninput = (e) => dataObj[name] = e.target.value;
            inputEl.onfocus = () => inputEl.style.borderColor = "#007acc";
            inputEl.onblur = () => inputEl.style.borderColor = "#3c3c3c";

            containerEl.appendChild(div);
        });
    },

    removeGoal(goalId) {
        const el = document.getElementById(`goal-${goalId}`);
        if (el) el.remove();
        delete this.goals[goalId];
    },

    generateLua() {
        const nodeEls = Array.from(this.container.querySelectorAll('.rule-line'));
        if (nodeEls.length === 0) throw new Error("There are no branches.");

        let luaCode = `-- AI system generated in Minecraft standard format\nmob.clearGoals()\n\n`;

        nodeEls.forEach((el, index) => {
            const goalId = el.id.replace('goal-', '');
            const goal = this.goals[goalId];

            goal.data.priority = index + 1;

            let canUseStr = "{}";
            if (goal.data.conditions && goal.data.conditions.length > 0) {
                const condList = [];
                for (const cond of goal.data.conditions) {
                    if (cond.condName !== "None" && cond.condName !== "Select Condition...") {
                        const argsMap = [`type = "${cond.condName}"`];
                        if (cond.isNot) argsMap.push(`isNot = true`);
                        for (const [k, v] of Object.entries(cond.condArgs)) {
                            argsMap.push(`${k} = ${this.formatArgValue(v)}`);
                        }
                        condList.push(`{ ${argsMap.join(", ")} }`);
                    }
                }
                if (condList.length > 0) {
                    canUseStr = `{ ${condList.join(", ")} }`;
                }
            }

            let tickArrayStr = "{}";
            if (goal.data.actions.length > 0) {
                const tickList = [];
                for (const act of goal.data.actions) {
                    if (act.actName !== "None" && act.actName !== "Select Action...") {
                        const actProps = [`type = "${act.actName}"`];
                        for (const [k, v] of Object.entries(act.actArgs)) {
                            actProps.push(`${k} = ${this.formatArgValue(v)}`);
                        }

                        if (act.condName !== "None") {
                            const condProps = [`type = "${act.condName}"`];
                            if (act.isNot) condProps.push(`isNot = true`);
                            for (const [k, v] of Object.entries(act.condArgs)) {
                                condProps.push(`${k} = ${this.formatArgValue(v)}`);
                            }
                            actProps.push(`condition = { ${condProps.join(", ")} }`);
                        }
                        tickList.push(`        { ${actProps.join(", ")} }`);
                    }
                }
                if (tickList.length > 0) {
                    tickArrayStr = `{\n${tickList.join(",\n")}\n    }`;
                }
            }

            const goalVar = `goalDef${index + 1}`;
            luaCode += `-- [Priority: ${goal.data.priority}] ${goal.data.isTarget ? "Target" : "Action"}\n`;
            luaCode += `local ${goalVar} = {\n`;
            luaCode += `    priority = ${goal.data.priority},\n`;
            luaCode += `    isTarget = ${goal.data.isTarget},\n`;
            luaCode += `    canUse = ${canUseStr},\n`;
            luaCode += `    tick = ${tickArrayStr}\n`;
            luaCode += `}\n`;
            luaCode += `mob.buildGoal(${goalVar})\n\n`;
        });

        const graphJson = JSON.stringify({
            goals: this.goals,
            nextGoalId: this.nextGoalId
        });
        luaCode += `--[[@NODE_GRAPH_DATA\n${graphJson}\n]]`;

        return luaCode;
    },

    loadLua(code) {
        this.clear();

        const match = code.match(/--\[\[@NODE_GRAPH_DATA\s*([\s\S]*?)\]\]/);
        if (match) {
            try {
                const data = JSON.parse(match[1]);
                this.nextGoalId = data.nextGoalId;

                const loadedGoals = Object.entries(data.goals).sort((a, b) => a[1].data.priority - b[1].data.priority);

                for (const [id, goal] of loadedGoals) {
                    this.addGoal(0, 0, id, goal.data);
                }
                this.updateRuleIndices();
                return;
            } catch (e) {
                console.error("Failed to restore the graph", e);
            }
        }

        this.addGoal();
        this.updateRuleIndices();
    }
};