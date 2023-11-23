import socket
import traceback
import time

from socket_client import SocketClient
from socket_message import SocketMessage

from predictor import Predictor
from app_utils.consts import APPLICATION_PREDICTOR
from app_utils.log import Log

import warnings


class Application:

    def __init__(self, disable_warnings):
        if disable_warnings:
            warnings.filterwarnings("ignore")

        self._socket = SocketClient(socket.gethostname(), 65432)
        self._socket.connect()
        self._predictor = None
        self._min_prediction_time = None

        self.loop()

    def loop(self):
        while self._socket.connected:
            request = self._socket.wait_and_get()
            response = self.handle_message(request)
            if response == "STOP":
                break
            self._socket.send(response)

    def handle_message(self, message):
        if message.event == "predict-feature":
            prediction = None
            try:
                start = time.time()
                prediction = self._predictor.compute(message.data["feature"])
                elapsed_time = time.time() - start
                if self._min_prediction_time is not None and elapsed_time < self._min_prediction_time:
                    time.sleep(self._min_prediction_time - elapsed_time)
            except:
                traceback.print_exc()

            response = SocketMessage("prediction-result", {"prediction": prediction})

            if prediction is None:
                response.data["error"] = "Something went wrong while predicting."
            return response

        elif message.event == "simulation-settings":
            self._min_prediction_time = message.data["simulation-settings"]["prediction"]["minPredictionTime"]
            self._predictor = Predictor(message.data["simulation-settings"])
            return SocketMessage("simulation-settings-response", {"message": None})
        elif message.event == "get-name":
            return SocketMessage("get-name-response", {"name": APPLICATION_PREDICTOR})
        elif message.event == "stop-connection":
            self._socket.close()
            return "STOP"
        else:
            Log.error("No event has been found!")

        return None
