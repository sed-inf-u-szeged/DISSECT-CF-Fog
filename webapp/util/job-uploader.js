const mongodb = require("mongodb");
const fs = require('fs');

//  Upload a file to MongoDB using GridFs
async function uploadFile(client, database, path, name) {
    const bucket = new mongodb.GridFSBucket(client.db(database));

    const result = await new Promise((resolve, reject) => {
        fs.createReadStream(path)
            .pipe(bucket.openUploadStream(name))
            .on('error', function (err) { reject(err); })
            .on('finish', function (file) { resolve(file); });
    });

    console.log(result);

    return result;
}

/**
 * The function first uploads the necessery XML files, 
 * then create the simulation job. Please note that the ObjectIDs should
 * be manually added from the mongoDB. 
 */
async function main() {

    const database = 'dissect';
    const uri = `mongodb://localhost:27017/${database}`;
    const client = new mongodb.MongoClient(uri, { useUnifiedTopology: true });

    try {
        await client.connect();

        // Upload the necessary XML files 
        const instances = await uploadFile(client, database, './resources/instances.xml', 'instances.xml');
        const applications = await uploadFile(client, database, './resources/applications.xml', 'applications.xml');
        const devices = await uploadFile(client, database, './resources/devices.xml', 'devices.xml');

        // Create the simulation job
        const createdDate = new Date().toISOString();
        const result = await client.db("dissect").collection("simulator_jobs").insertOne({
            user: "null",
            simulatorJobStatus: "SUBMITTED",
            configFiles: {
                APPLIANCES_FILE: new mongodb.ObjectId(applications._id),
                DEVICES_FILE: new mongodb.ObjectId(devices._id),
                INSTANCES_FILE: new mongodb.ObjectId(instances._id),
                IAAS_FILE0: new mongodb.ObjectId("64945d787f86027186921749"), 
                IAAS_FILE1: new mongodb.ObjectId("64945d787f8602718692174b"),
            },

            createdDate: createdDate,
            lastModifiedDate: createdDate,
        })
        console.log(result.insertedId);

    } catch (e) {
        console.error(e);
    } finally {
        await client.close();
    }
}

main().catch(console.error);