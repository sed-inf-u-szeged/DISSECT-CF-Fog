const {
    contextBridge,
    ipcRenderer
} = require('electron');

contextBridge.exposeInMainWorld(
    'electron-api', {
        onMobilityUpdated: (callback) => ipcRenderer.on('mobility-updated', (_event, value) => callback(value)),
        onDataUpdated: (callback) => ipcRenderer.on('data-updated', (_event, value) => callback(value)),
        onDataError: (callback) => ipcRenderer.on('data-error', (_event, value) => callback(value)),
        removeAllListeners: () => {
            ipcRenderer.removeAllListeners('mobility-updated');
            ipcRenderer.removeAllListeners('data-updated');
            ipcRenderer.removeAllListeners('data-error');
        }
    }
);
