"""Fixed-seed linear model signatures (§8.4 Pass 2).

A model signature is a fixed, seed-deterministic linear projection of the
flattened float32 parameter vector to ``d`` dimensions (d ≈ 128–256). Because
the projection is **linear**, the signature of an averaged model equals the
average of the signatures, so pairwise divergences used for peer selection
(§8.2) and TID (§8.3) are consistent under merging — the single divergence
basis (D5).

A dense Gaussian projection matrix would be ``d × P`` (≈1 GB at d=256, P=1e6),
so we use a memory-efficient **count-sketch / feature-hashing** projection:
each parameter index ``j`` is hashed to an output bucket ``h(j) ∈ [0,d)`` with a
sign ``s(j) ∈ {−1,+1}``, and ``sig[h(j)] += s(j)·w[j]``. This is O(P) memory,
exactly linear, deterministic from the seed, and preserves ℓ₂ distances up to a
quantified distortion (measured in Track B, §8.4). Signatures are float32
end-to-end so the Java reader's float reads match bit-for-bit.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

__all__ = ["Projection", "make_projection", "signature", "l2"]


def l2(a: np.ndarray, b: np.ndarray) -> float:
    """ℓ₂ distance computed exactly like Java's ``FLGossipNode.l2``: each float32
    element is widened to float64 before the subtraction and the accumulation, so
    the divergence values (and hence the selection agreement) match bit-for-bit
    across the bridge."""
    da = np.asarray(a, dtype=np.float64)
    db = np.asarray(b, dtype=np.float64)
    diff = da - db
    return float(np.sqrt(np.dot(diff, diff)))


@dataclass(frozen=True)
class Projection:
    """A count-sketch projection: per-parameter output bucket + sign."""
    d: int
    param_count: int
    buckets: np.ndarray  # int64[param_count] in [0, d)
    signs: np.ndarray    # float32[param_count] in {-1, +1}


def make_projection(param_count: int, d: int, seed: int) -> Projection:
    """Builds the fixed projection for a model of ``param_count`` parameters.

    Deterministic in ``seed`` (NumPy PCG64). The projection is generated once
    per model on the Python side only; Java never regenerates it — it reads the
    resulting float32 signature values from the sidecar.
    """
    rng = np.random.default_rng(seed)
    buckets = rng.integers(0, d, size=param_count, dtype=np.int64)
    signs = np.where(rng.integers(0, 2, size=param_count) == 0,
                     np.float32(-1.0), np.float32(1.0)).astype(np.float32)
    return Projection(d=d, param_count=param_count, buckets=buckets, signs=signs)


def signature(weights_flat: np.ndarray, proj: Projection) -> np.ndarray:
    """Projects a flattened float32 parameter vector to its ``d``-dim signature."""
    w = np.asarray(weights_flat, dtype=np.float32).ravel()
    if w.shape[0] != proj.param_count:
        raise ValueError(f"weight length {w.shape[0]} != projection param_count {proj.param_count}")
    sig = np.zeros(proj.d, dtype=np.float32)
    np.add.at(sig, proj.buckets, (proj.signs * w).astype(np.float32))
    return sig.astype(np.float32)
