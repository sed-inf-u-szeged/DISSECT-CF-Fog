const mongodb = require("mongodb");
const stream = require("stream");
const config = require("../config/gen-config");

// Save the user to the database
async function addUser(user) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).insertOne(user);
    } catch (e) {
        console.log('mongodb-service: addUser() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the user with the given ID
async function getUser(userid) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).findOne(userid);
    } catch (e) {
        console.log('mongodb-service: getUser() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's role
async function updateUserRole(userId, role) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    role: role
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserRole() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the list of all users in the database
async function getAllUsers() {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).find();
    } catch (e) {
        console.log('mongodb-service: getAllUsers() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update the default reset period a user can submit a job per day
async function updateDefaultResetPeriod(periodInDays) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).updateOne(
            { _id: new mongodb.ObjectId("user_job_configurations") },
            {
                $set: {
                    defaultResetPeriod: periodInDays
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateDefaultResetPeriod() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update the default job count a user can submit per the reset period
async function updateDefaultMaxSimulations(count) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).updateOne(
            { _id: new mongodb.ObjectId("user_job_configurations") },
            {
                $set: {
                    defaultMaxSimulations: count
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateDefaultMaxSimulations() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update the default maximum runtime a user can submit per the reset period
async function updateDefaultMaxRuntime(runtimeInSeconds) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).updateOne(
            { _id: new mongodb.ObjectId("user_job_configurations") },
            {
                $set: {
                    defaultMaxRuntime: runtimeInSeconds
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateDefaultMaxRuntime() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's reset period
async function updateUserResetPeriod(userId, periodInDays) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    resetPeriod: periodInDays
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserResetPeriod() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's maximum simulations
async function updateUserMaxSimulations(userId, count) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    maxSimulations: count
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserMaxSimulations() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's maximum runtime
async function updateUserMaxRuntime(userId, runtimeInSeconds) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    maxRuntime: runtimeInSeconds
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserMaxRuntime() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's current simulations count
async function updateUserSimulationsCount(userId, count) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    simulationsRun: count
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserSimulationsCount() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Update user's total runtime
async function updateUserTotalRuntime(userId, runtimeInSeconds) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.userCollectionName).updateOne(
            { _id: new mongodb.ObjectId(userId) },
            {
                $set: {
                    totalRuntime: runtimeInSeconds
                }
            }
        );
    } catch (e) {
        console.log('mongodb-service: updateUserTotalRuntime() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the specified strategy file
async function getStrategyFile(filename) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.strategiesCollectionName).findOne(filename);
    } catch (e) {
        console.log('mongodb-service: getStrategyFile() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the specified resource file
async function getResourceFiles(){
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.resourceCollectionName).find().toArray();
    } catch (e) {
        console.log('mongodb-service: getResourceFiles() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Save the given job to the database
async function addJob(job) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    job.user = new mongodb.ObjectId(job.user);
    try {
        return await client.db(config.databaseName).collection(config.simulationCollectionName).insertOne(job);
    } catch (e) {
        console.log('mongodb-service: addJob() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Save the configuration to the database
async function addConfiguration(configuration) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    configuration.user = new mongodb.ObjectId(configuration.user);
    try {
        return await client.db(config.databaseName).collection(config.configurationCollectionName).insertOne(configuration);
    } catch (e) {
        console.log('mongodb-service: addConfiguration() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Adds the admin configuration to the database
async function addAdminConfiguration(configuration) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    configuration.user = new mongodb.ObjectId(configuration.user);
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).insertOne(configuration);
    } catch (e) {
        console.log('mongodb-service: addConfiguration() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// returns the admin configurations from the database
async function getAdminConfigurations() {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).find().toArray();
    } catch (e) {
        console.log('mongodb-service: getAdminConfigurations() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Gets one specific admin configuration from the database based on it's id
async function getAdminConfigurationById(id) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationAdminCollectionName).findOne(new mongodb.ObjectId(id))
    } catch (e) {
        console.log('mongodb-service: getAdminConfigurations() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Gets custom simulations from the database and only returns specific rows of it
async function getCustomSimulations(adminConfigId){
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        const query = {
            $and: [
                { adminConfigId: adminConfigId },
                { $or: [
                    { 'deviceCode': { $exists: true } },
                    { 'applicationCode': { $exists: true } }
                ]}
            ]
        };

        const projection = {
            nickname: 1,
            createdDate: 1,
            'simulatorJobResult.architecture.totalEnergyConsumptionOfNodesInWatt': 1,
            'simulatorJobResult.architecture.totalEnergyConsumptionOfDevicesInWatt': 1,
            'simulatorJobResult.cost': 1
        };

    return await client.db(config.databaseName)
    .collection(config.simulationCollectionName)
    .find(query)
    .project(projection)
    .toArray();
    } catch (error) {
        console.log('mongodb-service: getCustomSimulations() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the simulation of the given ID
async function getSimulationById(id){
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        const job = await client.db(config.databaseName).collection(config.simulationCollectionName).findOne(new mongodb.ObjectId(id));
        for (const property in job.results) {
            job.results[property] = await getFileById(job.results[property]).then(res => {
                return "'" + res + "'";
            });
        }
        return job;
    } catch (e) {
        console.log('mongodb-service: getSimulationById() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the configuration of the given ID
async function getConfigurationById(id) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        const configuration = await client.db(config.databaseName).collection(config.configurationCollectionName).findOne(new mongodb.ObjectId(id)).then(res => {
            return res;
        });
        let simulations = [];
        for (const jobId of configuration.jobs) {
            await getSimulationById(jobId).then(
                res => simulations.push(res));
        }
        configuration.jobs = simulations;
        return configuration;
    } catch (e) {
        console.log('mongodb-service: getConfigurationById() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the list of the configurations for the given user
async function getConfigurationsByUserId(id) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    try {
        return await client.db(config.databaseName).collection(config.configurationCollectionName).find({
            user: new mongodb.ObjectId(id.toString())
        }).toArray();
    } catch (e) {
        console.log('mongodb-service: getConfigurationsByUserId() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Return the file of the given id
async function getFileById(id){
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    const stream = new mongodb.GridFSBucket(client.db(config.databaseName)).openDownloadStream(new mongodb.ObjectId(id));
    try {
        stream.read();
        return await new Promise((resolve, reject) => {
            const chunks = [];
            stream.on('data', data => {
                chunks.push(data);
            });
            stream.on('end', () => {
                const data = Buffer.concat(chunks);
                resolve(data);
            });
            stream.on('error', err => {
                reject(err);
            });
        });
    } catch (e){
        console.log('mongodb-service: getFileById() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

// Save the given file with the specified name
async function saveFile(name, data) {
    const client = await mongodb.MongoClient(config.connectionString, { useUnifiedTopology: true }).connect();
    const bucket = new mongodb.GridFSBucket(client.db(config.databaseName));
    try {
        const s = new stream.Readable();
        s.push(data);
        s.push(null);
        const res = await new Promise((resolve, reject) => {
            s.pipe(bucket.openUploadStream(name))
                .on('error', function (err) {
                    reject(err);
                })
                .on('finish', function (file) {
                    resolve(file);
                });
        })
        return res._id;
    } catch (e) {
        console.log('mongodb-service: saveFile() error:' + e.message);
        throw e;
    } finally {
        await client.close();
    }
}

//Export the functions, so will be available for import in other files.
module.exports = {
    addUser,
    getUser,
    getAllUsers,
    getResourceFiles,
    getStrategyFile,
    addJob,
    addConfiguration,
    addAdminConfiguration,
    getAdminConfigurations,
    getAdminConfigurationById,
    getCustomSimulations,
    getSimulationById,
    getConfigurationById,
    getConfigurationsByUserId,
    getFileById,
    saveFile
}