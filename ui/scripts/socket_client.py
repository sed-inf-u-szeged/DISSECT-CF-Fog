from socket import socket
import json

from socket_message import SocketMessage
from app_utils.consts import APPLICATION_FEATURE_HANDLER, APPLICATION_PREDICTOR
from app_utils.log import Log


class SocketClient:

    def __init__(self, host, port):
        self._host = host
        self._port = port
        self._socket = None
        self._connected = False

    def connect(self):
        while not self._connected:
            try:
                Log.warning("Waiting for server connection...")
                self._socket = socket()
                self._socket.connect((self._host, self._port))
                self._connected = True
            except:
                pass
        Log.warning("Client connected to server!")

    def close(self):
        self._connected = False
        if self._socket is not None:
            self._socket.close()
            self._socket = None

    def wait_and_get(self) -> SocketMessage:
        if self._connected is False:
            return

        response_message_size = json.loads(self._socket.recv(1024).decode())
        message_size = response_message_size["data"]["size"]

        self._socket.send(self.create_message(SocketMessage("data-size-response", {"message": "ACK"})))

        chunks = ''
        while len(chunks) < message_size:
            chunks += self._socket.recv(1024).decode()
        response = json.loads(chunks)

        Log.warning(f"[SOCKET-IN ] E: {response['event']}")
        return SocketMessage(response["event"], response["data"])

    def send(self, message: SocketMessage) -> SocketMessage:
        if self._connected is False:
            return

        Log.warning(f"[SOCKET-OUT] E: {message.event}")
        self._socket.send(SocketClient.create_message(message))

    @staticmethod
    def create_message(message):
        return bytes(message.serialize() + "\r\n", "utf-8")

    @property
    def connected(self):
        return self._connected
