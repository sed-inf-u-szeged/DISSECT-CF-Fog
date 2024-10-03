const express = require('express');
const morgan = require('morgan'); // HTTP request logger middleware 
const bodyParser = require('body-parser');
const http = require('http');

// routes
const configRoute = require('./routes/configuration-route');
const authorizationRoute = require('./routes/auth-route');
const userRoute = require('./routes/user-route');
const propertiesRoute = require('./routes/properties-route');

const app = express();
app.use(morgan('dev'));
app.use(bodyParser.urlencoded({ extended: false })); // true precises that the req.body object will contain values of any type instead of just strings.
app.use(bodyParser.json());
app.use(bodyParser.json({ limit: '200kb' })); // request body size limit set to 200kb

// Handle the CORS problems
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, X-Access-Token, Content-Type, Accept, Authorization');
  if (req.method === 'OPTIONS') {
    res.header('Access-Control-Allow-Methods', 'PUT, POST, PATCH, DELETE, GET');
    return res.status(200).json({});
  }
  next();
});

// Add the API routes
app.use('/auth', authorizationRoute);
app.use('/user', userRoute);
app.use('/configuration', configRoute);
app.use('/properties', propertiesRoute);

// Error message when the response not found
app.use((req, res, next) => {
  const error = new Error('The endpoint is not found!');
  next(error);
});

// Error message about any other errors
app.use((error, req, res, next) => {
  res.status(error.status || 500);
  res.json({
    error: {
      message: error.message,
      line: error.stack // should be commented out in prod mode
    }
  })
});

const port = 3000;
const server = http.createServer(app);
server.listen(port);