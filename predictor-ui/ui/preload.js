const {
    contextBridge,
    ipcRenderer
} = require('electron');

contextBridge.exposeInMainWorld(
    'electron-api', {
        openMobilityRun: () => ipcRenderer.invoke('open-mobility-run'),
        getMobilityJob: () => ipcRenderer.invoke('get-mobility-job'),
        startMobilityJob: (request) => ipcRenderer.invoke('start-mobility-job', request),
        cancelMobilityJob: () => ipcRenderer.invoke('cancel-mobility-job'),
        onMobilityJobUpdated: (callback) => ipcRenderer.on('mobility-job-updated', (_event, value) => callback(value)),
        onMobilityLiveUpdated: (callback) => ipcRenderer.on('mobility-live-updated', (_event, value) => callback(value)),
        onMobilityUpdated: (callback) => ipcRenderer.on('mobility-updated', (_event, value) => callback(value)),
        onDataUpdated: (callback) => ipcRenderer.on('data-updated', (_event, value) => callback(value)),
        onDataError: (callback) => ipcRenderer.on('data-error', (_event, value) => callback(value)),
        removeAllListeners: () => {
            ipcRenderer.removeAllListeners('mobility-job-updated');
            ipcRenderer.removeAllListeners('mobility-live-updated');
            ipcRenderer.removeAllListeners('mobility-updated');
            ipcRenderer.removeAllListeners('data-updated');
            ipcRenderer.removeAllListeners('data-error');
        }
    }
);
