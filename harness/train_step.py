"""The shared local training step (§8.4 / D-shared-local-train).

``local_train`` is the ONE SGD inner loop called by every coordination mode
(centralized / hierarchical / gossip). The modes differ only in what they do
*between* local steps (aggregate / two-tier average / select-exchange-merge),
so ERQ1 compares coordination, not three accidentally-different trainers. The
one-node equivalence test asserts that on an isolated node all three modes
produce a byte-identical weight trajectory for the same seed.
"""
from __future__ import annotations

from dataclasses import dataclass

import torch
import torch.nn as nn

from data import Dataset


def round_train_seed(seed: int, round_: int, node: int) -> int:
    """Deterministic per-(seed, round, node) seed for the local fit. Shared by
    every mode so the one-node equivalence test sees an identical trajectory."""
    return (int(seed) * 1_000_003 + round_ * 1009 + node) & 0x7FFFFFFF


def set_determinism(seed: int) -> None:
    """Pins single-threaded, deterministic CPU execution (float32 end-to-end)."""
    torch.manual_seed(seed)
    torch.use_deterministic_algorithms(True, warn_only=True)
    torch.set_num_threads(1)


@dataclass
class TrainResult:
    loss: float
    acc: float
    delta_norm: float       # ‖w_after − w_before‖₂
    train_time_ms: float    # measured wall-clock for the local fit


def _flat(model: nn.Module) -> torch.Tensor:
    return torch.cat([p.detach().reshape(-1) for p in model.parameters()]).clone()


def local_train(model: nn.Module, data: Dataset, epochs: int, lr: float,
                batch_size: int, seed: int) -> TrainResult:
    """Runs ``epochs`` of SGD over ``data`` in place. Deterministic in ``seed``:
    the batch order is drawn from a dedicated CPU generator, so the trajectory
    depends only on (model init, data, epochs, lr, batch_size, seed)."""
    import time

    before = _flat(model)
    gen = torch.Generator()
    gen.manual_seed(seed)
    opt = torch.optim.SGD(model.parameters(), lr=lr)
    loss_fn = nn.CrossEntropyLoss()
    n = len(data)

    model.train()
    t0 = time.perf_counter()
    for _ in range(epochs):
        perm = torch.randperm(n, generator=gen)
        for start in range(0, n, batch_size):
            idx = perm[start:start + batch_size]
            xb = data.x[idx]
            yb = data.y[idx]
            opt.zero_grad()
            out = model(xb)
            loss = loss_fn(out, yb)
            loss.backward()
            opt.step()
    train_time_ms = (time.perf_counter() - t0) * 1000.0

    # Final metrics on the local data (deterministic eval).
    model.eval()
    with torch.no_grad():
        out = model(data.x)
        final_loss = float(loss_fn(out, data.y).item())
        pred = out.argmax(dim=1)
        acc = float((pred == data.y).float().mean().item())

    after = _flat(model)
    delta_norm = float(torch.linalg.vector_norm(after - before).item())
    return TrainResult(loss=final_loss, acc=acc, delta_norm=delta_norm, train_time_ms=train_time_ms)


def evaluate(model: nn.Module, data: Dataset) -> tuple[float, float]:
    """Deterministic (loss, accuracy) of ``model`` on ``data`` — the same eval
    basis as ``local_train``'s final metrics. Used for the §8.3 secondary view
    (accuracy of the uniformly averaged model w̄) and the A_cen reference."""
    loss_fn = nn.CrossEntropyLoss()
    model.eval()
    with torch.no_grad():
        out = model(data.x)
        loss = float(loss_fn(out, data.y).item())
        acc = float((out.argmax(dim=1) == data.y).float().mean().item())
    return loss, acc
