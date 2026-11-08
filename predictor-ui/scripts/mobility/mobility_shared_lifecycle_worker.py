"""Publish frozen training/forecast events to the real shared Java lifecycle manager.

Python's retry manifest describes its training/qualification stream only. Actual
activation and fallback belong to Java and are recorded in shared-lifecycle.json.
Retraining after Java detects active degradation is a later integration stage.
"""
import argparse
import contextlib
import json
import os
from pathlib import Path
import sys
import traceback

from build_mobility_windows import read_json, require
from mobility_online_buffer import TrainingPolicy
from mobility_online_buffer_worker import serve
from mobility_online_lstm import OnlineLstm, tensorflow_runtime
from mobility_online_retry_worker import RetryObserver

PROTOCOL = "MOBILITY_SHARED_LIFECYCLE_V1"


class SharedLifecycleObserver(RetryObserver):
    """Send only facts; Java recomputes errors and owns every active-state change."""

    def __init__(self, output, trainer, reply, expected_observations=297):
        self.reply = reply
        self.protocol_sequence = 0
        super().__init__(output, trainer, expected_observations)

    def publish(self, kind, now, **details):
        self.protocol_sequence += 1
        message = dict(sequence=self.protocol_sequence, event=kind, sim_time_ms=now,
                       run_id=self.observation["run_id"], device_id=self.observation["device_id"], **details)
        print("LIFECYCLE\t" + json.dumps(message, allow_nan=False, separators=(",", ":")),
              file=self.reply, flush=True)

    def event(self, kind, now, **details):
        super().event(kind, now, **details)
        if kind in ("TRAINING_STARTED", "TRAINING_COMPLETE"):
            self.publish(kind, now, **details)

    def publish_predictions(self, predictions):
        first = predictions[0]
        self.publish("FORECAST", first["origin_ms"], model_version=self.current, target_ms=first["target_ms"],
                     predictions=[dict(model=r["model"], latitude_deg=r["predicted_latitude_deg"],
                                       longitude_deg=r["predicted_longitude_deg"]) for r in predictions])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", "--training", dest="policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    observer = None
    try:
        require(TrainingPolicy(**read_json(args.policy)) == TrainingPolicy(), "Expected the verified training policy")
        with contextlib.redirect_stdout(sys.stderr):
            tf = tensorflow_runtime()
        expected = int(os.environ.get("MOBILITY_ONLINE_EXPECTED_OBSERVATIONS", "297"))
        observer = SharedLifecycleObserver(args.output.resolve(), lambda request, snapshot, output:
                                           OnlineLstm.fit(tf, request, snapshot, output), sys.stdout, expected)
        serve(args.policy.resolve(), args.output.resolve(), sys.stdin, sys.stdout, observer, expected, PROTOCOL)
    except Exception as error:
        if observer is not None:
            observer.fail(error)
        traceback.print_exc(file=sys.stderr)
        return 1
    finally:
        if observer is not None:
            observer.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
