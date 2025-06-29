## MongoDB Setup Guide

This guide explains how to set up a MongoDB container and configure the database server, which can be used by DISSECT-CF-Fog-WebApp and the DISSECT-CF-Fog simulation tool.

### Creating the MongoDB container

 - Download Docker: https://www.docker.com/

 - Pull the latest mongodb image using the following command: ```docker pull mongo:latest```

 - Create a container with the name *dissect* using port 27017: ```docker run -d -p 27017:27017 --name dissect mongo:latest```

 - Start/Stop container *dissect* with the following commands: ```docker start/stop dissect```

### Configuring the MongoDB server

 - Create the necessary collections (its content may require further configuration): ```node mongo-setup.js```

   - *users*: contains the users
   
   - *strategies*: contains the predefined device and application strategies that can be used during the configurations and simulations
   
   - *simulator_jobs*: contains simulation jobs that can be processed by the simulator
   
   - *resources*: contains the possible computing resources of the physical nodes that can be used during the configurations and simulations

   - *fs.files*: one of the GridFS tables, registers the files that can be rebuilt from the chunks in the fs.chunks collection

   - *fs.chunks*: the other table used by GridFS, contains the actual contents of the files broken down into chunks
  
   - *configurations*: contains set of simulation jobs submitted by a user, which should be evaluated and visualised together

 - Upload a job manually (its content may require further configuration): ```node job-uploader.js```

 - You might need to resolve mongodb dependency: ```npm install```

### Further useful commands

 - GUI to access the mongoDB running locally: https://www.mongodb.com/products/compass

 - List all containers: ```docker ps -a```

 - Enter the bash of the mongoDB container:  ```docker exec -it dissect bash```

 - Enter the MongoDB shell: ```mongosh```

   - Print a list of all available databases: ```show dbs```
   
   - Set current database: ```use dissect```

     - Print a list of all available collections: ```show collections```

     - List all items of a collection: ```db.users.find()```
 
     - Remove all items from a collection: ```db.simulator_jobs.remove({})```

   - Drop a database: ```db.dropDatabase()```
  

