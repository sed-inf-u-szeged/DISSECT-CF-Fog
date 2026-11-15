'use strict';
const {parentPort,workerData}=require('node:worker_threads');
try { parentPort.postMessage({run:require('./mobility-results').readMobilityRun(workerData)}); }
catch(error) { parentPort.postMessage({error:error.message}); }
