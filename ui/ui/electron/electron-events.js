const { dialog } = require('electron');
const fs = require('fs');
const { PythonShell } = require('python-shell');
const path = require('path');

const LSTM_TRAINER_SCRIPT_PATH = path.join(__dirname, '..', '..', 'scripts', 'predictor_models', 'lstm_trainer.py');

module.exports.handleOpenFile = (event, args) => {
    return new Promise((resolve, reject) => {
        dialog.showOpenDialog({
            properties: ['openFile'],
            filters: [
                {
                    name: args.options[0].label,
                    extensions: [args.options[0].id]
                }
            ]
        }).then((response) => {
            if (!response.canceled) {
                resolve(response.filePaths[0]);
            } else {
                resolve(null);
            }
        });
    });
}

module.exports.handleOpenJSON = (event, args) => {
    return new Promise((resolve, reject) => {
        dialog.showOpenDialog({
            properties: ['openFile'],
            filters: [
                {
                    name: 'JSON file (*.json)',
                    extensions: ['json']
                }
            ]
        }).then((response) => {
            if (!response.canceled) {
                fs.readFile(response.filePaths[0], 'utf-8', (err, data) => {
                    if (err) {
                        reject(null);
                    } else {
                        resolve(JSON.parse(data));
                    }
                });
            } else {
                resolve(null);
            }
        });
    });
}

module.exports.handleOpenDirectory = (event, args) => {
    return new Promise((resolve, reject) => {
        dialog.showOpenDialog({
            properties: ['openDirectory'],
        }).then((response) => {
            if (!response.canceled) {
                resolve(response.filePaths[0]);
            } else {
                resolve(null);
            }
        });
    });
}

module.exports.handleLSTMtraining = (event, args, sendToUI) => {
    return new Promise((resolve, reject) => {
        const pyshell = new PythonShell(LSTM_TRAINER_SCRIPT_PATH);
        let progress = 0;

        pyshell.send(JSON.stringify(args));

        pyshell.on('message', (message) => {
            // received a message sent from the Python script (a simple "print" statement)
            console.log(message)
            if (message.includes('pyshell_data') && message.includes('progress')) {
                progress += 1;
                sendToUI('train-lstm-model-progress', { progress });
            } else if (message.includes('Epoch')) {
                progress += 1;
                sendToUI('train-lstm-model-progress', { progress });
            }
        });

        pyshell.end((err, code, signal) => {
            if (code === 1225) {
                return reject({ message: 'No dataset have been found!' });
            }

            if (code === 1226) {
                return reject({ message: 'Not enough have been found!' });
            }

            if (err) {
                return reject({ message: err.message || 'Something went wrong while training!' });
            }

            return resolve({ message: 'OK' })
        });

    });
}