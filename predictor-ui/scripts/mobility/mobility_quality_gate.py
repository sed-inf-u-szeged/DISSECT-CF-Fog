"""Freeze a relative quality decision on one model version's first mature evaluation block."""
import copy
from dataclasses import asdict, dataclass
import math

from build_mobility_windows import require
from mobility_learning import error_metrics
from mobility_shadow_evaluation import MODELS, distance_m, gps

GATE_KIND = "RELATIVE_BASELINE_V1"


@dataclass(frozen=True)
class QualityPolicy:
    minimum_evaluated_origins: int = 64
    minimum_relative_rmse_improvement: float = 0.0

    def __post_init__(self):
        require(type(self.minimum_evaluated_origins) is int and self.minimum_evaluated_origins >= 64,
                "At least 64 evaluated origins are required")
        value = self.minimum_relative_rmse_improvement
        require(type(value) in (float, int) and math.isfinite(value) and 0 <= value < 1,
                "Relative RMSE improvement must be in [0, 1)")


class RelativeQualityGate:
    """A decision component only; it never activates a model or executes training."""

    def __init__(self, policy, version, training_cutoff_ms):
        require(isinstance(policy, QualityPolicy) and isinstance(version, str) and version,
                "Invalid quality policy/version")
        require(type(training_cutoff_ms) is int and training_cutoff_ms >= 0, "Invalid training cutoff")
        self.policy, self.version, self.cutoff = policy, version, training_cutoff_ms
        self.identity = None
        self.last_target = training_cutoff_ms
        self.errors = {model: [] for model in MODELS}
        self._decision = None

    @property
    def decision(self):
        return copy.deepcopy(self._decision)

    @property
    def count(self):
        return len(self.errors["LSTM"])

    def observe(self, version, evaluated, observed_at_sim_ms):
        """Consume exactly one already-mature origin, atomically for all methods."""
        require(self._decision is None, "Quality decision is already frozen")
        require(version == self.version, "Evaluation model version differs")
        require(len(evaluated) == 3 and {r["model"] for r in evaluated} == set(MODELS),
                "Expected one evaluation per method at the same origin")
        now = int(observed_at_sim_ms)
        require(now == self.last_target + 60000, "Quality evaluation gap or duplicate")
        identity, truth = None, None
        checked = {}
        for row in evaluated:
            current_identity = (row["run_id"], row["device_id"])
            require(all(current_identity) and (identity is None or identity == current_identity)
                    and (self.identity is None or self.identity == current_identity), "Quality identity differs")
            identity = current_identity
            require(int(row["target_ms"]) == int(row["evaluated_at_sim_ms"]) == now
                    and int(row["origin_ms"]) == now - 60000 and now - 60000 >= self.cutoff,
                    "Quality evaluation precedes its actual target or training cutoff")
            actual = gps(row["actual_latitude_deg"], row["actual_longitude_deg"])
            require(truth is None or actual == truth, "Methods use different actual positions")
            truth = actual
            predicted = gps(row["predicted_latitude_deg"], row["predicted_longitude_deg"])
            error = float(row["position_error_m"])
            require(math.isfinite(error) and error >= 0 and abs(error - distance_m(predicted, actual)) <= 1e-7,
                    "Invalid or inconsistent quality error")
            checked[row["model"]] = error
        self.identity, self.last_target = identity, now
        for model in MODELS:
            self.errors[model].append(checked[model])
        if self.count == self.policy.minimum_evaluated_origins:
            metrics = {model: error_metrics(values) for model, values in self.errors.items()}
            # Ties resolve deterministically; every method uses the same matured block.
            reference = min(MODELS[:2], key=lambda model:
                            (metrics[model]["rmse_m"], metrics[model]["mean_error_m"], MODELS.index(model)))
            baseline, candidate = metrics[reference], metrics["LSTM"]
            checks = dict(rmse_strictly_better=candidate["rmse_m"] < baseline["rmse_m"],
                          required_rmse_improvement=candidate["rmse_m"] <= baseline["rmse_m"] *
                          (1 - self.policy.minimum_relative_rmse_improvement),
                          mean_error_not_worse=candidate["mean_error_m"] <= baseline["mean_error_m"])
            reasons = ["FAILED_" + name.upper() for name, passed in checks.items() if not passed]
            self._decision = dict(schema_version=1, gate_kind=GATE_KIND, policy=asdict(self.policy),
                                  model="LSTM", model_version=self.version, run_id=identity[0], device_id=identity[1],
                                  training_cutoff_sim_ms=self.cutoff, evaluated_origins=self.count,
                                  first_origin_ms=self.cutoff, last_origin_ms=now - 60000,
                                  first_target_ms=self.cutoff + 60000, last_target_ms=now, decided_at_sim_ms=now,
                                  decision="PASS" if all(checks.values()) else "REJECT",
                                  reason_codes=reasons or ["RELATIVE_QUALITY_PASSED"],
                                  reference_baseline=reference, metrics=metrics, checks=checks,
                                  relative_rmse_improvement=(1 - candidate["rmse_m"] / baseline["rmse_m"]
                                                             if baseline["rmse_m"] > 0 else None),
                                  absolute_quality="NOT_SPECIFIED", promotion="DISABLED", active=False)
        return self.decision
