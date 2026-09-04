"""Test-plan item 11: the shared ``local_train`` makes all three coordination
modes produce a byte-identical weight trajectory on a single isolated node
(no peers, no aggregation) for the same seed — so ERQ1 compares coordination,
not three accidentally-different trainers."""
from __future__ import annotations

import numpy as np
import torch

import data
from models import build_model, flatten_params
from modes.centralized import run_centralized
from modes.hierarchical import run_hierarchical
from train_step import local_train, round_train_seed, set_determinism

SEED = 123
ROUNDS = 3
EPOCHS = 1
LR = 0.05
BATCH = 16


def _fresh_model():
    torch.manual_seed(777)  # identical init across modes
    return build_model("lenet5", num_classes=4, in_channels=1)


def _dataset():
    return data.synthetic_classification(64, num_classes=4, channels=1, size=28, seed=5)


def test_one_node_modes_equivalent():
    set_determinism(SEED)
    ds = _dataset()

    # Gossip on one isolated node = local_train each round (no peers/merge).
    gm = _fresh_model()
    for t in range(ROUNDS):
        local_train(gm, ds, EPOCHS, LR, BATCH, round_train_seed(SEED, t, 0))
    gossip_flat = flatten_params(gm).numpy()

    # Centralized with a single client.
    cflat = run_centralized([_fresh_model()], [ds], [len(ds)], ROUNDS, EPOCHS, LR, BATCH, SEED).numpy()

    # Hierarchical with a single node / single cluster (G=1).
    hflat = run_hierarchical([_fresh_model()], [ds], [len(ds)], [0], ROUNDS, 1, EPOCHS, LR, BATCH, SEED).numpy()

    np.testing.assert_array_equal(gossip_flat, cflat)
    np.testing.assert_array_equal(gossip_flat, hflat)
