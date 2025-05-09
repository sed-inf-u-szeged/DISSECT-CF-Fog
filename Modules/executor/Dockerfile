FROM openjdk:11-jre-slim

# Set the working directory inside the container
WORKDIR /usr/src/app

# Copy your application files
COPY target/dissect-cf-fog-executor-1.0.0-SNAPSHOT.jar /usr/src/app/dissect-cf-fog-executor-1.0.0-SNAPSHOT.jar
# Copy the start script into the container
COPY start.sh /usr/src/app/start.sh

# Make the script executable
RUN chmod +x /usr/src/app/start.sh

# Set the custom entry point to the script
ENTRYPOINT ["/usr/src/app/start.sh"]

# Expose the application port
EXPOSE 8080
