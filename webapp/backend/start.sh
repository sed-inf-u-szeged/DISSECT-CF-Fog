#!/bin/bash
cd /usr/src/app/util

npm install

node mongo-setup.js
node job-uploader.js

cd ..

node server.js