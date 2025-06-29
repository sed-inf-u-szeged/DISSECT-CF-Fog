const mongodb = require("mongodb");
const fs = require('fs');

/**
 * Create a file reference in the specified collection that points to the
 * original file in the fs.files bucket.
 */
async function createFileReference(client, database, collection, id, filename) {
    const result = await client.db(database).collection(collection).insertOne({
        fileId: new mongodb.ObjectId(id),
        filename: filename
    })

    console.log(result);

    return result;
}

// Upload a file to MongoDB using GridFs
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
 * The function creates the database and its collections,
 * then uploads the files and adds their references to the corresponding collections.
 */
async function main() {

    //Check if created.lock exists, if so then the database is already set up
    if (fs.existsSync('./created.lock')) {
        console.log('Database already set up');
        return;
    }

    const database = 'dissect'
    const uri = `mongodb://mongodb:27017/${database}`;
    const client = new mongodb.MongoClient(uri, { useUnifiedTopology: true });

    try {
        await client.connect();

        // Create collections
        await client.db(database).createCollection('fs.files');
        await client.db(database).createCollection('fs.chunks');
        await client.db(database).createCollection('configurations');
        await client.db(database).createCollection('admin_configurations');
        await client.db(database).createCollection('resources');
        await client.db(database).createCollection('simulator_jobs');
        await client.db(database).createCollection('strategies');
        await client.db(database).createCollection('users');

        // Upload files
        const lpds32 = await uploadFile(client, database, './resources/LPDS_32.xml', 'LPDS_32.xml');
        const lpds16 = await uploadFile(client, database, './resources/LPDS_16.xml', 'LPDS_16.xml');
        const application_strategies = await uploadFile(client, database, './resources/application-strategies.xml', 'application-strategies.xml');
        const device_strategies = await uploadFile(client, database, './resources/device-strategies.xml', 'device-strategies.xml');

        //TODO add admin user

        // Add the references of the uploaded files to the corresponding collections
        await createFileReference(client, database, 'resources', lpds32._id, lpds32.filename);
        await createFileReference(client, database, 'resources', lpds16._id, lpds16.filename);
        await createFileReference(client, database, 'strategies', application_strategies._id, application_strategies.filename);
        await createFileReference(client, database, 'strategies', device_strategies._id, device_strategies.filename);

        // Add default user job configurations
        await client.db(database).collection('admin_configuration').updateOne(
            { "_id": "user_job_configurations" },
            { $set: {
                "defaultResetPeriod": 7,        // 7 days
                "defaultMaxSimulations": 100,   // 100 simulations per the reset period
                "defaultMaxRuntime": 86400      // 24 hours (in seconds)
            }}
        );

    } catch (e) {
        console.error(e);
    } finally {
        await client.close();
    }
    fs.writeFileSync('./created.lock', 'created');
    console.log('Database setup completed');
}

main().catch(console.error);