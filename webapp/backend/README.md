# DISSECT-CF-Fog-WebApp: Server

This server is part of DISSECT-CF-Fog-WebApp. It is written in Node.js with Express.js using Firabase as database and file storage.
It uses the [DISSECT-CF-Fog java application](https://github.com/andrasmarkus/dissect-cf), which generates the configuration's result.

## Setup the server
Basicaly there are the required folders and files for running the server. The basic resource files are provided, and also the DISSET-CF-Fog java application too (from refactor branch with [a49f55e044](https://github.com/andrasmarkus/dissect-cf/commit/a49f55e044e9294ca84d871070a5b3ad1d9de7ce) commit - 26.09.2020) But in few points there are the required settings for the server:

- Create and configure the MongoDB NoSQL database using the **MongoDB Setup Guide.docx** document and the **mongodb-setup.js** script.
- Clone the [dissect-cf-fog](https://github.com/andrasmarkus/dissect-cf) project, configure the microservice (especially the database connection string) and run it.

## Development server

Run `npm run start` for running the server on the 3000 port.
