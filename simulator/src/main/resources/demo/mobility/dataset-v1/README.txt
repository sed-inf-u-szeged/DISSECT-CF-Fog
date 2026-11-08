Mobility pilot dataset plan v1

Status: CONFIGURATION ONLY. No simulation data or trained model is included.
12 routes, 36 cases; train=24, validation=6, test=6.
Speeds: 2.0, 4.1, 6.0 m/s. Seed=42.

Read docs/mobility-workflow.md for the dataset protocol and workflow.
The checker uses only the Python standard library (Python >=3.8).
Run from the project root:
python predictor-ui/scripts/mobility/check_dataset_plan.py

This package adds only files. It requires the previously installed and tested
mobility-samples-export-v1 and mobility-scenarios-v1 code.
No Maven build or npm build is needed for this configuration-only step.
The installer checks every file before copying. Unexpected existing files cause
an abort before writes; identical existing files are left unchanged.
Do not edit a frozen plan based on test prediction quality. Make a new version
when changing the dataset design. Keep failed runs in the later batch log.
