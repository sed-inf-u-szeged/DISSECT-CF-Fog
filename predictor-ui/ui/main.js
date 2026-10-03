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
let currentDbPath = null;
let databaseReady = false;
let lastSentData = '';
let refreshTimer = null;

const predictionsForFeature = new Map();

/*
 * __dirname:
 * D:\Github\DISSECT-CF-Fog\predictor-ui\ui
 *
 * Correct simulation result directory:
 * D:\Github\DISSECT-CF-Fog\simulator\sim_res
 */
const simulationResultsRoot = path.join(
  __dirname,
  '..',
  '..',
  'simulator',
  'sim_res'
);

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

  win.loadFile(
    path.join(
      __dirname,
      'dist',
      'dissect-cf-predictor-ui',
      'index.html'
    )
  );

  win.webContents.once('did-finish-load', () => {
    fetchAndSendData();

    if (refreshTimer) {
      clearInterval(refreshTimer);
    }

    refreshTimer = setInterval(
      fetchAndSendData,
      5000
    );
  });

  win.on('closed', () => {
    win = null;
  });
};

function isSqliteDatabase(databasePath) {
  let fileDescriptor = null;

  try {
    const databaseStats = statSync(databasePath);

    if (!databaseStats.isFile() || databaseStats.size < 16) {
      return false;
    }

    const headerBuffer = Buffer.alloc(16);

    fileDescriptor = openSync(databasePath, 'r');

    const bytesRead = readSync(
      fileDescriptor,
      headerBuffer,
      0,
      16,
      0
    );

    if (bytesRead !== 16) {
      return false;
    }

    const header = headerBuffer.toString(
      'ascii',
      0,
      16
    );

    return header === 'SQLite format 3\u0000';
  } catch (error) {
    console.warn(
      'Could not validate database:',
      databasePath,
      error.message
    );

    return false;
  } finally {
    if (fileDescriptor !== null) {
      closeSync(fileDescriptor);
    }
  }
}

function findLatestDatabasePath() {
  if (!existsSync(simulationResultsRoot)) {
    console.error(
      'Simulation result directory does not exist:',
      simulationResultsRoot
    );

    return null;
  }

  const candidates = readdirSync(
    simulationResultsRoot,
    {
      withFileTypes: true
    }
  )
    .filter((entry) => entry.isDirectory())
    .map((entry) => {
      const databasePath = path.join(
        simulationResultsRoot,
        entry.name,
        'database.db'
      );

      if (!existsSync(databasePath)) {
        return null;
      }

      const databaseStats = statSync(databasePath);

      if (
        databaseStats.size === 0
        || !isSqliteDatabase(databasePath)
      ) {
        console.warn(
          'Skipping invalid database:',
          databasePath
        );

        return null;
      }

      return {
        databasePath,
        modifiedAt: databaseStats.mtimeMs
      };
    })
    .filter((candidate) => candidate !== null)
    .sort(
      (left, right) =>
        right.modifiedAt - left.modifiedAt
    );

  if (candidates.length === 0) {
    console.error(
      'No valid SQLite simulation database was found.'
    );

    return null;
  }

  return candidates[0].databasePath;
}

function connectToLatestDatabase() {
  const latestDatabasePath =
    findLatestDatabasePath();

  if (!latestDatabasePath) {
    return false;
  }

  if (
    databaseReady
    && db
    && currentDbPath === latestDatabasePath
  ) {
    return true;
  }

  if (db) {
    db.close((error) => {
      if (error) {
        console.warn(
          'Previous database close warning:',
          error.message
        );
      }
    });

    db = null;
  }

  databaseReady = false;
  currentDbPath = latestDatabasePath;

  predictionsForFeature.clear();
  lastSentData = '';

  db = new sqlite3.Database(
    currentDbPath,
    sqlite3.OPEN_READONLY,
    (error) => {
      if (error) {
        console.error(
          'Database connection error:',
          currentDbPath,
          error.message
        );

        databaseReady = false;
        db = null;
        currentDbPath = null;

        return;
      }

      databaseReady = true;

      console.log(
        'Database opened successfully:',
        currentDbPath
      );
    }
  );

  /*
   * sqlite3 opens the database asynchronously.
   * The next refresh cycle will read its content.
   */
  return false;
}

