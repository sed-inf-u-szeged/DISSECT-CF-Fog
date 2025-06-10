const {
  app,
  BrowserWindow
} = require('electron');
const { readdirSync } = require('fs');
const path = require('path');
const sqlite3 = require('sqlite3').verbose();

let win;

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

  //win.webContents.openDevTools();
  win.loadFile(path.join(__dirname, 'dist/dissect-cf-predictor-ui/index.html'));

  win.webContents.once('did-finish-load', () => {
    fetchAndSendData();
    setInterval(fetchAndSendData, 5000)
  })
}

folders = readdirSync(path.join(__dirname, "..", "..", "simulator", "sim_res"))

const dbPath = path.join(__dirname, "..", "..", "simulator", "sim_res", folders[folders.length - 1], "database.db")


const db = new sqlite3.Database(dbPath, sqlite3.OPEN_READONLY, (error, open) => {
    if (error) {
        console.error('DB csatlakozási hiba:', error);
    }

    if (open) {
        console.error('----- OPENED')
    }
});

const predictionsForFeature = new Map();
let newPredictions = true;

function fetchAndSendData() {
    if (!db || !db.open) {
        console.error('---------' + dbPath)
        if (win && win.webContents) {
            win.webContents.send('data-error', 'Adatbázis nem elérhető.');
            console.error('---------!' + dbPath)
        }
        return;
    }
    
    const sql = `SELECT name FROM sqlite_master WHERE type='table' and name LIKE '%prediction%';`;

    db.each(
        sql, [],
        (err, row) => {
            if (err) {
                console.error('Adatlekérdezési hiba (Tábla adatok):', err.message);
                win.webContents.send('data-error', err.message);
            } else {
                rowName = row["name"]

                if (!predictionsForFeature.has(rowName)) {
                    predictionsForFeature.set(rowName, [])
                } 
                getPrediction(rowName)
                
            }
        },
        (err, _) => {
            if (err) {
                console.error('Adatlekérdezési hiba (Tábla adatok):', err.message);
                win.webContents.send('data-error', err.message);
            } else if (predictionsForFeature && newPredictions) {
                newPredictions = false;

                win.webContents.send('data-updated', predictionsForFeature);
            }
        }

    )
}

function getPrediction(tableName) {
    const query = `SELECT * FROM ${tableName} LIMIT 100 OFFSET ${predictionsForFeature.get(tableName).length}`
    db.each(query, [],
        (err, row) => {
            if (err) {
                console.error('Adatlekérdezési hiba (Tábla adatok):', err.message);
                win.webContents.send('data-error', err.message);
            } else {
                predictionsForFeature.get(tableName).push({
                    feature_name: tableName,
                    original_data: JSON.parse(row["original_data"]),
                    preprocessed_data: JSON.parse(row["preprocessed_data"]),
                    test_data_beginning: JSON.parse(row["test_data_beginning"]),
                    test_data_end: JSON.parse(row["test_data_end"]),
                    prediction_future: JSON.parse(row["prediction_future"]),
                    prediction_test: JSON.parse(row["prediction_test"]),
                    error_metrics: JSON.parse(row["error_metrics"]),
                    prediction_time: JSON.parse(row["prediction_time"]),
                    prediction_number: JSON.parse(row["prediction_number"]),
                    predictor_settings: JSON.parse(row["predictor_settings"])
                })
            }
        },
        (err, count) => {
            if (err) {
                console.error('Adatlekérdezési hiba (Tábla adatok):', err.message);
                win.webContents.send('data-error', err.message);
            }
            if (count > 0) {
              console.log(`Predictions read this cycle: ${count}`)
              newPredictions = true;
            }
        }
    );
    return []
}

app.on('ready', createWindow);
