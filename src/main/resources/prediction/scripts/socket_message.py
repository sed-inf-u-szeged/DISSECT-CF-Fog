import json
import re


class SocketMessage:
    def __init__(self, event, data):
        self._event = event
        self._data = data

    def serialize(self):
        return re.sub(r'"_', "\"", str(json.dumps(vars(self))))

    @property
    def event(self):
        return self._event

    @property
    def data(self):
        return self._data
