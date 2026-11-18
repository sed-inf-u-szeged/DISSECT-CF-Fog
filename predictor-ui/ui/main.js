const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const {
  existsSync,
  readdirSync,
  statSync,
  openSync,
  readSync,
  closeSync,
  readFileSync
} = require('fs');
const path = require('path');
let mobilityRunDirectory = process.env.DISSECT_MOBILITY_RUN_DIR?.trim();
const sqlite3 = mobilityRunDirectory || process.env.DISSECT_MOBILITY_CONFIGURE === '1' ? null : require('sqlite3').verbose();
const { readMobilityRun } = require('./mobility-results');
const { MobilityJob } = require('./mobility-job');
const { Worker } = require('node:worker_threads');
const configureMobility = process.env.DISSECT_MOBILITY_CONFIGURE === '1';

let win;
let db = null;
let dbPath = null;
let refreshTimer = null;
let refreshInProgress = false;
let lastSentData = '';
let closeAfterMobilityRun = false;
const send = (channel, value) => { if (win && !win.isDestroyed()) win.webContents.send(channel, value); };
const mobilityJob = new MobilityJob({
  root: path.resolve(__dirname, '..', '..'),
  onStatus: value => {
    send('mobility-job-updated', value);
    if (!value.active && closeAfterMobilityRun && win && !win.isDestroyed()) win.close();
  },
  onLive: value => send('mobility-live-updated', value),
  onComplete: (directory, stillCurrent, verifyNodeLoad) => new Promise((resolve, reject) => {
    const worker = new Worker(path.join(__dirname, 'mobility-result-worker.js'), {workerData: directory});
    let delivered = false;
    worker.once('message', message => {
      delivered = true;
      if (message.error) { reject(new Error(message.error)); return; }
      try { verifyNodeLoad(message.run.nodeLoad); } catch (error) { reject(error); return; }
      if (stillCurrent()) { mobilityRunDirectory = directory; send('mobility-updated', message.run); }
      resolve();
    });
    worker.once('error', reject);
    worker.once('exit', code => { if (!delivered) reject(new Error('Result reader exited: ' + code)); });
  })
});
function trusted(event) { return win && event.sender === win.webContents; }
ipcMain.handle('get-mobility-job', event => {
  if (!trusted(event)) throw new Error('Unknown UI');
  return {...mobilityJob.snapshot(), configure: configureMobility && !mobilityRunDirectory};
});
ipcMain.handle('start-mobility-job', (event, request) => {
  if (!trusted(event)) throw new Error('Unknown UI');
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
  return mobilityJob.start(request);
});
ipcMain.handle('cancel-mobility-job', event => trusted(event) && mobilityJob.cancel());

const predictionsForFeature = new Map();
const simulationResultsPath = path.join(
  __dirname, '..', '..', 'simulator', 'sim_res'
);
const configuredDatabasePath =
  process.env.DISSECT_DATABASE_PATH?.trim();
const selectedDatabasePath = configuredDatabasePath
  ? path.resolve(configuredDatabasePath)
  : null;

ipcMain.handle('open-mobility-run', async (event) => {
  if (!win || event.sender !== win.webContents) return false;
  if (mobilityJob.active) throw new Error('Finish or cancel the active mobility run first');
  const selection = await dialog.showOpenDialog(win, {
    title: 'Open a completed mobility run',
    defaultPath: mobilityRunDirectory || simulationResultsPath,
    properties: ['openDirectory']
  });
  if (selection.canceled || selection.filePaths.length !== 1) return false;
  try {
    const directory = selection.filePaths[0];
    const run = readMobilityRun(directory);
    mobilityRunDirectory = directory;
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
    win.webContents.send('mobility-updated', run);
    return true;
  } catch (error) {
    win.webContents.send('data-error', 'Mobility: ' + error.message);
    return false;
  }
});

const createWindow = () => {
  closeAfterMobilityRun = false;
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
    path.join(__dirname, 'dist/dissect-cf-predictor-ui/index.html')
  );

  win.webContents.once('did-finish-load', () => {
    lastSentData = '';
    if (mobilityJob.active) return;
    if (mobilityRunDirectory) {
      try {
        win.webContents.send('mobility-updated', readMobilityRun(mobilityRunDirectory));
      } catch (error) {
        win.webContents.send('data-error', 'Mobility: ' + error.message);
      }
      return;
    }
    if (configureMobility) return;
    fetchAndSendData();
    if (refreshTimer) {
      clearInterval(refreshTimer);
    }
    refreshTimer = setInterval(fetchAndSendData, 5000);
  });

  win.on('closed', () => {
    win = null;
    if (refreshTimer) {
      clearInterval(refreshTimer);
      refreshTimer = null;
    }
  });
  let closePrompt = false;
  win.on('close', event => {
    if (!mobilityJob.active) return;
    event.preventDefault();
    if (closeAfterMobilityRun || closePrompt) return;
    closePrompt = true;
    dialog.showMessageBox(win, {type:'question',buttons:['Keep running','Stop and close'],defaultId:0,cancelId:0,
      message:'A mobility run is still active.',detail:'Stop this run before closing? Its partial output and logs will be kept.'})
      .then(answer => {
        if (answer.response === 1) {
          closeAfterMobilityRun = true;
          mobilityJob.cancel();
          if (!mobilityJob.active && win) win.close();
        }
      }).catch(error => send('data-error', error.message)).finally(() => { closePrompt = false; });
  });
};

