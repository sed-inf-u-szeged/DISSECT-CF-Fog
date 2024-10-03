const authJwt = require("../middleware/auth-jwt");
const express = require('express');
const router = express.Router({ caseSensitive: true });
const { isEmpty } = require('lodash');
const mongodb = require('../services/mongodb-service');
const {response} = require("express");

/**
 * Sends back all the list of all users
 * Sends all the users from the database with the given response.
 * If comes some error, 500 will be thrown with a message property.
 */
router.get("/", [authJwt.verifyToken], async (req, res) => {

  try {
    const users = await mongodb.getAllUsers();
    res.status(200).send({users: users})
  } catch (e) {
    res.status(500).send({message: e.message});
  }
});

/**
 * Sends back the list of the configurations of the given user.
 */
router.post("/configuration/list", [authJwt.verifyToken], async (req, res, next) => {
  if (isEmpty(req.body.id)) {
    return res.status(404).send({message: 'Bad request!'})
  }

  await await mongodb.getConfigurationsByUserId(req.body.id).then(details => {
    return res.status(200).json(details);
  })
});

// it returns the configuration results with the given id
router.post("/configuration", [authJwt.verifyToken], (req, res, next) => {
  return sendConfig(req,res);
});

// returns the XML file containing the applications' settings used by the simulator
router.post("/configurations/download/appliances", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});

// returns the XML file containing the devices' settings used by the simulator
router.post("/configurations/download/devices", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});

// returns the XML file containing the instances' settings used by the simulator
router.post("/configurations/download/instances", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});

// returns the HTML file visualising the task scheduling of the simulator for the given job
router.post("/configurations/download/timeline", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});

// returns the HTML file visualising the energy consumptions of the devices for the given job
router.post("/configurations/download/devicesenergy", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});

// returns the HTML file visualising the energy consumptions of the fog and cloud nodes for the given job
router.post("/configurations/download/nodesenergy", [authJwt.verifyToken], (req, res, next) => {
  return sendFileMongo(req,res);
});


/**
 * Send back the given file as a string
 * @param req The received request
 * @param res The returned response
 * @return {Promise<void>}
 */
async function sendFileMongo(req, res) {
  if (isEmpty(req.body._id)) {
    return res.status(404).send({ message: 'Bad request!' })
  }
  await mongodb.getFileById(req.body._id).then(file => {
    console.log(response);
    return res.send(file.toString());
  })
}

/**
 * Send back the config of the requested id
 * @param req The received request
 * @param res The returned response
 * @return {Promise<*>}
 */
async function sendConfig(req,res){
  const config = await mongodb.getConfigurationById(req.body._id);

  const jobs = [];
  for (const job of config.jobs) {
    jobs.push(await mongodb.getSimulationById(job._id));
  }

  config.jobs = jobs;

  return res.status(201).json({config: config, err: null});
}

module.exports = router;