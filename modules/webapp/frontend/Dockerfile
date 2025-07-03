# Serve the pre-built app with Nginx
FROM nginx:alpine

# Copy the already built Angular app to nginx
COPY ./dist/DISSECT-CF-Fog-WebApp /usr/share/nginx/html

# Expose port 80 to serve the app
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