function isSqliteDatabase(databasePath) {
  let descriptor = null;
  try {
    const stats = statSync(databasePath);
    if (!stats.isFile() || stats.size < 16) {
      return false;
    }
    const header = Buffer.alloc(16);
    descriptor = openSync(databasePath, 'r');
    return readSync(descriptor, header, 0, 16, 0) === 16
      && header.toString('ascii') === 'SQLite format 3\u0000';
  } catch (error) {
    return false;
  } finally {
    if (descriptor !== null) {
      closeSync(descriptor);
    }
  }
}

function findLatestDatabasePath() {
  if (!existsSync(simulationResultsPath)) {
    return null;
  }

  const candidates = readdirSync(
    simulationResultsPath, { withFileTypes: true }
  )
    .filter((entry) => entry.isDirectory())
    .map((entry) =>
      path.join(simulationResultsPath, entry.name, 'database.db')
    )
    .filter(isSqliteDatabase)
    .map((databasePath) => ({
      databasePath,
      modifiedAt: statSync(databasePath).mtimeMs
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt);

  return candidates[0]?.databasePath || null;
}

function getDatabasePath() {
  if (selectedDatabasePath) {
    if (!isSqliteDatabase(selectedDatabasePath)) {
      throw new Error(
        `Selected database is missing or invalid: ${selectedDatabasePath}`
      );
    }
    return selectedDatabasePath;
  }
  return findLatestDatabasePath();
}

function formatSetting(value, suffix = '') {
  return typeof value === 'number' && Number.isFinite(value)
    ? `${value}${suffix}`
    : 'Not recorded';
}

function formatErrorLimit(value) {
  return value === Number.MAX_VALUE
    ? 'No practical limit'
    : formatSetting(value);
}

function readRunInfo(databasePath) {
  const directory = path.dirname(databasePath);
  const info = {
    id: path.basename(directory),
    profile: 'Not recorded',
    settings_file: null,
    note: '',
    tolerance: null,
    thresholds: []
  };

  try {
    const files = readdirSync(directory, { withFileTypes: true })
      .filter((entry) =>
        entry.isFile()
        && /^simulation_settings.*\.json$/i.test(entry.name)
      )
      .map((entry) => entry.name);

    if (files.length !== 1) {
      info.note = files.length === 0
        ? 'Settings file not available yet.'
        : 'Multiple settings files found; configuration is not selected.';
      return info;
    }

    info.settings_file = files[0];
    const settings = JSON.parse(
      readFileSync(path.join(directory, files[0]), 'utf8')
        .replace(/^\uFEFF/, '')
    );

    const lifecycle = settings.lifecycleSettings;
    const profile = settings.lifecycleProfile;

    if (typeof profile === 'string' && profile.trim()) {
      info.profile = profile;
    }

    if (!lifecycle || typeof lifecycle !== 'object') {
      info.note = 'Lifecycle settings were not recorded.';
      return info;
    }

    const relative = lifecycle.accuracyTolerance;
    const inheritedAbsolute = lifecycle.absoluteTolerance == null;
    const absolute = inheritedAbsolute
      ? relative
      : lifecycle.absoluteTolerance;

    if (typeof relative === 'number' && Number.isFinite(relative) && relative >= 0
        && typeof absolute === 'number' && Number.isFinite(absolute) && absolute >= 0) {
      info.tolerance = { relative, absolute };
    }

    const relativeText =
      typeof relative === 'number' && Number.isFinite(relative)
        ? `${Number((relative * 100).toFixed(8))}%`
        : 'Not recorded';

    info.thresholds = [
      {
        label: 'Lifecycle checks',
        value: typeof lifecycle.enabled === 'boolean'
          ? (lifecycle.enabled ? 'Enabled' : 'Disabled')
          : 'Not recorded'
      },
      {
        label: 'Trusted baseline',
        value: lifecycle.baselinePredictorName || 'Not recorded'
      },
      {
        label: 'Required samples',
        value: formatSetting(lifecycle.minimumMatchedSamples)
      },
      {
        label: 'Relative tolerance',
        value: relativeText
      },
      {
        label: 'Absolute tolerance',
        value: formatSetting(absolute)
      },
      {
        label: 'Minimum accuracy',
        value: formatSetting(lifecycle.minimumAccuracy, '%')
      },
      {
        label: 'Maximum MAE',
        value: formatErrorLimit(lifecycle.maximumMae)
      },
      {
        label: 'Maximum MSE',
        value: formatErrorLimit(lifecycle.maximumMse)
      },
      {
        label: 'Maximum RMSE',
        value: formatErrorLimit(lifecycle.maximumRmse)
      },
      {
        label: 'Maximum SMAPE',
        value: formatSetting(lifecycle.maximumSmape, '%')
      }
    ];

    const notes = [];
    if (inheritedAbsolute) {
      notes.push('Absolute tolerance uses the legacy fallback rule.');
    }
    if (lifecycle.enabled === true && lifecycle.minimumAccuracy === 0) {
      notes.push('Minimum accuracy is 0%; activation does not require a positive hit rate.');
    }
    info.note = notes.join(' ');
  } catch (error) {
    info.note = 'Settings could not be read.';
    console.warn('Run settings warning:', error.message);
  }

  return info;
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

async function connectToDatabase() {
  const target = getDatabasePath();
  if (!target) {
    return false;
  }
  if (db && dbPath === target) {
    return true;
  }

  await closeDatabase(db);
  db = null;
  dbPath = null;
  predictionsForFeature.clear();
  lastSentData = '';

  db = await openDatabase(target);
  dbPath = target;

  console.log(
    'Database selection:', selectedDatabasePath ? 'fixed' : 'latest'
  );
  console.log('Database opened successfully:', dbPath);
  return true;
}

async function fetchAndSendData() {
  if (mobilityRunDirectory || configureMobility || mobilityJob.active) return;
  if (refreshInProgress) {
    return;
  }
  refreshInProgress = true;

  try {
    if (!await connectToDatabase()) {
      return;
    }

    const runInfo = readRunInfo(dbPath);
    const tables = await readRows(`
      SELECT name FROM sqlite_master
      WHERE type = 'table' AND name LIKE '%_feature_prediction'
      ORDER BY name;
    `);
    const newPredictions = new Map();

    for (const table of tables) {
      const lifecycleTable = table.name.replace(
        '_feature_prediction', '_feature_lifecycle'
      );
      const lifecycleData = await readLifecycleData(lifecycleTable);
      const rows = await readRows(`
        SELECT * FROM ${quoteIdentifier(table.name)}
        ORDER BY prediction_number;
      `);
      const predictions = [];

      for (const row of rows) {
        try {
          const settings = JSON.parse(row.predictor_settings);
          const name = settings.predictor_name || settings.name || 'unknown';

          predictions.push({
            feature_name: table.name,
            original_data: JSON.parse(row.original_data),
            preprocessed_data: JSON.parse(row.preprocessed_data),
            test_data_beginning: JSON.parse(row.test_data_beginning),
            test_data_end: JSON.parse(row.test_data_end),
            prediction_future: JSON.parse(row.prediction_future),
            prediction_test: JSON.parse(row.prediction_test),
            error_metrics: JSON.parse(row.error_metrics),
            prediction_time: JSON.parse(row.prediction_time),
            prediction_number: JSON.parse(row.prediction_number),
            predictor_settings: settings,
            lifecycle: lifecycleData[name] || null,
            run_info: runInfo
          });
        } catch (error) {
          console.warn(
            'Skipping invalid prediction row:',
            table.name, row.prediction_number, error.message
          );
        }
      }

      newPredictions.set(table.name, predictions);
    }

    const serialized = JSON.stringify(Array.from(newPredictions.entries()));
    if (serialized !== lastSentData) {
      predictionsForFeature.clear();
      newPredictions.forEach((predictions, name) => {
        predictionsForFeature.set(name, predictions);
      });
      lastSentData = serialized;

      if (win && !win.isDestroyed()) {
        if (!mobilityRunDirectory) win.webContents.send('data-updated', predictionsForFeature);
      }
    }
  } catch (error) {
    console.error('Database query error:', error.message);
    if (win && !win.isDestroyed()) {
      if (!mobilityRunDirectory) win.webContents.send('data-error', error.message);
    }
  } finally {
    refreshInProgress = false;
  }
}

async function readLifecycleData(tableName) {
  try {
    const rows = await readRows(`
      SELECT predictor_name, lifecycle_state, is_active,
             latest_metrics, updated_at
      FROM ${quoteIdentifier(tableName)};
    `);

    return rows.reduce((result, row) => {
      result[row.predictor_name] = {
        state: row.lifecycle_state,
        is_active: row.is_active === 1,
        metrics: row.latest_metrics ? JSON.parse(row.latest_metrics) : null,
        updated_at: row.updated_at
      };
      return result;
    }, {});
  } catch (error) {
    if (error.message && error.message.includes('no such table')) {
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
