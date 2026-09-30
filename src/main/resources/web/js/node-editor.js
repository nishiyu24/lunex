export const NodeEditor = {
    actionDefs: [],
    conditionDefs: [],
    nodes: {},
    edges: {},
    nextNodeId: 1,
    nextEdgeId: 1,
    mcLang: "en_us",

    container: null,
    transform: { x: 0, y: 0, scale: 1 },

    isPanning: false,
    draggedNode: null,
    isDraggingEdge: false,
    edgeSourceId: null,
    mousePos: { x: 0, y: 0 },
    mouseLocalPos: { x: 0, y: 0 },

    init(payload) {
        this.mcLang = payload.mcLang || "en_us";
        this.actionDefs = payload.actions || [];
        this.conditionDefs = payload.conditions || [];

        const outer = document.getElementById('node-editor-area');
        this.container = document.getElementById('node-container');

        if (payload.registries) {
            this.createDataList("dl-entities", payload.registries.entities);
            this.createDataList("dl-blocks", payload.registries.blocks);
            this.createDataList("dl-items", payload.registries.items);
        }

        this.container.style.position = "absolute";
        this.container.style.transformOrigin = "0 0";
        this.container.style.width = "100%";
        this.container.style.height = "100%";
        this.container.innerHTML = `
            <svg id="edges-svg" style="position:absolute; top:0; left:0; width:100%; height:100%; overflow:visible; pointer-events:none; z-index:1;"></svg>
            <div id="nodes-layer" style="position:absolute; top:0; left:0; width:100%; height:100%; z-index:2;"></div>
            <div id="edges-ui-layer" style="position:absolute; top:0; left:0; width:100%; height:100%; z-index:3; pointer-events:none;"></div>
        `;

        outer.style.backgroundImage = 'radial-gradient(circle, #444 1px, transparent 1px)';
        outer.style.cursor = 'grab';

        const toolbar = document.getElementById('node-toolbar');
        if (toolbar) {
            toolbar.innerHTML = `
                <button class="btn-primary" id="btn-add-node" style="background:#0e639c; border:none; padding:6px 16px; border-radius:4px; color:white; cursor:pointer; font-weight:bold; font-size:13px; box-shadow:0 2px 4px rgba(0,0,0,0.2);">+ Add State</button>
                <button class="btn-secondary" id="btn-reset-view" style="margin-left: 5px; padding:6px 12px; font-size:13px;">Reset View</button>
            `;
        }

        this.bindWorkspaceEvents(outer);
        this.updateTransform();
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

    getCanvasPos(clientX, clientY) {
        const outerRect = document.getElementById('node-editor-area').getBoundingClientRect();
        return {
            x: (clientX - outerRect.left - this.transform.x) / this.transform.scale,
            y: (clientY - outerRect.top - this.transform.y) / this.transform.scale
        };
    },

    bindWorkspaceEvents(outer) {
        document.getElementById('btn-add-node').onclick = () => this.addNode();
        document.getElementById('btn-reset-view').onclick = () => {
            this.transform = { x: 0, y: 0, scale: 1 };
            this.updateTransform();
        };

        outer.addEventListener('mousedown', (e) => {
            const portOut = e.target.closest('.port-out');
            if (portOut) {
                this.isDraggingEdge = true;
                this.edgeSourceId = portOut.closest('.node-block').id.replace('node-', '');
                this.mouseLocalPos = this.getCanvasPos(e.clientX, e.clientY);
                e.stopPropagation();
                return;
            }

            if (e.target.closest('.node-block') || e.target.closest('.edge-panel')) return;

            this.isPanning = true;
            outer.style.cursor = 'grabbing';
            this.mousePos = { x: e.clientX, y: e.clientY };
        });

        window.addEventListener('mousemove', (e) => {
            this.mouseLocalPos = this.getCanvasPos(e.clientX, e.clientY);

            if (this.isDraggingEdge) {
                this.updateEdges();
            } else if (this.isPanning) {
                const dx = e.clientX - this.mousePos.x;
                const dy = e.clientY - this.mousePos.y;
                this.transform.x += dx;
                this.transform.y += dy;
                this.mousePos = { x: e.clientX, y: e.clientY };
                this.updateTransform();
            } else if (this.draggedNode) {
                const dx = (e.clientX - this.mousePos.x) / this.transform.scale;
                const dy = (e.clientY - this.mousePos.y) / this.transform.scale;
                this.draggedNode.x += dx;
                this.draggedNode.y += dy;
                const el = document.getElementById(`node-${this.draggedNode.id}`);
                if (el) {
                    el.style.left = this.draggedNode.x + 'px';
                    el.style.top = this.draggedNode.y + 'px';
                }
                this.mousePos = { x: e.clientX, y: e.clientY };
                this.updateEdges();
            }
        });

        window.addEventListener('mouseup', (e) => {
            if (this.isDraggingEdge) {
                this.isDraggingEdge = false;
                const portIn = e.target.closest('.port-in');
                if (portIn) {
                    const targetId = portIn.closest('.node-block').id.replace('node-', '');
                    if (this.edgeSourceId !== targetId) {
                        this.addEdge(this.edgeSourceId, targetId);
                    }
                }
                this.edgeSourceId = null;
                this.updateEdges();
            }

            if (this.isPanning) {
                this.isPanning = false;
                outer.style.cursor = 'grab';
            }
            if (this.draggedNode) {
                this.draggedNode = null;
            }
        });

        outer.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = -e.deltaY * 0.001;
            const newScale = Math.min(Math.max(0.2, this.transform.scale + delta), 3);

            const rect = outer.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;

            const xs = (mouseX - this.transform.x) / this.transform.scale;
            const ys = (mouseY - this.transform.y) / this.transform.scale;

            this.transform.scale = newScale;
            this.transform.x = mouseX - xs * newScale;
            this.transform.y = mouseY - ys * newScale;

            this.updateTransform();
        });
    },

    updateTransform() {
        this.container.style.transform = `translate(${this.transform.x}px, ${this.transform.y}px) scale(${this.transform.scale})`;
        const outer = document.getElementById('node-editor-area');
        if (outer) {
            outer.style.backgroundPosition = `${this.transform.x}px ${this.transform.y}px`;
            outer.style.backgroundSize = `${20 * this.transform.scale}px ${20 * this.transform.scale}px`;
        }
        this.updateEdges();
    },

    updateEdges() {
        const svg = document.getElementById('edges-svg');
        if (!svg) return;

        let paths = "";

        Object.values(this.edges).forEach(edge => {
            const srcEl = document.getElementById(`node-${edge.sourceId}`);
            const tgtEl = document.getElementById(`node-${edge.targetId}`);
            const uiPanel = document.getElementById(`edge-ui-${edge.id}`);

            if (srcEl && tgtEl) {
                const x1 = this.nodes[edge.sourceId].x + 300;
                const y1 = this.nodes[edge.sourceId].y + 20;

                const x2 = this.nodes[edge.targetId].x;
                const y2 = this.nodes[edge.targetId].y + 20;

                const cp = Math.max(Math.abs(x2 - x1) / 2, 50);
                paths += `<path d="M ${x1} ${y1} C ${x1 + cp} ${y1}, ${x2 - cp} ${y2}, ${x2} ${y2}" fill="none" stroke="#569cd6" stroke-width="3" opacity="0.8"/>`;

                if (uiPanel) {
                    uiPanel.style.left = `${(x1 + x2) / 2}px`;
                    uiPanel.style.top = `${(y1 + y2) / 2}px`;
                }
            }
        });

        if (this.isDraggingEdge && this.edgeSourceId && this.nodes[this.edgeSourceId]) {
            const x1 = this.nodes[this.edgeSourceId].x + 300;
            const y1 = this.nodes[this.edgeSourceId].y + 20;
            const x2 = this.mouseLocalPos.x;
            const y2 = this.mouseLocalPos.y;
            const cp = Math.max(Math.abs(x2 - x1) / 2, 50);
            paths += `<path d="M ${x1} ${y1} C ${x1 + cp} ${y1}, ${x2 - cp} ${y2}, ${x2} ${y2}" fill="none" stroke="#4fc1ff" stroke-width="3" stroke-dasharray="5,5" opacity="0.8"/>`;
        }

        svg.innerHTML = paths;
    },

    clear() {
        this.nodes = {};
        this.edges = {};
        this.nextNodeId = 1;
        this.nextEdgeId = 1;
        document.getElementById('nodes-layer').innerHTML = "";
        document.getElementById('edges-ui-layer').innerHTML = "";
        document.getElementById('edges-svg').innerHTML = "";
    },

    formatComboString(def) {
        const desc = this.mcLang === "ja_jp" ? (def.desc || def.descEn) : (def.descEn || def.desc);
        return `${desc || def.name} [${def.name}]`;
    },

    extractName(val) {
        if (val === "None") return "None";
        const match = val.match(/\[(.*?)\]$/);
        return match ? match[1] : val;
    },

    formatArgValue(v) {
        if (v === "" || v === undefined || v === null) return `""`;
        if (v === "true" || v === "false") return v;
        if (!isNaN(v)) return v;
        return `"${v}"`;
    },

    generateOptions(defsArray, selectedName, defaultLabel = "None") {
        let options = `<option value="None">${defaultLabel}</option>`;

        const groups = {};
        defsArray.forEach(def => {
            if ((def.desc && def.desc.includes("(内部処理用)")) || (def.descEn && def.descEn.includes("(Internal)"))) {
                return;
            }

            const parts = def.name.split('.');
            const category = parts.length > 1 ? parts[0] : "General";
            if (!groups[category]) groups[category] = [];
            groups[category].push(def);
        });

        for (const [category, defs] of Object.entries(groups)) {
            const label = category.replace(/([A-Z])/g, ' $1').trim();
            options += `<optgroup label="■ ${label}">`;

            defs.forEach(def => {
                const isSelected = selectedName === def.name ? "selected" : "";
                options += `<option value="${def.name}" ${isSelected}>${this.formatComboString(def)}</option>`;
            });

            options += `</optgroup>`;
        }

        return options;
    },

    addNode(x = null, y = null, id = null, dataOverride = null) {
        if (x === null || y === null) {
            const rect = document.getElementById('node-editor-area').getBoundingClientRect();
            x = (rect.width / 2 - this.transform.x) / this.transform.scale - 150;
            y = (rect.height / 2 - this.transform.y) / this.transform.scale;
        }

        const nodeId = id || `node_${this.nextNodeId++}`;
        const node = {
            id: nodeId,
            x: x, y: y,
            data: {
                name: `State_${this.nextNodeId - 1}`,
                nodeType: "Normal",
                priority: 5,
                isAlwaysActive: false,
                action: { actName: "None", actArgs: {} },
                clearConditions: []
            }
        };

        if (dataOverride) node.data = JSON.parse(JSON.stringify({...node.data, ...dataOverride}));
        this.nodes[nodeId] = node;

        const el = document.createElement('div');
        el.className = `node-block`;
        el.id = `node-${node.id}`;

        el.style.position = "absolute";
        el.style.left = `${node.x}px`;
        el.style.top = `${node.y}px`;
        el.style.width = "300px";
        el.style.background = "#1e1e1e";
        el.style.border = "1px solid #3c3c3c";
        el.style.borderRadius = "6px";
        el.style.boxShadow = "0 6px 12px rgba(0,0,0,0.5)";

        this.renderNodeHtml(nodeId, el);
        document.getElementById('nodes-layer').appendChild(el);
        this.bindNodeEvents(nodeId, el);

        return node;
    },

    renderNodeHtml(nodeId, el) {
        const node = this.nodes[nodeId];

        const inDisplay = node.data.nodeType === "Start" ? "none" : "block";
        const outDisplay = node.data.nodeType === "Goal" ? "none" : "block";

        let headerColor = "#252526";
        if (node.data.nodeType === "Start") headerColor = "#1e3a29";
        if (node.data.nodeType === "Goal") headerColor = "#4a2121";

        const actOptions = this.generateOptions(this.actionDefs, node.data.action.actName, "Select Action...");
        const showPriority = node.data.nodeType === "Start" || node.data.isAlwaysActive;

        let goalCondHtml = "";
        if (node.data.nodeType === "Goal") {
            goalCondHtml = `
                <div style="font-size:11px; color:#858585; font-weight:bold; border-bottom:1px solid #333; padding-bottom:2px; margin-top:8px;">▶ CLEAR CONDITIONS (Wait for...)</div>
                <div class="goal-cond-list" style="display:flex; flex-direction:column; gap:4px; background: #161616; border: 1px solid #333; padding: 6px; border-radius: 4px;"></div>
                <button class="btn-add-goal-cond" style="background:none; border:none; color:#dcdcaa; cursor:pointer; font-size:10px; margin-top:4px; text-align:left;">+ Add Clear Condition</button>
            `;
        }

        el.innerHTML = `
            <div class="port-in" style="display:${inDisplay}; position:absolute; left:-6px; top:14px; width:12px; height:12px; background:#4fc1ff; border-radius:50%; cursor:crosshair; border:2px solid #1e1e1e;" title="Input Condition (Drop here)"></div>
            <div class="port-out" style="display:${outDisplay}; position:absolute; right:-6px; top:14px; width:12px; height:12px; background:#4fc1ff; border-radius:50%; cursor:crosshair; border:2px solid #1e1e1e;" title="Next Action (Drag from here)"></div>
            
            <div class="node-header" style="background: ${headerColor}; padding: 6px 10px; border-bottom: 1px solid #3c3c3c; border-radius: 6px 6px 0 0; display: flex; justify-content: space-between; align-items: center; cursor: grab;">
                <input type="text" class="bind-node-name" value="${node.data.name}" style="background:transparent; border:none; color:#dcdcaa; font-weight:bold; font-size:13px; width:100px; outline:none;">
                
                <div style="display: flex; gap: 4px; align-items: center;">
                    <div style="display:${showPriority ? 'flex' : 'none'}; align-items:center; gap:2px; background:#1e1e1e; padding:1px 4px; border-radius:3px; border:1px solid #3c3c3c;" title="Priority (Lower is higher priority)">
                        <span style="font-size:10px; color:#858585;">Pri:</span>
                        <input type="number" class="bind-priority" value="${node.data.priority !== undefined ? node.data.priority : 5}" min="1" max="10" style="background:transparent; border:none; color:#4fc1ff; font-size:11px; width:24px; outline:none; text-align:center;">
                    </div>

                    <select class="bind-node-type" style="background: #1e1e1e; color: #ccc; border: 1px solid #3c3c3c; padding: 2px 4px; border-radius: 3px; font-size: 11px; outline: none; cursor: pointer;">
                        <option value="Start" ${node.data.nodeType === "Start" ? "selected" : ""}>🟢 Start</option>
                        <option value="Normal" ${node.data.nodeType === "Normal" ? "selected" : ""}>⚪ Normal</option>
                        <option value="Goal" ${node.data.nodeType === "Goal" ? "selected" : ""}>🔴 Goal</option>
                    </select>
                    <button class="node-delete" style="background: none; border: none; color: #f48771; cursor: pointer; font-size: 14px; padding: 0 4px;">✖</button>
                </div>
            </div>
            
            <div class="node-body" style="padding: 10px; display: flex; flex-direction: column; gap: 8px;">
                <label style="display:flex; align-items:center; gap:4px; font-size:11px; color:#c586c0; cursor:pointer;" title="If checked, this action runs independently without waiting for previous links.">
                    <input type="checkbox" class="bind-always-active" ${node.data.isAlwaysActive ? "checked" : ""}> Always Active (Independent)
                </label>
            
                <div style="font-size:11px; color:#858585; font-weight:bold; border-bottom:1px solid #333; padding-bottom:2px;">▶ ACTION (Max: 1)</div>
                <div style="display: flex; flex-direction: column; gap: 4px; background: #161616; border: 1px solid #333; padding: 6px; border-radius: 4px;">
                    <select class="bind-act" style="border: 1px solid #3c3c3c; background: #252526; color: #4fc1ff; padding: 2px 4px; border-radius: 3px; font-family: inherit; font-size: 11px; outline: none; width: 100%;">${actOptions}</select>
                    <div class="dynamic-args act-args" style="display: flex; gap: 4px; align-items: center; flex-wrap: wrap;"></div>
                </div>
                ${goalCondHtml}
            </div>
        `;
    },

    bindNodeEvents(nodeId, el) {
        const node = this.nodes[nodeId];
        const header = el.querySelector('.node-header');

        header.addEventListener('mousedown', (e) => {
            if (e.target.tagName === 'BUTTON' || e.target.tagName === 'SELECT' || e.target.tagName === 'INPUT') return;
            this.draggedNode = node;
            this.mousePos = { x: e.clientX, y: e.clientY };
            el.parentNode.appendChild(el);
            e.stopPropagation();
        });

        el.querySelector('.bind-node-name').onchange = (e) => node.data.name = e.target.value;

        const priInput = el.querySelector('.bind-priority');
        if (priInput) {
            priInput.onchange = (e) => {
                node.data.priority = parseInt(e.target.value) || 5;
            };
        }

        el.querySelector('.bind-node-type').onchange = (e) => {
            node.data.nodeType = e.target.value;
            if (node.data.nodeType === "Goal") {
                Object.values(this.edges).forEach(edge => {
                    if (edge.sourceId === nodeId) this.removeEdge(edge.id);
                });
            }
            if (node.data.nodeType === "Start") {
                Object.values(this.edges).forEach(edge => {
                    if (edge.targetId === nodeId) this.removeEdge(edge.id);
                });
            }
            this.renderNodeHtml(nodeId, el);
            this.bindNodeEvents(nodeId, el);
            this.updateEdges();
        };

        el.querySelector('.bind-always-active').onchange = (e) => {
            node.data.isAlwaysActive = e.target.checked;
            this.renderNodeHtml(nodeId, el);
            this.bindNodeEvents(nodeId, el);
            this.updateEdges();
        };

        el.querySelector('.node-delete').onclick = () => {
            el.remove();
            delete this.nodes[nodeId];
            Object.values(this.edges).forEach(edge => {
                if (edge.sourceId === nodeId || edge.targetId === nodeId) this.removeEdge(edge.id);
            });
            this.updateEdges();
        };

        el.querySelector('.bind-act').onchange = (e) => {
            node.data.action.actName = this.extractName(e.target.value);
            node.data.action.actArgs = {};
            this.buildArgsUI(el.querySelector('.act-args'), this.actionDefs, node.data.action.actName, node.data.action.actArgs);
        };
        this.buildArgsUI(el.querySelector('.act-args'), this.actionDefs, node.data.action.actName, node.data.action.actArgs);

        if (node.data.nodeType === "Goal") {
            if (!node.data.clearConditions) node.data.clearConditions = [];
            const btnAdd = el.querySelector('.btn-add-goal-cond');
            const listContainer = el.querySelector('.goal-cond-list');
            if (btnAdd && listContainer) {
                btnAdd.onclick = () => {
                    const condData = {id: `cond_${Date.now()}`, condName: "None", condArgs: {}, isNot: false};
                    node.data.clearConditions.push(condData);
                    this.renderConditionRow(node.data.clearConditions, condData, listContainer);
                };
                node.data.clearConditions.forEach(c => this.renderConditionRow(node.data.clearConditions, c, listContainer));
            }
        }
    },

    addEdge(sourceId, targetId, id = null, conditionsOverride = null) {
        const edgeId = id || `edge_${this.nextEdgeId++}`;
        const edge = {
            id: edgeId,
            sourceId,
            targetId,
            conditions: conditionsOverride || []
        };
        this.edges[edgeId] = edge;

        const panel = document.createElement('div');
        panel.className = "edge-panel";
        panel.id = `edge-ui-${edgeId}`;
        panel.style.position = "absolute";
        panel.style.transform = "translate(-50%, -50%)";
        panel.style.background = "rgba(30, 30, 30, 0.95)";
        panel.style.border = "1px solid #569cd6";
        panel.style.borderRadius = "4px";
        panel.style.padding = "6px";
        panel.style.minWidth = "160px";
        panel.style.pointerEvents = "auto";
        panel.style.boxShadow = "0 4px 8px rgba(0,0,0,0.5)";

        panel.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px; border-bottom:1px solid #444; padding-bottom:2px;">
                <span style="font-size:11px; color:#569cd6; font-weight:bold;">Condition (Wait for...)</span>
                <button class="edge-delete" style="background:none; border:none; color:#f48771; cursor:pointer; font-size:12px;">✖</button>
            </div>
            <div class="edge-cond-list" style="display:flex; flex-direction:column; gap:4px;"></div>
            <button class="btn-add-edge-cond" style="background:none; border:none; color:#dcdcaa; cursor:pointer; font-size:10px; margin-top:4px;">+ Add Condition</button>
        `;

        document.getElementById('edges-ui-layer').appendChild(panel);

        panel.addEventListener('mousedown', (e) => e.stopPropagation());

        panel.querySelector('.edge-delete').onclick = () => this.removeEdge(edgeId);

        panel.querySelector('.btn-add-edge-cond').onclick = () => {
            const condData = {id: `cond_${Date.now()}`, condName: "None", condArgs: {}, isNot: false};
            edge.conditions.push(condData);
            this.renderConditionRow(edge.conditions, condData, panel.querySelector('.edge-cond-list'));
        };

        const listContainer = panel.querySelector('.edge-cond-list');
        edge.conditions.forEach(cond => this.renderConditionRow(edge.conditions, cond, listContainer));

        this.updateEdges();
        return edge;
    },

    removeEdge(edgeId) {
        const panel = document.getElementById(`edge-ui-${edgeId}`);
        if (panel) panel.remove();
        delete this.edges[edgeId];
        this.updateEdges();
    },

    renderConditionRow(conditionsArray, condData, container) {
        const row = document.createElement('div');
        const condOptions = this.generateOptions(this.conditionDefs, condData.condName, "Select Condition...");

        row.innerHTML = `
            <div style="background: #252526; border: 1px dashed #444; padding: 4px; border-radius: 3px; position: relative; margin-bottom: 2px;">
                <button class="cond-delete" style="position: absolute; top: 4px; right: 2px; background: none; border: none; color: #f48771; cursor: pointer; font-size:9px;">✖</button>
                <select class="bind-cond" style="border: 1px solid #3c3c3c; background: #1e1e1e; color: #dcdcaa; padding: 1px 2px; border-radius: 2px; font-family: inherit; font-size: 10px; outline: none; width: calc(100% - 15px);">${condOptions}</select>
                <label style="display: flex; align-items: center; gap: 2px; color: #dcdcaa; font-size: 10px; margin-top: 2px;">
                    <input type="checkbox" class="bind-cond-not" ${condData.isNot ? "checked" : ""}> 🚫 NOT
                </label>
                <div class="dynamic-args cond-args" style="display: flex; gap: 2px; flex-wrap: wrap; margin-top: 2px;"></div>
            </div>
        `;
        container.appendChild(row);

        row.querySelector('.cond-delete').onclick = () => {
            const index = conditionsArray.findIndex(c => c.id === condData.id);
            if (index > -1) conditionsArray.splice(index, 1);
            row.remove();
        };
        row.querySelector('.bind-cond').onchange = (e) => {
            condData.condName = this.extractName(e.target.value);
            condData.condArgs = {};
            this.buildArgsUI(row.querySelector('.cond-args'), this.conditionDefs, condData.condName, condData.condArgs);
        };
        row.querySelector('.bind-cond-not').onchange = (e) => {
            condData.isNot = e.target.checked;
        };
        this.buildArgsUI(row.querySelector('.cond-args'), this.conditionDefs, condData.condName, condData.condArgs);
    },

    buildArgsUI(containerEl, defsArray, targetName, targetArgsObj) {
        containerEl.innerHTML = "";
        if (targetName !== "None" && targetName !== "Select...") {
            const def = defsArray.find(d => d.name === targetName);
            if (def && def.args) this.generateInputs(containerEl, def.args, targetArgsObj);
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
            div.style.gap = "2px";

            let inputHtml = "";
            let isSelect = false;

            const width = (type === 'num') ? '35px' : '65px';
            const style = `width: ${width}; padding: 1px 2px; background: #1e1e1e; color: #d4d4d4; border: 1px solid #3c3c3c; border-radius: 2px; font-family: inherit; font-size: 10px; outline: none;`;

            if (name === "handType") {
                isSelect = true;
                inputHtml = `<select style="${style}"><option value="mainhand" ${val === "mainhand" ? "selected" : ""}>Main</option><option value="offhand" ${val === "offhand" ? "selected" : ""}>Off</option></select>`;
            } else if (name === "slotType") {
                isSelect = true;
                inputHtml = `<select style="${style}"><option value="input" ${val === "input" ? "selected" : ""}>Top</option><option value="fuel" ${val === "fuel" ? "selected" : ""}>Btm</option></select>`;
            } else if (type === 'bool' || name === "isSneaking" || name === "open") {
                isSelect = true;
                inputHtml = `<select style="${style}"><option value="true" ${String(val) === "true" ? "selected" : ""}>Yes</option><option value="false" ${String(val) === "false" ? "selected" : ""}>No</option></select>`;
            } else if (name === "entityId") {
                inputHtml = `<input type="text" list="dl-entities" value="${val}" style="${style}">`;
            } else if (name === "blockId") {
                inputHtml = `<input type="text" list="dl-blocks" value="${val}" style="${style}">`;
            } else if (name === "itemId") {
                inputHtml = `<input type="text" list="dl-items" value="${val}" style="${style}">`;
            } else {
                inputHtml = `<input type="text" value="${val}" style="${style}">`;
            }

            const labelMap = { "entityId": "Ent", "blockId": "Blk", "itemId": "Itm", "distance": "Dist", "radius": "Rad", "reach": "Rch", "speed": "Spd", "count": "Cnt", "minCount": "Min", "chance": "Chnc", "percent": "Pct", "ticks": "Tk", "timerId": "Tmr", "resultId": "Res" };
            let label = labelMap[name] || name.substring(0, 4);

            div.innerHTML = `<span style="font-size:9px; color:#9cdcfe;">${label}:</span>${inputHtml}`;

            const inputEl = div.querySelector(isSelect ? 'select' : 'input');
            inputEl.onchange = (e) => dataObj[name] = e.target.value;
            inputEl.oninput = (e) => dataObj[name] = e.target.value;

            containerEl.appendChild(div);
        });
    },

    getFlowPriority(nodeId, visited = new Set()) {
        const node = this.nodes[nodeId];
        if (!node) return 5;
        if (node.data.nodeType === "Start" || node.data.isAlwaysActive) {
            return node.data.priority !== undefined ? node.data.priority : 5;
        }

        if (visited.has(nodeId)) return 5;
        visited.add(nodeId);

        const incomingEdges = Object.values(this.edges).filter(e => e.targetId === nodeId);
        for (const edge of incomingEdges) {
            const p = this.getFlowPriority(edge.sourceId, visited);
            if (p !== null) return p;
        }
        return 5;
    },

    generateLua() {
        const allNodes = Object.values(this.nodes);
        if (allNodes.length === 0) throw new Error("There are no Action Nodes.");

        const targetLaneActions = [
            "TargetEntity.FindAndTarget",
            "TargetEntity.TargetAttacker",
            "TargetBlock.FindAndTarget"
        ];

        let luaCode = `-- AI Hidden State Machine generated in Minecraft standard format\nmob.clearGoals()\n\n`;

        allNodes.forEach((node, index) => {
            const goalVar = `goalDef_${index + 1}`;
            const actName = node.data.action.actName;
            const isTargetNode = targetLaneActions.includes(actName);
            const computedPriority = this.getFlowPriority(node.id);

            luaCode += `-- [State: ${node.data.name}] ${isTargetNode ? "Target Lane (Auto)" : "Action Lane"}\n`;
            luaCode += `local ${goalVar} = {\n`;
            luaCode += `    priority = ${computedPriority},\n`;
            luaCode += `    isTarget = ${isTargetNode},\n`;

            if (node.data.isAlwaysActive) {
                luaCode += `    canUse = {},\n`;
            } else if (node.data.nodeType === "Start") {
                luaCode += `    canUse = { GoalCondition.System.IsInitialState({ key = "state_${node.id}" }) },\n`;
            } else {
                luaCode += `    canUse = { GoalCondition.System.HasStateKey({ key = "state_${node.id}" }) },\n`;
            }

            const tickList = [];

            if (actName !== "None" && actName !== "Select Action...") {
                const actProps = [];
                for (const [k, v] of Object.entries(node.data.action.actArgs)) {
                    actProps.push(`${k} = ${this.formatArgValue(v)}`);
                }
                const argsStr = actProps.length > 0 ? `{ ${actProps.join(", ")} }` : "";
                tickList.push(`GoalAction.${actName}(${argsStr})`);
            }

            if (node.data.nodeType === "Goal") {
                const condList = [];
                if (node.data.clearConditions) {
                    for (const cond of node.data.clearConditions) {
                        if (cond.condName !== "None" && cond.condName !== "Select Condition...") {
                            const argsMap = [];
                            if (cond.isNot) argsMap.push(`isNot = true`);
                            for (const [k, v] of Object.entries(cond.condArgs)) {
                                argsMap.push(`${k} = ${this.formatArgValue(v)}`);
                            }
                            const argsStr = argsMap.length > 0 ? `{ ${argsMap.join(", ")} }` : "";
                            condList.push(`GoalCondition.${cond.condName}(${argsStr})`);
                        }
                    }
                }
                const conditionStr = condList.length > 0 ? `{ ${condList.join(", ")} }` : "{}";
                tickList.push(`GoalAction.System.ClearStateKey({ condition = ${conditionStr} })`);
            } else {
                Object.values(this.edges).filter(e => e.sourceId === node.id).forEach(edge => {
                    const condList = [];
                    if (node.data.nodeType === "Start" || node.data.isAlwaysActive) {
                        condList.push(`GoalCondition.System.IsInitialState({ key = "state_${edge.targetId}" })`);
                    }

                    for (const cond of edge.conditions) {
                        if (cond.condName !== "None" && cond.condName !== "Select Condition...") {
                            const argsMap = [];
                            if (cond.isNot) argsMap.push(`isNot = true`);
                            for (const [k, v] of Object.entries(cond.condArgs)) {
                                argsMap.push(`${k} = ${this.formatArgValue(v)}`);
                            }
                            const argsStr = argsMap.length > 0 ? `{ ${argsMap.join(", ")} }` : "";
                            condList.push(`GoalCondition.${cond.condName}(${argsStr})`);
                        }
                    }
                    const conditionStr = condList.length > 0 ? `{ ${condList.join(", ")} }` : "{}";
                    tickList.push(`GoalAction.System.SetStateKey({ key = "state_${edge.targetId}", condition = ${conditionStr} })`);
                });
            }

            let tickArrayStr = "{}";
            if (tickList.length > 0) tickArrayStr = `{\n        ${tickList.join(",\n        ")}\n    }`;

            luaCode += `    tick = ${tickArrayStr}\n`;
            luaCode += `}\n`;
            luaCode += `mob.buildGoal(${goalVar})\n\n`;
        });

        const graphJson = JSON.stringify({ nodes: this.nodes, edges: this.edges, nextNodeId: this.nextNodeId, nextEdgeId: this.nextEdgeId });
        luaCode += `--[[@NODE_GRAPH_DATA\n${graphJson}\n]]`;

        return luaCode;
    },

    loadLua(code) {
        this.clear();
        this.transform = { x: 0, y: 0, scale: 1 };
        this.updateTransform();

        const match = code.match(/--\[\[@NODE_GRAPH_DATA\s*([\s\S]*?)\]\]/);
        if (match) {
            try {
                const data = JSON.parse(match[1]);
                this.nextNodeId = data.nextNodeId;
                this.nextEdgeId = data.nextEdgeId || 1;

                for (const [id, node] of Object.entries(data.nodes)) {
                    if (!node.data.nodeType) node.data.nodeType = "Normal";
                    if (node.data.priority === undefined) node.data.priority = 5;
                    if (node.data.actions && node.data.actions.length > 0) {
                        node.data.action = node.data.actions[0];
                    } else if (!node.data.action) {
                        node.data.action = { actName: "None", actArgs: {} };
                    }
                    if (!node.data.clearConditions) node.data.clearConditions = [];
                    this.addNode(node.x, node.y, id, node.data);
                }
                if (data.edges) {
                    for (const [id, edge] of Object.entries(data.edges)) {
                        this.addEdge(edge.sourceId, edge.targetId, id, edge.conditions);
                    }
                }
                return;
            } catch (e) {
                console.error("Failed to restore the graph", e);
            }
        }

        this.addNode(null, null, null, { nodeType: "Start" });
    }
};