"""Models (§9 workloads): LeNet-5 (MNIST / the synthetic canary set) and a
compact CIFAR-10 CNN. Parameter counts are reported explicitly because the
model payload size drives every traffic/energy figure in the study."""
from __future__ import annotations

import torch
import torch.nn as nn


class LeNet5(nn.Module):
    """LeNet-5 for 1×28×28 inputs (≈62k params, ≈0.25 MB float32)."""

    def __init__(self, num_classes: int = 10, in_channels: int = 1):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(in_channels, 6, kernel_size=5, padding=2), nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(6, 16, kernel_size=5), nn.ReLU(),
            nn.MaxPool2d(2),
        )
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Linear(16 * 5 * 5, 120), nn.ReLU(),
            nn.Linear(120, 84), nn.ReLU(),
            nn.Linear(84, num_classes),
        )

    def forward(self, x):
        return self.classifier(self.features(x))


class Cifar10Cnn(nn.Module):
    """Compact CIFAR-10 CNN (~1M params, ~4.1 MB float32) — cifar10_cnn_v1."""

    def __init__(self, num_classes: int = 10):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(3, 32, 3, padding=1), nn.ReLU(),
            nn.Conv2d(32, 64, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(64, 128, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
        )
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Linear(128 * 8 * 8, 128), nn.ReLU(),
            nn.Linear(128, num_classes),
        )

    def forward(self, x):
        return self.classifier(self.features(x))


_REGISTRY = {
    "lenet5": lambda nc=10, ch=1: LeNet5(num_classes=nc, in_channels=ch),
    "cifar10_cnn_v1": lambda nc=10, ch=3: Cifar10Cnn(num_classes=nc),
}


def build_model(name: str, num_classes: int = 10, in_channels: int = 1) -> nn.Module:
    if name not in _REGISTRY:
        raise ValueError(f"unknown model: {name}")
    if name == "lenet5":
        return _REGISTRY[name](num_classes, in_channels)
    return _REGISTRY[name](num_classes)


def param_count(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())


def flatten_params(model: nn.Module) -> torch.Tensor:
    """Flattened float32 parameter vector (for signatures)."""
    return torch.cat([p.detach().reshape(-1) for p in model.parameters()]).to(torch.float32)


def load_flat_params(model: nn.Module, flat: torch.Tensor) -> None:
    """Loads a flattened vector back into the model parameters."""
    offset = 0
    with torch.no_grad():
        for p in model.parameters():
            n = p.numel()
            p.copy_(flat[offset:offset + n].reshape(p.shape))
            offset += n
