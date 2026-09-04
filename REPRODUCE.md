# Reproducing the gossip FL results

This branch extends the DISSECT-CF-Fog FL module with decentralized (gossip)
federated learning over explicit communication topologies. It contains the
simulator extension, the Python training harness it couples to, the scenario
definitions of the reported campaign, and a reference copy of the measurements
those scenarios produced.

## Requirements

* JDK 11 or newer, and Maven 3.8 or newer.
* Python 3.12 with the packages listed in `harness/requirements.txt`.

PyTorch is installed from the CPU index:

```
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install -r harness/requirements.txt
```

The harness is plain PyTorch for all three coordination modes, so no federated
learning framework is required.

## Build and test

```
cd simulator
mvn compile
mvn test
```

## Run

The campaign driver resolves the repository root from its own location, so it
works from any checkout without editing paths:

```
sh tools/run_campaign.sh
```

With no arguments it runs every stage in order: the pre-registration dry run,
cell V, scenarios S1 to S6, and the report. Individual stages can be named:

```
sh tools/run_campaign.sh V
sh tools/run_campaign.sh S1 S2
```

Each stage is resumable. The scenario runner skips any cell that already has a
`pass3/run_metadata.json`, so re-invoking after an interruption continues rather
than restarts.

Stage order is part of the protocol rather than a convenience. Cell V produces
the compute-delay calibration that the later scenarios consume, and its fidelity
gate must pass before the trace-driven measurements are trusted. A failed gate
stops the run.

The interpreter and Maven can be overridden:

```
PY=/path/to/python3.12 MVN=/path/to/mvn sh tools/run_campaign.sh
```

Fresh runs write to `simulator/results/`, which is not tracked.

## Reference artefacts

`artefacts/paper/` holds the measurements behind the reported results, so the
analysis can be re-run without repeating the simulation:

```
python analysis/report.py --results-root artefacts --campaign paper \
    --scenario V --scenario S1 --scenario S2 \
    --scenario S3 --scenario S4 --scenario S5
```

The scenarios must be named explicitly, because `--all` also expects S6, which
is not part of the reported set.

Layout:

```
artefacts/paper/
  preregistration.txt        campaign grid, archived before any compute
  <scenario>/<cell>/seed_<n>/
    pass1/system_trace.json  topology, per-link costs, and schedule
    pass3/*.csv, *.json      per-round measurements and run metadata
  report/results.json        every reported quantity, machine-readable
  report/figures/            the reported figures
  logs/                      stage logs
artefacts/evidence/          diagnostic bundle for the fidelity tripwire
```

Two categories of output are deliberately absent because they regenerate from
what is here: the per-cell signature blobs written by pass 2, and the per-cell
figures. `report/results.md`, the tables in the order they appear in the paper,
is likewise produced by `analysis/report.py`.

## Notes

`run_metadata.json` records a `code_git_hash` field. It refers to the internal
development revision that produced the run, not to a commit on this branch.

The `prediction` package carries a pre-existing test defect on this branch's
parent: `FeatureManagerTest` fails with a null `sqLiteManager`. It is unrelated
to the FL module and predates this work.
