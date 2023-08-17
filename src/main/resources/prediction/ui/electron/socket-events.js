let buffers;
let bufferSize;
let messageSize;

module.exports.handleSocketData = (buffer) => {
    return new Promise((resolve, reject) => {
        if (buffer.toString().includes('data-size')) {
            try {
                const responseMessageSize = JSON.parse(buffer.toString());
                messageSize = responseMessageSize['data']['size'];
                buffers = [];
                bufferSize = 0;
                resolve({ ackResponse: responseMessageSize })
            } catch (error) {
                reject(error)
            }
        } else {
            bufferSize += buffer.length;
            buffers.push(buffer);

            if (bufferSize < messageSize) {
                resolve({ wait: true, messageSize, bufferSize })
            } else {
                const response = JSON.parse(Buffer.concat(buffers).toString());
                resolve({ socketResponse: response });
            }
        }
    });
}

module.exports.createACKMessage = (event) => {
    return { event: `${event}-response`, data: { message: 'ACK' }};
}