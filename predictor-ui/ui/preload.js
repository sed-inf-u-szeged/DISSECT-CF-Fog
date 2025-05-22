const {
    contextBridge,
    ipcRenderer
} = require('electron');

contextBridge.exposeInMainWorld(
    'electron-api', {
        onDataUpdated: (callback) => ipcRenderer.on('data-updated', (_event, value) => callback(value)),
        onDataError: (callback) => ipcRenderer.on('data-error', (_event, value) => callback(value)),
        removeAllListeners: () => {
            ipcRenderer.removeAllListeners('data-updated');
            ipcRenderer.removeAllListeners('data-error');
        }
    }
);
