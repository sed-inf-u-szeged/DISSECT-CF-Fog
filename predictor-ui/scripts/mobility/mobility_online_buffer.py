"""Causal labelled windows and model-independent training requests.

Requests contain raw, immutable training prefixes. No model/scaler is fitted here.
"""
import copy
from dataclasses import asdict, dataclass
import math

from build_mobility_windows import project, require
from check_mobility_run import motion
from mobility_shadow_worker import History

REFERENCE = (47.73, 18.27)
STEP = 60000


@dataclass(frozen=True)
class TrainingPolicy:
    first_windows: int = 32
    growth_windows: int = 64
    max_training_windows: int = 4096

    def __post_init__(self):
        require(all(type(v) is int and v > 0 for v in asdict(self).values()),
                "Policy counts must be positive integers")
        require(self.first_windows <= self.max_training_windows, "First request exceeds the cap")


@dataclass(frozen=True)
class PendingWindow:
    run_id: str
    device_id: str
    origin_ms: int
    target_ms: int
    history_time_ms: tuple
    anchor_latitude_deg: float
    anchor_longitude_deg: float
    origin_node: str
    x: tuple


@dataclass(frozen=True)
class LabelledWindow:
    window: PendingWindow
    completed_at_sim_ms: int
    target_latitude_deg: float
    target_longitude_deg: float
    y: tuple

    def record(self):
        result = asdict(self.window)
        result.update(completed_at_sim_ms=self.completed_at_sim_ms,
                      target_latitude_deg=self.target_latitude_deg,
                      target_longitude_deg=self.target_longitude_deg, y=self.y)
        return result


@dataclass(frozen=True)
class TrainingRequest:
    request_number: int
    requested_at_sim_ms: int
    windows: tuple

    def metadata(self):
        return dict(request_number=self.request_number, requested_at_sim_ms=self.requested_at_sim_ms,
                    training_windows=len(self.windows),
                    first_origin_ms=self.windows[0].window.origin_ms,
                    last_origin_ms=self.windows[-1].window.origin_ms,
                    latest_label_ms=self.windows[-1].completed_at_sim_ms,
                    training="NOT_STARTED", scaler="NOT_FITTED", mode="REQUEST_ONLY")


class OnlineSampleBuffer:
    """One run/device per instance. Reject invalid observations without advancing state.

    The development request probe emits at 32, 96, 160, ... completed windows.
    The final request is capped; the retained training prefix never exceeds the cap.
    A future lifecycle controller must decide whether and when to execute a request.
    """

    def __init__(self, policy=None):
        self.policy = policy or TrainingPolicy()
        self.history = History()
        self.pending = None
        self.completed_count = 0
        self.request_count = 0
        self._training_prefix = []
        self.next_request_windows = self.policy.first_windows
        self.final_seen = False
        self.closed = False

    def observe(self, raw, forecast_allowed=True):
        require(type(forecast_allowed) is bool, "Forecast flag must be boolean")
        require(not self.closed and not self.final_seen, "Observation after stream finalization")
        # History's parser is shared with frozen inference. Commit only after all checks pass.
        candidate = copy.deepcopy(self.history)
        row, x = candidate.append(raw)
        now = int(row["time_ms"])
        if self.history.count:
            distance, heading = motion(self.history.rows[-1], row)
            require(abs(float(row["displacement_m"]) - distance) < 1e-5
                    and abs(float(row["observed_speed_m_s"]) - distance / 60) < 1e-6,
                    "Motion does not match observed positions")
            moving = distance > 1e-6
            require(row["heading_valid"] == ("true" if moving else "false"), "Heading mask differs")
            if moving:
                value = float(row["heading_deg"])
                require(math.isfinite(value) and 0 <= value < 360
                        and abs((value - heading + 180) % 360 - 180) < 1e-5, "Heading differs")
            else:
                require(row["heading_deg"] == "", "Stationary heading must be empty")
        if not forecast_allowed:
            require(self.pending is not None, "Final observation must settle a pending window")
        labelled = None
        if self.pending is not None:
            old = self.pending
            require((row["run_id"], row["device_id"]) == (old.run_id, old.device_id)
                    and now == old.target_ms, "Target observation does not match pending window")
            anchor = project(old.anchor_latitude_deg, old.anchor_longitude_deg, REFERENCE)
            target = project(float(row["latitude_deg"]), float(row["longitude_deg"]), REFERENCE)
            labelled = LabelledWindow(old, now, float(row["latitude_deg"]),
                                     float(row["longitude_deg"]),
                                     (target[0] - anchor[0], target[1] - anchor[1]))
        pending = None
        if forecast_allowed and x is not None:
            pending = PendingWindow(row["run_id"], row["device_id"], now, now + STEP,
                                    tuple(int(q["time_ms"]) for q in candidate.rows),
                                    float(row["latitude_deg"]), float(row["longitude_deg"]),
                                    row["current_node"], tuple(tuple(v) for v in x))
        self.history = candidate
        self.pending = pending
        self.final_seen = not forecast_allowed
        request = None
        if labelled is not None:
            self.completed_count += 1
            if len(self._training_prefix) < self.policy.max_training_windows:
                self._training_prefix.append(labelled)
            if self.completed_count == self.next_request_windows:
                self.request_count += 1
                request = TrainingRequest(self.request_count, now, tuple(self._training_prefix))
                self.next_request_windows = (None if self.completed_count == self.policy.max_training_windows
                    else min(self.completed_count + self.policy.growth_windows, self.policy.max_training_windows))
        return row, pending, labelled, request

    def scheduled_request(self, number, count, now):
        """Freeze the latest mature window for an external lifecycle command.

        Scheduled requests do not alter the legacy prefix request counters.
        The live protocol is bounded below the retention cap.
        """
        require(type(number) is int and number > 0 and type(count) is int
                and 1 <= count <= self.policy.max_training_windows, "Invalid scheduled window")
        require(not self.closed and not self.final_seen and len(self._training_prefix) == self.completed_count
                and count <= self.completed_count and self._training_prefix[-1].completed_at_sim_ms == now,
                "Scheduled window is unavailable or stale")
        return TrainingRequest(number, now, tuple(self._training_prefix[-count:]))

    def finish(self):
        require(not self.closed and self.final_seen and self.pending is None,
                "Stream must end with a real final observation and no pending label")
        self.closed = True
        return dict(observations=self.history.count, completed_windows=self.completed_count,
                    retained_windows=len(self._training_prefix), pending_windows=0,
                    training_requests=self.request_count, next_request_windows=self.next_request_windows)
