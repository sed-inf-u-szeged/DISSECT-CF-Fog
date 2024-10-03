const authJwt = require("../middleware/auth-jwt");
const express = require('express');
const router = express.Router({ caseSensitive: true });
const parser = require('fast-xml-parser');
const xmlParserOptions = require('../config/xml-parser-options');
const mongodb = require('../services/mongodb-service');

/**
 * It sends the 'Application-strategies.xml' file which contains the list of the currently available app strategies.
 */
router.get("/strategies/application", [authJwt.verifyToken], async (req, res) => {
  const file = await mongodb.getStrategyFile({
    filename: 'application-strategies.xml'
  })

  const fileContent = await mongodb.getFileById(file.fileId).then(file => {
    return file.toString();
  });

  const jsonObj = parser.parse(fileContent.toString(), xmlParserOptions.getParserOptions());

  const result = jsonObj.strategies.strategy instanceof Array ?
      jsonObj.strategies : { strategy: [jsonObj.strategies.strategy] };
  res.status(200).json(result);
});

/**
 * It sends the 'Device-strategies.xml' file which contains the list of the currently available device strategies.
 */
router.get("/strategies/device", [authJwt.verifyToken], async (req, res) => {

  const file = await mongodb.getStrategyFile({
    filename: 'device-strategies.xml'
  })

  const fileContent = await mongodb.getFileById(file.fileId).then(file => {
    return file.toString();
  });

  const jsonObj = parser.parse(fileContent.toString(), xmlParserOptions.getParserOptions());

  const result = jsonObj.strategies.strategy instanceof Array ?
      jsonObj.strategies : { strategy: [jsonObj.strategies.strategy] };
  res.status(200).json(result);
});

module.exports = router;

/**
 * It sends the currently available instances that is defined in the previously saved Instances.xml file in the MongoDB.
 */
router.get("/resources", [authJwt.verifyToken], async (req, res) => {
  const data = []

  const resourceFilesList = await mongodb.getResourceFiles();

  let contentsOfResourcesFiles = [];

  for (const item of resourceFilesList) {
    let fileContent = await mongodb.getFileById(item.fileId);
    contentsOfResourcesFiles.push(fileContent);
  }

  let i = 0;

  contentsOfResourcesFiles.forEach(content => {
    const jsonObj = parser.parse(content.toString(), xmlParserOptions.getParserOptions());
    const resource = {
      name: resourceFilesList[i].filename.replace(".xml", ""),
      machines: getResponseMachines(jsonObj),
      repositories: getResponseRepositories(jsonObj)
    };
    data.push(resource);
    i++;
  });

  return res.status(200).json(data);
});



function getResponseRepositories(jsonObj) {
  const result = [];
  if (jsonObj.cloud.repository instanceof Array) {
    jsonObj.cloud.repository.forEach(repo =>
        result.push({
          id: repo.id,
          capacity: repo.capacity,
          inBW: repo.inBW,
          outBW: repo.outBW,
          diskBW: repo.diskBW
        })
    );
  } else {
    const repo = jsonObj.cloud.repository;

    result.push({
      id: repo.id,
      capacity: repo.capacity,
      inBW: repo.inBW,
      outBW: repo.outBW,
      diskBW: repo.diskBW
    })
  }
  return result;
}

function getResponseMachines(jsonObj) {
  const result = [];
  if (jsonObj.cloud.machine instanceof Array) {
    jsonObj.cloud.machine.forEach(mach =>
        result.push({
          id: mach.id,
          cores: mach.cores,
          processing: mach.processing,
          memory: mach.memory
        })
    );
  } else {
    const mach = jsonObj.cloud.machine;

    result.push({
      id: mach.id,
      cores: mach.cores,
      processing: mach.processing,
      memory: mach.memory
    })
  }
  return result;
}