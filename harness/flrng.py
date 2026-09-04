"""Bit-identical Python mirror of the Java ``SplitMix64`` + per-decision
``derive`` helper used by the DISSECT-CF-Fog FL Module gossip extension
(see ``simulator/src/main/java/hu/u_szeged/inf/fog/simulator/util/{SplitMix64,SimRandom}.java``).

Pass-2 (Python harness) makes the authoritative peer-selection decisions in
the co-simulation bridge; Pass-3 (Java replay) must reproduce them bit-for-bit
so the recorded peer sets, payloads, and signatures are consumed unambiguously.
``java.util.Random`` and Python's ``random`` are NOT bit-compatible, so the
gossip stack uses this PRNG on both sides and the consumption-order contract
below to pin every stochastic decision.

Python ints are arbitrary-precision; Java ``long`` is 64-bit two's-complement.
Every additive/multiplicative step is masked to 64 bits to mimic the Java
overflow semantics exactly. Shifts use ``>>`` after the mask, which is the
logical (unsigned) shift on Python's non-negative ints (the equivalent of
Java's ``>>>`` operator).

The committed golden-vector tests (``tests/test_flrng_golden.py`` and the
JUnit counterpart) lock the wire format — drift on either side is a build
break.
"""
from __future__ import annotations

__all__ = ["SplitMix64", "derive", "MASK64", "GAMMA"]

MASK64 = 0xFFFFFFFFFFFFFFFF
"""64-bit unsigned mask. Applied after every ``+`` / ``*``."""

GAMMA = 0x9E3779B97F4A7C15
"""SplitMix64 increment (golden-ratio constant). Matches Java's ``SplitMix64.GAMMA``."""

_FIN_MUL_1 = 0xBF58476D1CE4E5B9
_FIN_MUL_2 = 0x94D049BB133111EB

# Per-decision stream-derivation constants (must match SimRandom.derive in Java).
_DERIVE_ROUND_CONST = 0xA24BAED4963EE407
_DERIVE_NODE_CONST = 0x9FB21C651E98DF25


def _mix(z: int) -> int:
    """SplitMix64 finalizer applied once to ``z``. Matches ``SplitMix64.mix``
    in Java exactly."""
    z = ((z ^ (z >> 30)) * _FIN_MUL_1) & MASK64
    z = ((z ^ (z >> 27)) * _FIN_MUL_2) & MASK64
    return z ^ (z >> 31)


class SplitMix64:
    """SplitMix64 PRNG (Steele, Lea & Flood, OOPSLA 2014).

    Constructed from a 64-bit seed (Python int, low 64 bits used). Not thread-safe.
    """

    __slots__ = ("_state",)

    def __init__(self, seed: int):
        self._state = seed & MASK64

    def next_long(self) -> int:
        """Return the next 64-bit unsigned value, in ``[0, 2**64)``.

        Counterpart of Java's ``nextLong()`` viewed as an unsigned bit pattern.
        """
        self._state = (self._state + GAMMA) & MASK64
        return _mix(self._state)

    def next_double(self) -> float:
        """Return a uniform float in ``[0.0, 1.0)``.

        Matches Java's ``(nextLong() >>> 11) * 0x1.0p-53`` exactly: the high 53
        bits of ``next_long()`` scaled by ``2**-53``.
        """
        return (self.next_long() >> 11) * (2.0 ** -53)

    def next_int(self, bound: int) -> int:
        """Return a uniform int in ``[0, bound)``.

        Uses the same rejection rule as the Java mirror. The check
        ``u + (bound - 1) - r < 2**63`` is the infinite-precision equivalent of
        Java's signed-overflow detection ``u - r + (bound - 1) >= 0L``.
        """
        if bound <= 0:
            raise ValueError(f"bound must be positive: {bound}")
        while True:
            u = self.next_long() & 0x7FFFFFFFFFFFFFFF  # low 63 bits == Java `& Long.MAX_VALUE`
            r = u % bound
            if u - r + (bound - 1) < (1 << 63):
                return r
            # rejected: ragged tail of the 63-bit space — redraw


def derive(seed: int, round_: int, node_id: int) -> SplitMix64:
    """Build a per-decision SplitMix64 stream from ``(seed, round, node_id)``.

    The mixing function and constants are identical to
    ``SimRandom.derive(long, int, int)`` in Java. ``node_id = -1`` is the
    convention for graph-level decisions (e.g. dynamic edge toggling); all
    other negative ids are reserved.

    See the consumption-order contract in the Java Javadoc and in
    ``docs/REPRODUCIBILITY.md``. Order of draws is the real cross-language
    hazard, not the PRNG itself.
    """
    h = seed & MASK64
    h = _mix(h ^ ((_DERIVE_ROUND_CONST * (round_ + 1)) & MASK64))
    h = _mix(h ^ ((_DERIVE_NODE_CONST * (node_id + 1)) & MASK64))
    return SplitMix64(h)
