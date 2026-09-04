"""Signature projection: linearity (the §8.4 property) + determinism."""
from __future__ import annotations

import numpy as np

import signatures


def test_linearity():
    # signature(a·w1 + b·w2) == a·signature(w1) + b·signature(w2) within float32 tol.
    p, d, seed = 5000, 64, 1234
    proj = signatures.make_projection(p, d, seed)
    rng = np.random.default_rng(0)
    w1 = rng.standard_normal(p).astype(np.float32)
    w2 = rng.standard_normal(p).astype(np.float32)
    a, b = np.float32(0.3), np.float32(0.7)

    lhs = signatures.signature(a * w1 + b * w2, proj)
    rhs = a * signatures.signature(w1, proj) + b * signatures.signature(w2, proj)
    assert np.allclose(lhs, rhs, atol=1e-4), "projection must be linear"


def test_average_of_signatures_equals_signature_of_average():
    # The key §8.4 claim used by drift-suppressed merge + TID.
    p, d, seed = 4000, 128, 7
    proj = signatures.make_projection(p, d, seed)
    rng = np.random.default_rng(1)
    ws = [rng.standard_normal(p).astype(np.float32) for _ in range(4)]
    weights = np.array([0.4, 0.3, 0.2, 0.1], dtype=np.float32)

    avg_model = sum(wi * w for wi, w in zip(weights, ws))
    sig_of_avg = signatures.signature(avg_model, proj)
    avg_of_sig = sum(wi * signatures.signature(w, proj) for wi, w in zip(weights, ws))
    assert np.allclose(sig_of_avg, avg_of_sig, atol=1e-4)


def test_determinism():
    p, d = 1000, 32
    a = signatures.make_projection(p, d, 99)
    b = signatures.make_projection(p, d, 99)
    np.testing.assert_array_equal(a.buckets, b.buckets)
    np.testing.assert_array_equal(a.signs, b.signs)
    w = np.arange(p, dtype=np.float32)
    np.testing.assert_array_equal(signatures.signature(w, a), signatures.signature(w, b))


def test_dtype_is_float32():
    proj = signatures.make_projection(100, 16, 3)
    sig = signatures.signature(np.ones(100, dtype=np.float32), proj)
    assert sig.dtype == np.float32
