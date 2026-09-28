export const API = {
    socket: null,
    messageQueue: {},
    messageId: 0,

    connect() {
        return new Promise((resolve, reject) => {
            // ★変更: 開いているページによって接続先のURLパスを変える
            const type = window.EDITOR_TYPE || 'machine';
            this.socket = new WebSocket(`ws://localhost:14321/${type}`);

            this.socket.onopen = () => {
                console.log(`WebSocket connected to Minecraft! (${type})`);
                resolve();
            };

            this.socket.onmessage = (event) => {
                const data = JSON.parse(event.data);

                if (data.msgId && this.messageQueue[data.msgId]) {
                    this.messageQueue[data.msgId].resolve(data);
                    delete this.messageQueue[data.msgId];
                    return;
                }

                if (data.type === 'notification') {
                    console.log("Server Notification:", data.message);
                } else if (data.type === 'linked_vm') {
                    document.dispatchEvent(new CustomEvent('LinkedVmChanged', {detail: data}));
                } else if (data.type === 'vm_error') {
                    document.dispatchEvent(new CustomEvent('VmError', {detail: data}));
                }
            };

            this.socket.onerror = (e) => reject(e);
            this.socket.onclose = () => console.warn("WebSocket Connection Closed.");
        });
    },

    sendRequest(action, payload = {}) {
        return new Promise((resolve, reject) => {
            if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
                return reject("WebSocket is not connected.");
            }

            const msgId = ++this.messageId;
            this.messageQueue[msgId] = {resolve, reject};

            this.socket.send(JSON.stringify({
                action,
                msgId,
                ...payload
            }));
        });
    },

    async fetchSuggestions() {
        return await this.sendRequest("get_api_data");
    },

    async saveProgram(programName, code) {
        return await this.sendRequest("save_file", {programName, content: code});
    },

    async loadProgram(programName) {
        return await this.sendRequest("load_file", {programName});
    },

    async fetchFiles() {
        const res = await this.sendRequest("get_files");
        return res.workspaces;
    },

    async fetchLinkInfo() {
        return await this.sendRequest("get_link_info");
    },

    async deleteProgram(programName) {
        return await this.sendRequest("delete_file", {programName});
    }
};