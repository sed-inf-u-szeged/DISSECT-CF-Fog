# Step 1: Build the Angular app
FROM node:14 AS build

# Set working directory
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install Angular dependencies
RUN npm install

# Copy all files to the container
COPY . .

# Build the Angular app in production mode
RUN npm run build --prod

# Step 2: Serve the app with Nginx
FROM nginx:alpine

# Copy the built Angular app from the previous step
COPY --from=build /app/dist/DISSECT-CF-Fog-WebApp /usr/share/nginx/html

# Expose port 80 to serve the app
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
