from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt

BASE_DIR = Path("sim_res")
OUT_DIR = BASE_DIR / "figures"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def load_all(filename: str) -> pd.DataFrame:
    files = list(BASE_DIR.glob(f"*/{filename}"))
    if not files:
        raise FileNotFoundError(f"No {filename} files found under {BASE_DIR}")

    frames = []
    for file in files:
        df = pd.read_csv(file, low_memory=False)
        frames.append(df)

    return pd.concat(frames, ignore_index=True)


def normalize_deadline_met(series: pd.Series) -> pd.Series:
    return (
        series.astype(str)
        .str.strip()
        .str.lower()
        .map({
            "true": "met",
            "false": "missed",
            "1": "met",
            "0": "missed"
        })
    )


# ---------- LOAD DATA ----------

task_df = load_all("task_statistics.csv")
offload_df = load_all("offloading_statistics.csv")
run_df = load_all("run_statistics.csv")


# ---------- CLEAN TASK DATA ----------

task_df["deadlineStatus"] = normalize_deadline_met(task_df["deadlineMet"])
task_df["e2eLatency"] = pd.to_numeric(task_df["e2eLatency"], errors="coerce")
task_df["deviceCount"] = task_df["deviceCount"].astype(str)

task_df = task_df.dropna(subset=["e2eLatency", "deadlineStatus"])
task_df["e2eLatencySec"] = task_df["e2eLatency"] / 1000.0


# ---------- 1. E2E LATENCY BOXPLOT ----------

task_df["label"] = task_df["strategy"] + " (" + task_df["deviceCount"] + " device)"
labels = sorted(task_df["label"].dropna().unique())
data = [task_df[task_df["label"] == label]["e2eLatencySec"] for label in labels]

plt.figure(figsize=(12, 5))
plt.boxplot(data, tick_labels=labels, showmeans=True)
plt.ylabel("E2E latency (s)")
plt.title("E2E latency eloszlása")
plt.xticks(rotation=30, ha="right")
plt.grid(axis="y", alpha=0.3)
plt.tight_layout()
plt.savefig(OUT_DIR / "e2e_latency_boxplot.png", dpi=300)
plt.close()


# ---------- 2. E2E SUMMARY TABLE ----------

summary = task_df.groupby(["strategy", "deviceCount"])["e2eLatencySec"].agg(
    average="mean",
    median="median",
    p95=lambda x: x.quantile(0.95),
    p99=lambda x: x.quantile(0.99),
    count="count"
).reset_index()

summary.to_csv(OUT_DIR / "e2e_latency_summary.csv", index=False)


# ---------- 3. DEADLINE STACKED BAR ----------

deadline_summary = (
    task_df.groupby(["strategy", "deviceCount", "deadlineStatus"])
    .size()
    .unstack(fill_value=0)
)

for col in ["met", "missed"]:
    if col not in deadline_summary.columns:
        deadline_summary[col] = 0

deadline_summary = deadline_summary[["met", "missed"]].reset_index()
deadline_summary["label"] = (
        deadline_summary["strategy"] + " (" + deadline_summary["deviceCount"] + " device)"
)

plt.figure(figsize=(12, 5))
plt.bar(deadline_summary["label"], deadline_summary["met"], label="Határidőn belül")
plt.bar(
    deadline_summary["label"],
    deadline_summary["missed"],
    bottom=deadline_summary["met"],
    label="Határidőn túl"
)
plt.ylabel("Taskok száma")
plt.title("Deadline teljesülési arány")
plt.xticks(rotation=30, ha="right")
plt.legend()
plt.tight_layout()
plt.savefig(OUT_DIR / "deadline_ratio_stacked_bar.png", dpi=300)
plt.close()

deadline_summary["deadlineHitRatio"] = (
        deadline_summary["met"] /
        (deadline_summary["met"] + deadline_summary["missed"])
)

deadline_summary.rename(columns={
    "met": "deadlineMet",
    "missed": "deadlineMissed"
}, inplace=True)

deadline_summary[[
    "strategy",
    "deviceCount",
    "deadlineMet",
    "deadlineMissed",
    "deadlineHitRatio"
]].to_csv(OUT_DIR / "deadline_ratio_summary.csv", index=False)


# ---------- 4. OFFLOADING RATIO STACKED BAR ----------

offload_df["deviceCount"] = offload_df["deviceCount"].astype(str)
offload_df["targetLayer"] = offload_df["targetLayer"].astype(str).str.strip().str.upper()
offload_df["label"] = offload_df["strategy"] + " (" + offload_df["deviceCount"] + " device)"

offload_summary = (
    offload_df.groupby(["label", "targetLayer"])
    .size()
    .unstack(fill_value=0)
)

for col in ["LOCAL", "FOG", "CLOUD"]:
    if col not in offload_summary.columns:
        offload_summary[col] = 0

offload_summary = offload_summary[["LOCAL", "FOG", "CLOUD"]]

plt.figure(figsize=(12, 5))
bottom = None

for col in ["LOCAL", "FOG", "CLOUD"]:
    if bottom is None:
        plt.bar(offload_summary.index, offload_summary[col], label=col)
        bottom = offload_summary[col].copy()
    else:
        plt.bar(offload_summary.index, offload_summary[col], bottom=bottom, label=col)
        bottom += offload_summary[col]

plt.ylabel("Taskok száma")
plt.title("Lokális, köd és felhő feldolgozási arány")
plt.xticks(rotation=30, ha="right")
plt.legend()
plt.tight_layout()
plt.savefig(OUT_DIR / "offloading_ratio_stacked_bar.png", dpi=300)
plt.close()

offload_summary.to_csv(OUT_DIR / "offloading_ratio_summary.csv")


# ---------- 5. RUNTIME BAR ----------

run_df["deviceCount"] = run_df["deviceCount"].astype(str)
run_df["runtimeMs"] = pd.to_numeric(run_df["runtimeMs"], errors="coerce")
run_df["label"] = run_df["strategy"] + " (" + run_df["deviceCount"] + " device)"

plt.figure(figsize=(12, 5))
plt.bar(run_df["label"], run_df["runtimeMs"] / 1000.0)
plt.ylabel("Futási idő (s)")
plt.title("Szimulációk futási ideje")
plt.xticks(rotation=30, ha="right")
plt.tight_layout()
plt.savefig(OUT_DIR / "runtime_bar.png", dpi=300)
plt.close()


# ---------- 6. MEMORY BAR ----------

run_df["usedMemoryMb"] = pd.to_numeric(run_df["usedMemoryMb"], errors="coerce")

plt.figure(figsize=(12, 5))
plt.bar(run_df["label"], run_df["usedMemoryMb"])
plt.ylabel("Memóriahasználat (MB)")
plt.title("Szimulációk memóriahasználata")
plt.xticks(rotation=30, ha="right")
plt.tight_layout()
plt.savefig(OUT_DIR / "memory_bar.png", dpi=300)
plt.close()


# ---------- DEBUG OUTPUT ----------

print("Strategies in task statistics:", task_df["strategy"].unique())

print("\nDeadline counts:")
print(task_df.groupby(["strategy", "deviceCount", "deadlineStatus"]).size())

print("\nÁbrák elkészültek:", OUT_DIR)