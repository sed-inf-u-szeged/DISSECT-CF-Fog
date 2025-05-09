const jwt = require("jsonwebtoken");
const config = require("../config/gen-config");

/**
 * Checks the given request token.
 * - 403 - Not token provided in request header.
 * - 401 - If the token verification fails.
 */
const verifyToken = (req, res, next) => {
  const token = req.headers["x-access-token"];

  if (!token) {
    return res.status(403).send({
      message: "No token provided!"
    });
  }

  jwt.verify(token, config.secret, (err, decoded) => {
    if (err) {
      return res.status(401).send({
        message: "Unauthorized!"
      });
    }
    req.userId = decoded.id;
    next();
  });
};

const authJwt = {
  verifyToken
}

module.exports = authJwt;