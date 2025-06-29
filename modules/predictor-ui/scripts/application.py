import sys
import json

from predictor import Predictor

class Application:

    def __init__(self):
        predictor = Predictor(json.loads(sys.argv[1])["predictor-settings"])

        while True:
            line = sys.stdin.readline()

            if not line:
                break

            try:
                prediction = predictor.compute(json.loads(line)["feature"])
                json_obj = {"prediction": prediction}
                print(f"{json.dumps(json_obj)}", flush=True)
            except Exception as e:
                print("Error: %s", repr(e), flush=True)
