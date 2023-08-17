const {
  app,
  BrowserWindow,
  ipcMain
} = require('electron');
const path = require('path');
const Net = require('net');
const { handleOpenFile, handleOpenJSON, handleOpenDirectory, handleLSTMtraining } = require('./electron/electron-events');
const { handleSocketData, createACKMessage } = require('./electron/socket-events');
const { Events } = require('./electron/app-events');

const client = new Net.Socket();
let win;
let connected = false;

const sleep = (ms) => {
  return new Promise(resolve => setTimeout(resolve, ms));
}

const createWindow = () => {
  win = new BrowserWindow({
    width: 1400,
    height: 850,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      enableRemoteModule: false,
      preload: path.join(__dirname, 'preload.js')
    },
    autoHideMenuBar: true
  });

  win.webContents.openDevTools();
  win.loadFile(path.join(__dirname, 'dist/dissect-cf-predictor-ui/index.html'));

  win.on('close', () => {
    //client.write(JSON.stringify({ sender: 'APPLICATION_INTERFACE', destination: 'APPLICATION_FEATURE_HANDLER', event: 'close-application', data: 'CLOSE' }) + '\r\n'); // TODO
  });
}

app.on('ready', createWindow);

ipcMain.on(Events.OPEN_FILE, (event, args) => {
  handleOpenFile(event, args).then((result) => {
    if (result) {
      sendToUI(`${Events.OPEN_FILE}-result-${args.id}`, result);
    } else {
      sendToUI(`${Events.OPEN_FILE}-result-${args.id}`, null);
    }
  }).catch((error) => {
    handleError(Events.OPEN_FILE, error);
  });
});

ipcMain.on(Events.OPEN_JSON, (event, args) => {
  handleOpenJSON(event, args).then((result) => {
    if (result) {
      sendToUI(`${Events.OPEN_JSON}-result-${args.id}`, result);
    } else {
      sendToUI(`${Events.OPEN_JSON}-result-${args.id}`, null);
    }
  }).catch((error) => {
    handleError(Events.OPEN_JSON, error);
  });
});

ipcMain.on(Events.OPEN_DIRECTORY, (event, args) => {
  handleOpenDirectory(event, args).then((result) => {
    if (result) {
      sendToUI(`${Events.OPEN_DIRECTORY}-result-${args.id}`, result);
    } else {
      sendToUI(`${Events.OPEN_DIRECTORY}-result-${args.id}`, null);
    }
  }).catch((error) => {
    handleError(Events.OPEN_DIRECTORY, error);
  });
});

ipcMain.on(Events.TRAIN_LSTM_MODEL, (event, args) => {
  handleLSTMtraining(event, args, sendToUI).then((result) => {
    sendToUI(`${Events.TRAIN_LSTM_MODEL}-result`, result);
  }).catch((error) => {
    sendToUI(`${Events.TRAIN_LSTM_MODEL}-result`, null, error);
    handleError(Events.TRAIN_LSTM_MODEL, error);
  });
});

ipcMain.on(Events.SOCKET_CONNECT, async (event, args) => {
  while (!connected) {
    client.connect({ port: 65432, host: '127.0.0.1' });
    await sleep(1000);
  }
});

ipcMain.on(Events.SOCKET_SEND, (event, args) => {
  if (client.writableEnded) {
    return;
  }

  try {
    send(args);
  } catch (error) {
    handleError(Events.SOCKET_SEND, error);
  }
});

client.on('data', (buffer) => {
  handleSocketData(buffer).then((result) => {
    if (result.socketResponse) {
      const response = result.socketResponse;
      console.log(`[SOCKET-IN ] E: ${response.event}`);
      switch (response.event) {
        case Events.MESSAGE_GET_NAME:
          send({ event: `${Events.MESSAGE_GET_NAME}-response`, data: { name: 'APPLICATION_INTERFACE' } });
          break;
        case Events.MESSAGE_GET_SIMULATION_SETTINGS:
          // Skip and wait in 'socket-send'
          // Do not delete
          break;
        case Events.MESSAGE_STOP_CONNECTION:
          sendToUI('simulation-ended', true);
          client.end();
          connected = false;
          // TODO
          break;
        default:
          sendToUI('socket-response', response);
          send(createACKMessage(response.event));
      }
    }

    if (result.ackResponse) {
      send(createACKMessage(result.ackResponse.event), true);
    }

    if (result.wait) {
      // pass
    }
  }).catch((error) => {
    handleError('socket-data', error);
  });
});

client.on('end', () => {
  console.log('Requested an end to the TCP connection');
  sendToUI('simulation-ended', true);
  //process.exit(0);
});

client.on('error', (err) => {
  if (err['code'] === 'ECONNREFUSED' || err['code'] === 'ECONNRESET') {
    return;
  }
  handleError('error', err);
});

client.on('connect', () => {
  connected = true;
});

const handleError = (event, error) => {
  console.log(`Error event: ${event} | Error: `, error);
}

const send = (data, logFlag) => {
  if (!logFlag) {
    console.log(`[SOCKET-OUT] E: ${data.event}`);
  }
  client.write(JSON.stringify(data) + '\r\n');
}

const sendToUI = (event, data, error) => {
  try {
    console.log(`[UP] E: ${event}`);
    win.webContents.send(event, { result: data, error });
  } catch (err) {
    handleError('socket-response', err);
  }
}
