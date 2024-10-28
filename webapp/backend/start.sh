#!/bin/bash
cd /usr/src/app/util

npm install

node mongo-setup.js

cd ..

node server.js