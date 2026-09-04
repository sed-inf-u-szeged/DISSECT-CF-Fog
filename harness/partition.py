"""Data partitioning across nodes (§9): Dirichlet(ρ) non-IID partitioner with an
IID control, seed-deterministic, one shard per node."""
from __future__ import annotations

from typing import List

import numpy as np


def dirichlet_partition(labels: np.ndarray, n_nodes: int, rho: float, seed: int) -> List[np.ndarray]:
    """Partitions sample indices across ``n_nodes`` by a Dirichlet(ρ) draw per
    class (the prevailing non-IID mechanism). Lower ρ ⇒ more heterogeneity.
    Deterministic in ``seed``. Returns one index array per node."""
    labels = np.asarray(labels)
    rng = np.random.default_rng(seed)
    classes = np.unique(labels)
    node_idx: List[List[int]] = [[] for _ in range(n_nodes)]
    for c in classes:
        idx_c = np.where(labels == c)[0]
        rng.shuffle(idx_c)
        proportions = rng.dirichlet(np.full(n_nodes, rho))
        # Cut points along the shuffled class indices.
        cuts = (np.cumsum(proportions) * len(idx_c)).astype(int)[:-1]
        for node, chunk in enumerate(np.split(idx_c, cuts)):
            node_idx[node].extend(chunk.tolist())
    return [np.array(sorted(ix), dtype=np.int64) for ix in node_idx]


def iid_partition(n_samples: int, n_nodes: int, seed: int) -> List[np.ndarray]:
    """IID control: a uniform random equal split."""
    rng = np.random.default_rng(seed)
    perm = rng.permutation(n_samples)
    return [np.array(sorted(s.tolist()), dtype=np.int64) for s in np.array_split(perm, n_nodes)]
