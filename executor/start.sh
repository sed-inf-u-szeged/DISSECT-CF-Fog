#!/bin/bash
echo "Listing files in the current directory:"
ls -la

# Change to the app directory if necessary
cd /usr/src/app

# Print the contents of the directory again
echo "Contents of /usr/src/app:"
ls -la

# Copy the JAR file to the current directory from target
cp /usr/src/app/target/dissect-cf-fog-executor-1.0.0-SNAPSHOT.jar .

# Start your Spring Boot application
java -jar dissect-cf-fog-executor-1.0.0-SNAPSHOT.jar