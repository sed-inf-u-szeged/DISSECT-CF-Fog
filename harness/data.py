"""Datasets for the Pass-2 harness (§9 workloads).

Provides the real campaign dataset (MNIST via torchvision) and a deterministic
**synthetic** image-classification dataset used by the co-simulation bridge
canary (P3.7): the canary validates the bridge contract (determinism, selection
agreement, replay fidelity), for which the pixel content is irrelevant — a
seeded synthetic set keeps the canary hermetic (no network) and fast while
still driving genuine end-to-end training (real model, real SGD, real weight
divergence).

**Train / evaluation separation (§9 metrics).** Every workload is loaded as a
*pair*: the training pool that is Dirichlet-partitioned across the nodes, and a
disjoint held-out evaluation set that no node ever trains on. All reported
accuracies come from the held-out set. Evaluating on the training pool would
report an optimistically biased figure that must not be called a test accuracy;
:func:`load_workload` is the single entry point that makes the split explicit.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import List, Tuple

import numpy as np
import torch


@dataclass
class Dataset:
    """A tiny in-memory classification dataset (images + integer labels)."""
    x: torch.Tensor  # [N, C, H, W] float32
    y: torch.Tensor  # [N] int64
    num_classes: int

    def __len__(self) -> int:
        return self.x.shape[0]


def _class_means(rng: np.random.Generator, num_classes: int, channels: int,
                 size: int) -> List[np.ndarray]:
    return [rng.standard_normal((channels, size, size)).astype(np.float32) * 0.5
            for _ in range(num_classes)]


def _blob_block(rng: np.random.Generator, mean: np.ndarray, per: int,
                channels: int, size: int) -> np.ndarray:
    return mean[None, :, :, :] + rng.standard_normal(
        (per, channels, size, size)).astype(np.float32) * 0.3


def synthetic_classification(n_samples: int, num_classes: int = 10,
                             channels: int = 1, size: int = 28, seed: int = 0,
                             n_test: int = 0):
    """A deterministic synthetic dataset: each class is a Gaussian blob with a
    class-specific mean, so a small CNN can actually learn it (real gradients).

    With ``n_test > 0`` a **disjoint** held-out set of ``n_test`` samples is
    drawn from the *same* class means (fresh noise, so it is genuinely unseen)
    and returned alongside the training pool. The training stream is consumed
    first and is unaffected by ``n_test``, so the training pool is identical
    whether or not a held-out set is requested.
    """
    rng = np.random.default_rng(seed)
    per = n_samples // num_classes
    means = []
    xs, ys = [], []
    for c in range(num_classes):
        # Class-specific mean field + noise → learnable signal.
        mean = rng.standard_normal((channels, size, size)).astype(np.float32) * 0.5
        means.append(mean)
        xs.append(_blob_block(rng, mean, per, channels, size))
        ys.append(np.full(per, c, dtype=np.int64))
    x = np.concatenate(xs, axis=0)
    y = np.concatenate(ys, axis=0)
    # Deterministic shuffle.
    perm = rng.permutation(x.shape[0])
    train = Dataset(x=torch.from_numpy(x[perm]), y=torch.from_numpy(y[perm]),
                    num_classes=num_classes)
    if n_test <= 0:
        return train

    # Held-out set: same class means, fresh noise from the continuing stream.
    per_test = max(1, n_test // num_classes)
    txs, tys = [], []
    for c in range(num_classes):
        txs.append(_blob_block(rng, means[c], per_test, channels, size))
        tys.append(np.full(per_test, c, dtype=np.int64))
    tx = np.concatenate(txs, axis=0)
    ty = np.concatenate(tys, axis=0)
    tperm = rng.permutation(tx.shape[0])
    test = Dataset(x=torch.from_numpy(tx[tperm]), y=torch.from_numpy(ty[tperm]),
                   num_classes=num_classes)
    return train, test


def mnist(root: str, train: bool = True) -> Dataset:
    """Real MNIST via torchvision (campaign use; downloads on first call).

    ``train=True`` is the 60k training split, ``train=False`` the disjoint 10k
    test split — the two are separate files in the MNIST distribution, so the
    held-out set is genuinely unseen.
    """
    from torchvision import datasets, transforms  # local import: only for campaigns
    tfm = transforms.Compose([transforms.ToTensor()])
    ds = datasets.MNIST(root=root, train=train, download=True, transform=tfm)
    x = torch.stack([ds[i][0] for i in range(len(ds))])
    y = torch.tensor([ds[i][1] for i in range(len(ds))], dtype=torch.int64)
    return Dataset(x=x, y=y, num_classes=10)


def stratified_subsample(ds: Dataset, n_samples: int, seed: int) -> Dataset:
    """A deterministic class-balanced subsample of ``n_samples`` items.

    Used to cut MNIST down to the n=30 scenario size (200 samples/node) while
    keeping every class represented, so the Dirichlet partitioner still has all
    ten classes to skew. Returns ``ds`` unchanged when it is already small
    enough.
    """
    if n_samples <= 0 or n_samples >= len(ds):
        return ds
    rng = np.random.default_rng(seed)
    labels = ds.y.numpy()
    classes = np.unique(labels)
    per = n_samples // len(classes)
    picked: List[int] = []
    for c in classes:
        idx_c = np.where(labels == c)[0]
        take = min(per, len(idx_c))
        picked.extend(rng.choice(idx_c, size=take, replace=False).tolist())
    # Top up any rounding shortfall from the unpicked remainder.
    if len(picked) < n_samples:
        remaining = np.setdiff1d(np.arange(len(labels)), np.asarray(picked, dtype=np.int64))
        extra = rng.choice(remaining, size=min(n_samples - len(picked), len(remaining)),
                           replace=False)
        picked.extend(extra.tolist())
    return subset(ds, sorted(picked))


def load_workload(name: str, *, root: str, samples: int, num_classes: int,
                  in_channels: int, seed: int,
                  eval_samples: int = 0) -> Tuple[Dataset, Dataset]:
    """Loads a workload as a ``(train_pool, eval_pool)`` pair (§9 metrics).

    ``train_pool`` is what the Dirichlet partitioner splits across the nodes;
    ``eval_pool`` is disjoint and is the only basis for reported accuracy.

    * ``mnist`` — the 60k train split (optionally cut to ``samples`` by a
      class-balanced subsample) against the disjoint 10k test split.
    * ``synthetic`` — Gaussian blobs against a held-out draw from the same
      class means.

    :param name: ``"mnist"`` or ``"synthetic"``.
    :param root: torchvision data root (MNIST only).
    :param samples: training-pool size; ``<= 0`` means the full split.
    :param num_classes: number of classes (synthetic only; MNIST is 10).
    :param in_channels: image channels (synthetic only; MNIST is 1).
    :param seed: determinism seed for the subsample / generator.
    :param eval_samples: cap on the held-out set; ``<= 0`` means the full split.
    """
    if name == "synthetic":
        n_test = eval_samples if eval_samples > 0 else max(num_classes, samples // 2)
        train, test = synthetic_classification(
            samples, num_classes=num_classes, channels=in_channels, size=28,
            seed=seed, n_test=n_test)
        return train, test
    if name == "mnist":
        train = mnist(root, train=True)
        test = mnist(root, train=False)
        if samples > 0:
            # Subsample seed is offset so the train and eval draws never coincide.
            train = stratified_subsample(train, samples, seed)
        if eval_samples > 0:
            test = stratified_subsample(test, eval_samples, seed + 1)
        return train, test
    raise ValueError(f"unknown dataset: {name}")


def subset(ds: Dataset, indices) -> Dataset:
    idx = torch.as_tensor(list(indices), dtype=torch.int64)
    return Dataset(x=ds.x[idx], y=ds.y[idx], num_classes=ds.num_classes)
