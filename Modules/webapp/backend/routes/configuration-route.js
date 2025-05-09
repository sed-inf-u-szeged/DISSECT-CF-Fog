const express = require('express');
const authJwt = require("..//middleware/auth-jwt");
const router = express.Router({caseSensitive:true});
const { isEmpty } = require('lodash');
const Parser = require("fast-xml-parser").j2xParser;
const xmlParserOptions = require('../config/xml-parser-options');
const mongodb = require('../services/mongodb-service');
const fs = require('fs');
const path = require('path');

/**
 * It parses the different config files (appliances, devices, instances) for the simulations to XML ones and saves them to MongoDB.
 * Then a configuration will be created in the MongoDB (among others) from the objects containing the IDs of the saved config files (for each simulation).
 * The newly created configuration will be returned with all of its simulations as an answer to the request.
 */
router.post('/', [authJwt.verifyToken], async (req, res) => {
  const jobs = [];
  const configs = [];
 

  for (let config of req.body) {
    configs.push(config.configuration);
  }

  // Creates the simulator jobs one by one and adds their id to the jobs array
  for (let config of configs) {
    if (!checkConfigurationRequestBody(config)) {
      throw new Error('Bad request!');
    }

    let obj = await saveResourceFiles(config);

    let configFiles = {};
    configFiles["APPLIANCES_FILE"] = obj.appliancesId;
    configFiles["DEVICES_FILE"] = obj.devicesId;
    configFiles["INSTANCES_FILE"] = obj.instancesId;

    const resources = await mongodb.getResourceFiles();

    let counter = 0;
    for (const item of resources) {
      configFiles["IAAS_FILE" + counter] = item.fileId;
      counter += 1;
    }

    const job = await mongodb.addJob({
      user: req.userId,
      simulatorJobStatus: "SUBMITTED",
      configFiles: configFiles,
      createdDate: new Date().toISOString()
    })

    jobs.push(job.insertedId);
  }

  const new_config_id = await mongodb.addConfiguration({
    user: req.userId,
    time: new Date().toISOString(),
    jobs: jobs
  }).then(res => {
    return res.insertedId;
  });

  const config = await mongodb.getConfigurationById(new_config_id);

  return res.status(201).json({config: config, err: null});
});

/**
 * gets the admin configuration xml file ids and the users own algorithm code from the frontend
 * It builds a job object with the additional data what the java simulator needs
 * 
 */
router.post('/ownAlgorithmConfiguration', [authJwt.verifyToken], async (req, res) => {
  console.log(req.body.code)
  let configFiles = {};
  configFiles["APPLIANCES_FILE"] = req.body.ApplicationId;
  configFiles["DEVICES_FILE"] = req.body.DevicesId;
  configFiles["INSTANCES_FILE"] = req.body.InstancesId;

  const resources = await mongodb.getResourceFiles();

  let counter = 0;
  for (const item of resources) {
    configFiles["IAAS_FILE" + counter] = item.fileId;
    counter += 1;
  }

    const job = await mongodb.addJob({
      user: req.userId,
      simulatorJobStatus: "SUBMITTED",
      configFiles: configFiles,
      createdDate: new Date().toISOString(),
      deviceCode: req.body.deviceCode,
      isDeviceCodeCustom: req.body.isDeviceCodeCustom,
      applicationCode: req.body.applicationCode,
      isApplicationCodeCustom: req.body.isApplicationCodeCustom,
      adminConfigId: req.body.adminConfigId,
      nickname: req.body.nickname
  })

  const jobs = [];
  jobs.push(job.insertedId)

  await mongodb.addConfiguration({
    user: req.userId,
    time: new Date().toISOString(),
    jobs: jobs
  }).then(res => {
    return res.insertedId;
  });
  
  console.log(configFiles);
  return res.status(201)
})
/**
 * Sends every row of the admincofigurations collection from the database to the frontend
 */
router.get('/getAdminConfigurations', [authJwt.verifyToken], async (req, res) => {
  try {
    const configurations = await mongodb.getAdminConfigurations();
    return res.status(201).json(configurations);
  } catch (error) {
    return res.status(500).json({err: error.message});
  }
  
})

/**
 * Sends a specific adminconfiguration for the frontend by id
 */
router.get('/getAdminConfigurations/:id', [authJwt.verifyToken], async (req, res) => {
  try {
    const configuration = await mongodb.getAdminConfigurationById(req.params.id);
    return res.status(201).json(configuration);
  } catch (error) {
    return res.status(500).json({err: error.message});
  }
  
})

/**
 * Sends a specific custom simulation for the frontend by id
 */
router.get('/getCustomSimulations/:id', [authJwt.verifyToken], async (req, res) => {
  try {
    const customSimulations = await mongodb.getCustomSimulations(req.params.id);
    return res.status(201).json(customSimulations);
  } catch (error) {
    return res.status(500).json({err: error.message});
  }
  
})

/**
 * Sends the configuration made by the admin to the database
 */
router.post('/adminConfiguration', [authJwt.verifyToken], async (req, res) =>{

  const appliancesId = await mongodb.saveFile('appliances.xml',req.body.configs[0]);
  const devicesId = await mongodb.saveFile('devices.xml', req.body.configs[1]);
  const instancesId = await mongodb.saveFile('Instances.xml', req.body.configs[2]);

  let configFiles = {};
  configFiles["APPLIANCES_FILE"] = appliancesId;
  configFiles["DEVICES_FILE"] = devicesId;
  configFiles["INSTANCES_FILE"] = instancesId;

  await mongodb.addAdminConfiguration({
    user: req.userId,
    time: new Date().toISOString(),
    shortDescription: req.body.shortDescription,
    configFiles: configFiles
  })
  try {
    res.status(201)
  } catch (error) {
    res.status(500).json({ error: error.message }); // Mentési hiba esetén hibaüzenetet küld vissza
  }
})


/**
 * Parses the retrieved config files to XMLs and saves them into the MongoDB,
 * then returns an object containing the IDs of the newly created files.
 */
async function saveResourceFiles(config) {
  const parser = new Parser(xmlParserOptions.getParserOptions('$'));

  const plainAppliances = config.appliances;
  const plainDevices = config.devices;
  const plainInstances = config.instances;


  const appliances = parser.parse(plainAppliances);
  const devices = parser.parse(plainDevices);
  const instances = parser.parse(plainInstances);

  const xmlFileHeader = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n';

  console.log(appliances);
  console.log(devices);
  console.log(instances);

  const appliancesId = await mongodb.saveFile('appliances.xml',xmlFileHeader + appliances);
  const devicesId = await mongodb.saveFile('devices.xml', xmlFileHeader + devices);
  const instancesId = await mongodb.saveFile('Instances.xml', xmlFileHeader + instances);

  return {
    appliancesId: appliancesId,
    devicesId: devicesId,
    instancesId: instancesId
  }
}

// Checks whether the body of the configuration request meets the requirements or not
function checkConfigurationRequestBody(req){
  return req.appliances && req.devices && !isEmpty(req.appliances) && !isEmpty(req.devices)
}
module.exports = router
