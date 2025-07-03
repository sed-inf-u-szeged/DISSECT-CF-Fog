# Step 1: Build the Angular app
FROM node:14 AS build

# Set working directory
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install Angular dependencies
RUN npm install

# Copy the already built Angular app
COPY ./dist/DISSECT-CF-Fog-WebApp .

# Step 2: Serve the app with Nginx
FROM nginx:alpine

# Expose port 80 to serve the app
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