async function fetchAndSendData() {
  if (!connectToLatestDatabase()) {
    return;
  }

  try {
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

      const lifecycleTableName =
        predictionTableName.replace(
          '_feature_prediction',
          '_feature_lifecycle'
        );

      const lifecycleData =
        await readLifecycleData(
          lifecycleTableName
        );

      const predictionRows = await readRows(`
        SELECT *
        FROM ${quoteIdentifier(predictionTableName)}
        ORDER BY prediction_number;
      `);

      const predictions = [];

      for (const row of predictionRows) {
        try {
          const predictorSettings = parseJson(
            row.predictor_settings,
            {}
          );

          const predictorName =
            predictorSettings.predictor_name
            || predictorSettings.name
            || 'unknown';

          predictions.push({
            feature_name: predictionTableName,

            original_data: parseJson(
              row.original_data,
              []
            ),

            preprocessed_data: parseJson(
              row.preprocessed_data,
              []
            ),

            test_data_beginning: parseJson(
              row.test_data_beginning,
              []
            ),

            test_data_end: parseJson(
              row.test_data_end,
              []
            ),

            prediction_future: parseJson(
              row.prediction_future,
              []
            ),

            prediction_test: parseJson(
              row.prediction_test,
              []
            ),

            error_metrics: parseJson(
              row.error_metrics,
              {}
            ),

            prediction_time: row.prediction_time,
            prediction_number: row.prediction_number,
            predictor_settings: predictorSettings,

            lifecycle:
              lifecycleData[predictorName] || null
          });
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

    predictionsForFeature.clear();

    newPredictionsForFeature.forEach(
      (predictions, featureName) => {
        predictionsForFeature.set(
          featureName,
          predictions
        );
      }
    );

    const serializedData = JSON.stringify(
      Array.from(
        predictionsForFeature.entries()
      )
    );

    if (serializedData !== lastSentData) {
      lastSentData = serializedData;

      const totalPredictions =
        Array.from(
          predictionsForFeature.values()
        ).reduce(
          (total, predictions) =>
            total + predictions.length,
          0
        );

      console.log(
        'Prediction tables found:',
        tableRows.length
      );

      console.log(
        'Predictions read this cycle:',
        totalPredictions
      );

      if (win && !win.isDestroyed()) {
        win.webContents.send(
          'data-updated',
          predictionsForFeature
        );
      }
    }
  } catch (error) {
    sendDatabaseError(error.message);
  }
}

async function readLifecycleData(
  lifecycleTableName
) {
  try {
    const lifecycleRows = await readRows(`
      SELECT predictor_name,
             lifecycle_state,
             is_active,
             latest_metrics,
             updated_at
      FROM ${quoteIdentifier(lifecycleTableName)};
    `);

    return lifecycleRows.reduce(
      (result, row) => {
        result[row.predictor_name] = {
          state: row.lifecycle_state,
          is_active: row.is_active === 1,

          metrics: parseJson(
            row.latest_metrics,
            null
          ),

          updated_at: row.updated_at
        };

        return result;
      },
      {}
    );
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
    if (!db || !databaseReady) {
      reject(
        new Error('Database is not ready.')
      );

      return;
    }

    db.all(
      query,
      [],
      (error, rows) => {
        if (error) {
          reject(error);
          return;
        }

        resolve(rows || []);
      }
    );
  });
}

function parseJson(value, fallbackValue) {
  if (
    value === null
    || value === undefined
    || value === ''
  ) {
    return fallbackValue;
  }

  if (
    typeof value === 'object'
    && !Buffer.isBuffer(value)
  ) {
    return value;
  }

  try {
    return JSON.parse(value);
  } catch (error) {
    return fallbackValue;
  }
}

function quoteIdentifier(identifier) {
  return `"${String(identifier).replace(
    /"/g,
    '""'
  )}"`;
}

function sendDatabaseError(message) {
  console.error(
    'Database query error:',
    message
  );

  if (win && !win.isDestroyed()) {
    win.webContents.send(
      'data-error',
      message
    );
  }
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }

  if (db) {
    db.close((error) => {
      if (error) {
        console.warn(
          'Database close warning:',
          error.message
        );
      }
    });

    db = null;
    databaseReady = false;
    currentDbPath = null;
  }

  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (
    BrowserWindow.getAllWindows().length === 0
  ) {
    createWindow();
  }
});