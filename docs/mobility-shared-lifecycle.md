# Mobility integration with the shared lifecycle

This stage connects real mobility predictions to the same Java
`LifecycleCoordinator` and `LifecycleManager` used by load prediction. It adds
domain-specific assessment while preserving the existing scalar load evaluator.

## Implemented scope

| Responsibility | Owner in this stage |
| --- | --- |
| Official movement and actual positions | Java simulator: Nomadic or RandomWalk |
| Causal samples, growing training requests and LSTM fitting | Existing Python mobility scheduler |
| Frozen forecasts and training-completion messages | Python worker |
| Target-time matching and position errors in metres | Java mobility adapter |
| READY, ACTIVE and DEGRADED transitions | Shared Java LifecycleCoordinator |
| One active owner per feature, promotion and baseline fallback | Shared Java LifecycleManager |
| Selected forecast and later actual error | Java prediction/evaluation ledgers |
| Independent event, model, scaler and result verification | Python verification runner |

The adapter calls the real shared manager. Setting a manifest flag alone cannot
activate a model. Its run/device-specific feature name prevents collisions with
CPU, memory or other load features; it never resets the global manager.

**The complete load training controller is not yet reused.** Training requests
still come from the Python mobility scheduler. Retraining after an ACTIVE model
degrades is deferred. TCN/GRU adapters, multi-model selection and connection control
are outside this stage. `OnlineModelLifecycleManager` and
`LstmOnlineLifecycleController` are not modified by this package.

## Causality and state transitions

For each simulation tick:

1. Java observes the real position after movement and connection refresh.
2. Java settles forecasts issued at the previous tick using that position.
3. After 64 matched future targets, the shared coordinator evaluates a candidate.
   A passing candidate becomes READY, and the shared manager promotes it to ACTIVE.
   A rejected candidate becomes FAILED; the existing Python retry scheduler may
   train the next version using the available growing prefix.
4. Python finishes any training at the same simulation tick and publishes its
   immutable version and forecast batch. No forecast can precede training completion.
5. Java selects the currently registered active owner and records that prediction.
   Before qualification, or after fallback, selection uses HoldPosition.

An ACTIVE model is monitored at every subsequent matched target using the latest
64 paired errors, including the overlapping tail of its qualification block.
If it fails the same relative-quality rule, the common coordinator marks it
DEGRADED and the manager restores HoldPosition before the next selection.

The Python first-block qualification decision stays frozen for audit. A Java
DEGRADED state does not rewrite that earlier PASS. In this stage the Python
scheduler still defers requests after that frozen PASS. Java continues using the
baseline until the run ends; it does not pretend that retraining was scheduled.

## Quality rule and sample sizes

The unchanged relative gate chooses the better HoldPosition/ConstantVelocity
baseline on the same 64 actual targets. The candidate must have strictly lower
position RMSE and no worse mean position error. The minimum relative improvement
is 0, with strict RMSE improvement still required. There is no absolute accuracy
claim or guaranteed minimum useful improvement.

Position errors are great-circle endpoint distances in metres, using Earth radius
6,378,137 m. Load percentage accuracy and SMAPE are not assigned artificial values
for positions. Domain assessments leave those metrics unavailable; the original
load policy, including its tolerances, remains unchanged.

The model still receives eight historical feature rows and predicts displacement
60 seconds ahead. This input length is distinct from the training-set size and
the 64-target evaluation block. Initial motion warm-up plus delayed target arrival
means 32 complete training windows first exist at observation 41.

| Observation number | Causal milestone |
| --- | --- |
| 41 | Fit v0001 using 32 complete windows |
| 105 | Finish v0001's 64-target block; retry with 96 windows only after REJECT |
| 169 | Finish v0002's block; retry with 160 windows only after REJECT |
| 233 | Finish v0003's block; promote on PASS, or retry with 224 windows after REJECT |
| 234–297 | Observe post-qualification selection/monitoring, or evaluate v0004 |
| 297 | Settle the final target; do not fit a terminal model with no future targets |

These are observation counts, not epoch numbers. Model fitting is synchronous in
wall-clock time and does not advance simulated time. Each fitted version retains
its own training prefix, scaler, model and first 64-target decision.

## Running the verification

Activate the existing `dissect-fog` environment and run from the repository root:

```powershell
python predictor-ui/scripts/mobility/run_mobility_shared_lifecycle.py
```

The runner executes both official strategies for 296 simulated minutes, producing
297 observations per strategy. It freezes Python sources and compiled Java runtime
in a fresh result directory, checks the original causal retry stream, reloads every
saved model, reconstructs its training-only scaler, and independently replays the
Java ownership decisions from actual target positions.

Successful completion prints `MOBILITY_SHARED_LIFECYCLE_OK`. Model PASS/REJECT and
the final active owner depend on measured quality; verification does not force a
candidate to pass. Existing short and `--long` retry commands remain available and
retain their shadow-only behavior.

Results are written below `simulator/sim_res/mobility-shared-lifecycle/`:

- Batch `shared-lifecycle.json`: per-strategy checks, artifact hashes and scope.
- Run `shared-lifecycle.json`: authoritative Java states and active owner.
- `shared_lifecycle_events.jsonl`: actual observations, training messages,
  frozen forecasts, matching, qualification, promotion, monitoring and fallback.
- `shared_selected_predictions.csv`: forecasts actually selected by the manager.
- `shared_selected_evaluations.csv`: errors after those forecasts' real targets arrive.
- Existing `retry_*`, request snapshots and model files: Python training/shadow evidence.

The old Python `retry.json` fields `active=false` and `promotion=DISABLED` describe
that component's authority. The Java `shared-lifecycle.json` records actual shared
selection. `shadow_receipts.csv` remains a transport receipt and must not be used
as the selected-model ledger.

The selected forecasts do not steer device movement, connection changes, migration
or offloading in this stage. `connection_control=false` remains explicit.

## Local validation

Validation used the reviewed Windows source snapshot, Java 17, Python 3.12 and CPU
TensorFlow 2.21. The Windows installation and execution require separate confirmation.

- 107 Python tests passed, including protocol ordering and failed/incomplete models.
- 203 Java tests passed, including the supplied load lifecycle suites, 28 mobility
  regression tests, and nine new domain/shared-adapter tests.
- The real LSTM run completed both official strategies with 288 selected forecasts
  and 288 matched selected targets per strategy.
- Nomadic rejected four versions and kept HoldPosition throughout.
- RandomWalk rejected v0001/v0002, passed v0003, selected it for six origins, then
  detected degradation and fell back to HoldPosition. Its frozen first-block PASS
  remained intact. This demonstrates lifecycle behavior, not general superiority.
- Independent audit rejected a false active flag, premature promotion, changed
  issued coordinates and a missing fallback event.

No trained models, generated simulation outputs, local paths, or non-English
documentation need to be committed with the source changes.
