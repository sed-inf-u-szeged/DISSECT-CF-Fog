const {
    contextBridge,
    ipcRenderer
} = require('electron');

contextBridge.exposeInMainWorld(
    'electron-api', {
        send: (channel, data) => {
            console.log(`[DOWN] E: ${channel}`);
            ipcRenderer.send(channel, data);
        },
        receive: (channel, func) => {
            ipcRenderer.on(channel, (event, ...args) => func(...args));
        }
    }
);
