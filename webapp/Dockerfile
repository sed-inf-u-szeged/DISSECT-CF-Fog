# Use official Node.js LTS image
FROM node:14

# Set the working directory inside the container
WORKDIR /usr/src/app

# Copy package.json and package-lock.json
COPY ./backend/package*.json ./

# Copy util files
COPY ./util/ ./util/

# Install dependencies
RUN npm install

# Copy the rest of the backend code
COPY ./backend .

# Make the script executable
RUN chmod +x /usr/src/app/start.sh

# Expose the backend port (3000)
EXPOSE 3000

# Set the custom entry point to the script
ENTRYPOINT ["/usr/src/app/start.sh"]
