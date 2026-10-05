const {
  app,
  BrowserWindow
} = require('electron');
const {
  existsSync,
  readdirSync,
  statSync,
  openSync,
  readSync,
  closeSync
} = require('fs');
const path = require('path');
const sqlite3 = require('sqlite3').verbose();

let win;
let db = null;
let dbPath = null;
let refreshTimer = null;
let refreshInProgress = false;
let lastSentData = '';

const predictionsForFeature = new Map();
const simulationResultsPath = path.join(__dirname, '..', '..', 'simulator', 'sim_res');

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

    if (refreshTimer) {
      clearInterval(refreshTimer);
    }

    refreshTimer = setInterval(fetchAndSendData, 5000)
  })

  win.on('closed', () => {
    win = null;
  });
}

function isSqliteDatabase(databasePath) {
  let fileDescriptor = null;

  try {
    const databaseStats = statSync(databasePath);

    if (!databaseStats.isFile() || databaseStats.size < 16) {
      return false;
    }

    const header = Buffer.alloc(16);
    fileDescriptor = openSync(databasePath, 'r');

    if (readSync(fileDescriptor, header, 0, 16, 0) !== 16) {
      return false;
    }

    return header.toString('ascii') === 'SQLite format 3\u0000';
  } catch (error) {
    return false;
  } finally {
    if (fileDescriptor !== null) {
      closeSync(fileDescriptor);
    }
  }
}

function findLatestDatabasePath() {
  if (!existsSync(simulationResultsPath)) {
    return null;
  }

  const candidates = readdirSync(simulationResultsPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(simulationResultsPath, entry.name, 'database.db'))
    .filter((databasePath) => existsSync(databasePath) && isSqliteDatabase(databasePath))
    .map((databasePath) => ({
      databasePath,
      modifiedAt: statSync(databasePath).mtimeMs
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt);

  return candidates.length > 0 ? candidates[0].databasePath : null;
}

function closeDatabase(database) {
  return new Promise((resolve) => {
    if (!database) {
      resolve();
      return;
    }

    database.close((error) => {
      if (error) {
        console.warn('Database close warning:', error.message);
      }

      resolve();
    });
  });
}

function openDatabase(databasePath) {
  return new Promise((resolve, reject) => {
    const database = new sqlite3.Database(
      databasePath,
      sqlite3.OPEN_READONLY,
      (error) => {
        if (error) {
          reject(error);
          return;
        }

        resolve(database);
      }
    );
  });
}

async function connectToLatestDatabase() {
  const latestDatabasePath = findLatestDatabasePath();

  if (!latestDatabasePath) {
    return false;
  }

  if (db && dbPath === latestDatabasePath) {
    return true;
  }

  await closeDatabase(db);

  db = null;
  dbPath = null;
  predictionsForFeature.clear();
  lastSentData = '';

  try {
    db = await openDatabase(latestDatabasePath);
    dbPath = latestDatabasePath;

    console.log('Database opened successfully:', dbPath);

    return true;
  } catch (error) {
    console.error(
      'Database connection error:',
      latestDatabasePath,
      error.message
    );

    return false;
  }
}

async function fetchAndSendData() {
  if (refreshInProgress) {
    return;
  }

  refreshInProgress = true;

  try {
    if (!await connectToLatestDatabase()) {
      return;
    }

    const tableRows = await readRows(`
      SELECT name
      FROM sqlite_master
      WHERE type = 'table'
        AND name LIKE '%_feature_prediction'
      ORDER BY name;
    `);

    const newPredictionsForFeature = new Map();

    for (const tableRow of tableRows) {
      const predictionTableName = tableRow.name;
      const lifecycleTableName = predictionTableName.replace(
        '_feature_prediction',
        '_feature_lifecycle'
      );

      const lifecycleData = await readLifecycleData(lifecycleTableName);

      const predictionRows = await readRows(`
        SELECT *
        FROM ${quoteIdentifier(predictionTableName)}
        ORDER BY prediction_number;
      `);

      const predictions = [];

      for (const row of predictionRows) {
        try {
          const predictorSettings = JSON.parse(row["predictor_settings"]);
          const predictorName = predictorSettings.predictor_name
            || predictorSettings.name
            || 'unknown';

          predictions.push({
            feature_name: predictionTableName,
            original_data: JSON.parse(row["original_data"]),
            preprocessed_data: JSON.parse(row["preprocessed_data"]),
            test_data_beginning: JSON.parse(row["test_data_beginning"]),
            test_data_end: JSON.parse(row["test_data_end"]),
            prediction_future: JSON.parse(row["prediction_future"]),
            prediction_test: JSON.parse(row["prediction_test"]),
            error_metrics: JSON.parse(row["error_metrics"]),
            prediction_time: JSON.parse(row["prediction_time"]),
            prediction_number: JSON.parse(row["prediction_number"]),
            predictor_settings: predictorSettings,
            lifecycle: lifecycleData[predictorName] || null
          })
        } catch (error) {
          console.warn(
            'Skipping invalid prediction row:',
            predictionTableName,
            row.prediction_number,
            error.message
          );
        }
      }

      newPredictionsForFeature.set(
        predictionTableName,
        predictions
      );
    }

    const serializedData = JSON.stringify(
      Array.from(newPredictionsForFeature.entries())
    );

    if (serializedData !== lastSentData) {
      predictionsForFeature.clear();

      newPredictionsForFeature.forEach(
        (predictions, featureName) => {
          predictionsForFeature.set(
            featureName,
            predictions
          );
        }
      );

      lastSentData = serializedData;

      if (win && !win.isDestroyed()) {
        win.webContents.send(
          'data-updated',
          predictionsForFeature
        );
      }
    }
  } catch (error) {
    console.error(
      'Database query error:',
      error.message
    );

    if (win && !win.isDestroyed()) {
      win.webContents.send(
        'data-error',
        error.message
      );
    }
  } finally {
    refreshInProgress = false;
  }
}

async function readLifecycleData(tableName) {
  try {
    const rows = await readRows(`
      SELECT predictor_name,
             lifecycle_state,
             is_active,
             latest_metrics,
             updated_at
      FROM ${quoteIdentifier(tableName)};
    `);

    return rows.reduce((result, row) => {
      result[row.predictor_name] = {
        state: row.lifecycle_state,
        is_active: row.is_active === 1,
        metrics: row.latest_metrics
          ? JSON.parse(row.latest_metrics)
          : null,
        updated_at: row.updated_at
      };

      return result;
    }, {});
  } catch (error) {
    if (
      error.message
      && error.message.includes('no such table')
    ) {
      return {};
    }

    throw error;
  }
}

function readRows(query) {
  return new Promise((resolve, reject) => {
    if (!db) {
      reject(new Error('Database is not ready.'));
      return;
    }

    db.all(query, [], (error, rows) => {
      if (error) {
        reject(error);
        return;
      }

      resolve(rows || []);
    });
  });
}

function quoteIdentifier(identifier) {
  return `"${String(identifier).replace(/"/g, '""')}"`;
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }

  closeDatabase(db);
  db = null;
  dbPath = null;

  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
