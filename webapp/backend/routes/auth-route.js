const express = require('express');
const router = express.Router({caseSensitive:true});
const config = require("../config/gen-config");
const jwt = require("jsonwebtoken");
const bcrypt = require("bcryptjs");
const mongodb = require('../services/mongodb-service');

/**
 * It tries the sign up the new user. If it succeed it will send 201 response with a message,
 * if not then it will send 500 response with a message.
 * - 400 -  email already used
 * - 500 -  other error
 */
router.post("/signup", async (req, res) => {
    try {
        const user = await mongodb.getUser({ email: req.body.email })

        if (user != null) {
            res.status(400).send({ message: "Failed, e-mail is already in use!" });
        } else {


            await mongodb.addUser({ email: req.body.email, password: bcrypt.hashSync(req.body.password, 8) });
            res.status(201).send({ message: "User was registered successfully" });
        }
    } catch (e) {
        res.status(500).send({ message: "Error" });
    }
});

/**
 * It tries the sign in the user. If it succeed it will send 200 response with the user id, the email and token.
 * Response if something went wrong:
 * - 404 - email is not found
 * - 401 - invalid password
 * - 500 - other
 */
router.post("/signin", async (req, res) => {
    try {
        // Get the user connected to the email of the sign in request
        let user = await mongodb.getUser({
            email: req.body.email
        })

        // If the user (response) is null, it means that there are no users registered with the email in the sign in request
        if (user == null) {
            return res.status(404).send({ message: "User Not found." });
        } else {
            // Compare the hash of the password from the sign in request with the stored hash of the password
            const passwordIsValid = bcrypt.compareSync(
                req.body.password,
                user.password
            );

            // Check whether the passwords' hashes match
            if (!passwordIsValid) {
                return res.status(401).send({
                    accessToken: null,
                    message: "Invalid Password!"
                });
            }

            // Create a jwt token that is valid for 24 hours
            const token = jwt.sign({ id: user._id }, config.secret, {
                expiresIn: 86400 // 24 hours
            });

            // Return among others the jwt token
            res.status(200).send({
                id: user._id,
                email: user.email,
                accessToken: token
            });
        }
    } catch (e) {
        res.status(500).send({ message: "Error" });
    }
});

module.exports = router;
