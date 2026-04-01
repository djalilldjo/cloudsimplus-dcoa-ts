#!/usr/bin/env python3
r"""Generate the revised DCOA-TS manuscript figures from CSV data.

Expected files inside --data-dir:
    raw_runs.csv
    param_sensitivity.csv
    weight_sensitivity.csv

Example:
    python generate_revision_figures.py --data-dir final_package_dcoa_ts/data --out-dir final_package_dcoa_ts/figures

If you want the generated PNGs to compile immediately with the current TeX files,
set --out-dir to the manuscript root (or add \graphicspath{{figures/}} in LaTeX).
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Dict, List, Sequence

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ALGORITHM_ORDER: List[str] = [
    "FCFS",
    "RoundRobin",
    "SJF",
    "PSO-SA",
    "DTSO-TS",
    "DCOA-TS",
    "DQN",
    "DGWO",
    "DBDE",
    "DWOA",
    "GO",
    "I-COA",
]

DISPLAY_NAME: Dict[str, str] = {
    "RoundRobin": "Round Robin",
}

ALGO_COLORS: Dict[str, str] = {
    "FCFS": "#4C78A8",
    "RoundRobin": "#F28E2B",
    "SJF": "#59A14F",
    "PSO-SA": "#9C755F",
    "DTSO-TS": "#76B7B2",
    "DCOA-TS": "#E41A1C",
    "DQN": "#9467BD",
    "DGWO": "#E15759",
    "DBDE": "#8C564B",
    "DWOA": "#F28E9C",
    "GO": "#E5C349",
    "I-COA": "#BAB0AC",
}

ALGO_MARKERS: Dict[str, str] = {
    "FCFS": "o",
    "RoundRobin": "s",
    "SJF": "^",
    "PSO-SA": "D",
    "DTSO-TS": "v",
    "DCOA-TS": "P",
    "DQN": "X",
    "DGWO": ">",
    "DBDE": "<",
    "DWOA": "h",
    "GO": "*",
    "I-COA": "p",
}

# The final manuscript annotations show the 100k energy bars in MJ, which is more readable.
BAR_ENERGY_IN_MJ = True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate revised manuscript figures from CSV files.")
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("."),
        help="Directory containing raw_runs.csv, param_sensitivity.csv, and weight_sensitivity.csv.",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("figures"),
        help="Directory where the PNG figures will be written.",
    )
    return parser.parse_args()


# -----------------------------
# Utilities
# -----------------------------

def setup_style() -> None:
    plt.rcParams.update(
        {
            "font.family": "DejaVu Sans",
            "axes.titlesize": 18,
            "axes.labelsize": 16,
            "xtick.labelsize": 12,
            "ytick.labelsize": 12,
            "legend.fontsize": 12,
            "figure.dpi": 200,
            "savefig.dpi": 300,
            "axes.grid": True,
            "grid.alpha": 0.25,
            "grid.linestyle": "-",
        }
    )


def ensure_files(data_dir: Path, required: Sequence[str]) -> None:
    missing = [name for name in required if not (data_dir / name).exists()]
    if missing:
        missing_str = ", ".join(missing)
        raise FileNotFoundError(f"Missing required file(s) in {data_dir}: {missing_str}")



def save(fig: plt.Figure, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, bbox_inches="tight")
    plt.close(fig)
    print(f"[saved] {out_path}")



def format_cloudlet_label(n: int) -> str:
    if n >= 1000 and n % 1000 == 0:
        return f"{n // 1000}k"
    return f"{n:,}"



def nice_algo_name(algo: str) -> str:
    return DISPLAY_NAME.get(algo, algo)



def mean_by_algorithm_and_size(raw_runs: pd.DataFrame, metric: str) -> pd.DataFrame:
    return (
        raw_runs.groupby(["algorithm", "num_cloudlets"], as_index=False)[metric]
        .mean()
        .rename(columns={metric: "value"})
    )



def get_metric_series(grouped: pd.DataFrame, algorithm: str, sizes: Sequence[int]) -> List[float]:
    sub = grouped[grouped["algorithm"] == algorithm].set_index("num_cloudlets")
    return [float(sub.loc[size, "value"]) for size in sizes if size in sub.index]



def choose_text_color(value: float, vmin: float, vmax: float, threshold: float = 0.55) -> str:
    if vmax <= vmin:
        return "black"
    norm = (value - vmin) / (vmax - vmin)
    return "white" if norm >= threshold else "black"


# -----------------------------
# Main paper figures
# -----------------------------

def plot_metric_vs_cloudlets(
    raw_runs: pd.DataFrame,
    metric: str,
    title: str,
    ylabel: str,
    out_path: Path,
    sizes: Sequence[int],
) -> None:
    grouped = mean_by_algorithm_and_size(raw_runs, metric)

    fig, ax = plt.subplots(figsize=(15.5, 8.5))
    x = np.arange(len(sizes))

    for algo in ALGORITHM_ORDER:
        sub = grouped[(grouped["algorithm"] == algo) & (grouped["num_cloudlets"].isin(sizes))].copy()
        if sub.empty:
            continue
        sub = sub.sort_values("num_cloudlets")
        xpos = [x[list(sizes).index(int(n))] for n in sub["num_cloudlets"]]
        ax.plot(
            xpos,
            sub["value"],
            label=nice_algo_name(algo),
            color=ALGO_COLORS.get(algo),
            marker=ALGO_MARKERS.get(algo, "o"),
            linewidth=3.0 if algo == "DCOA-TS" else 2.0,
            markersize=8 if algo == "DCOA-TS" else 6,
        )

    ax.set_title(title)
    ax.set_xlabel("Number of cloudlets")
    ax.set_ylabel(ylabel)
    ax.set_xticks(x)
    ax.set_xticklabels([format_cloudlet_label(n) for n in sizes])
    ax.set_yscale("log")
    ax.grid(True, which="major", alpha=0.25)
    ax.grid(False, which="minor")
    ax.legend(ncol=4, loc="upper center", bbox_to_anchor=(0.5, -0.18), frameon=False)
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    save(fig, out_path)



def plot_bar_for_100k(
    raw_runs: pd.DataFrame,
    metric: str,
    title: str,
    ylabel: str,
    out_path: Path,
    formatter,
    value_transform=lambda x: x,
    annotate_transform=lambda x: x,
    annotate_fmt=lambda x: f"{x:,.0f}",
) -> None:
    grouped = mean_by_algorithm_and_size(raw_runs, metric)
    sub = grouped[grouped["num_cloudlets"] == 100000].copy()
    sub["plot_value"] = sub["value"].map(value_transform)
    sub["annotate_value"] = sub["value"].map(annotate_transform)
    sub = sub.sort_values("plot_value", ascending=True)

    fig, ax = plt.subplots(figsize=(14.0, 7.0))
    colors = [ALGO_COLORS.get(algo, "#4C78A8") for algo in sub["algorithm"]]
    bars = ax.bar([nice_algo_name(a) for a in sub["algorithm"]], sub["plot_value"], color=colors)

    ax.set_title(title)
    ax.set_ylabel(ylabel)
    ax.tick_params(axis="x", rotation=35)
    for label in ax.get_xticklabels():
        label.set_ha("right")
    ax.yaxis.set_major_formatter(formatter)
    ax.grid(True, axis="y", alpha=0.25)

    ymax = float(sub["plot_value"].max())
    offset = ymax * 0.01
    for bar, ann_value in zip(bars, sub["annotate_value"]):
        ax.text(
            bar.get_x() + bar.get_width() / 2.0,
            bar.get_height() + offset,
            annotate_fmt(float(ann_value)),
            ha="center",
            va="bottom",
            fontsize=12,
        )

    save(fig, out_path)


# -----------------------------
# Parameter sensitivity heatmaps
# -----------------------------

def _heatmap_panel_data(
    param_df: pd.DataFrame,
    algorithm: str,
    metric: str,
    populations: Sequence[int],
    iterations: Sequence[int],
) -> np.ndarray:
    sub = (
        param_df[param_df["algorithm"] == algorithm]
        .groupby(["population", "iterations"], as_index=False)[metric]
        .mean()
    )
    pivot = sub.pivot(index="population", columns="iterations", values=metric)
    pivot = pivot.reindex(index=populations, columns=iterations)
    return pivot.to_numpy(dtype=float)



def plot_heatmaps(
    param_df: pd.DataFrame,
    metric: str,
    colorbar_label: str,
    out_path: Path,
    value_fmt: str,
) -> None:
    algorithms = ["PSO-SA", "DTSO-TS", "DCOA-TS"]
    populations = sorted(param_df["population"].unique())
    iterations = sorted(param_df["iterations"].unique())

    matrices = {
        algo: _heatmap_panel_data(param_df, algo, metric, populations, iterations)
        for algo in algorithms
    }

    all_values = np.concatenate([m.flatten() for m in matrices.values()])
    all_values = all_values[np.isfinite(all_values)]
    vmin = float(all_values.min())
    vmax = float(all_values.max())
    if np.isclose(vmin, vmax):
        vmin -= 0.05
        vmax += 0.05

    fig, axes = plt.subplots(1, 3, figsize=(18, 5.4), sharey=True, constrained_layout=True)
    ims = []

    for idx, (ax, algo) in enumerate(zip(axes, algorithms)):
        mat = matrices[algo]
        im = ax.imshow(mat, aspect="auto", cmap="viridis_r", vmin=vmin, vmax=vmax)
        ims.append(im)
        ax.set_title(algo)
        ax.set_xticks(np.arange(len(iterations)))
        ax.set_xticklabels([str(i) for i in iterations])
        ax.set_xlabel("Iterations")
        ax.set_yticks(np.arange(len(populations)))
        ax.set_yticklabels([str(p) for p in populations])
        ax.tick_params(labelleft=True)
        if idx == 0:
            ax.set_ylabel("Population")

        for i in range(mat.shape[0]):
            for j in range(mat.shape[1]):
                val = float(mat[i, j])
                ax.text(
                    j,
                    i,
                    format(val, value_fmt),
                    ha="center",
                    va="center",
                    color=choose_text_color(val, vmin, vmax),
                    fontsize=10,
                )

    cbar = fig.colorbar(ims[-1], ax=axes, fraction=0.025, pad=0.02)
    cbar.set_label(colorbar_label)
    save(fig, out_path)


# -----------------------------
# Weight-sensitivity figure
# -----------------------------

def plot_weight_sensitivity(weight_df: pd.DataFrame, out_path: Path) -> None:
    # Keys are (w_makespan, w_energy, w_violation)
    configs = [
        ((0.10, 0.80, 0.10), "Energy-priority ($w_1 = 0.1, w_2 = 0.8, w_3 = 0.1)$", "#1b9e77"),
        ((0.45, 0.45, 0.10), "Balanced ($w_1 = 0.45, w_2 = 0.45, w_3 = 0.1)$", "#7570b3"),
        ((0.70, 0.20, 0.10), "Makespan-priority / default ($w_1 = 0.7, w_2 = 0.2, w_3 = 0.1)$", "#d95f02"),
    ]

    grouped = (
        weight_df.groupby(["w_makespan", "w_energy", "w_violation", "num_cloudlets"], as_index=False)[
            ["makespan_sec", "total_energy_j"]
        ]
        .mean()
    )

    sizes = sorted(grouped["num_cloudlets"].unique())
    x = np.arange(len(sizes))

    fig, axes = plt.subplots(1, 2, figsize=(18, 7), sharex=True)
    handles = []

    for key, label, color in configs:
        wm, we, wv = key
        sub = grouped[
            np.isclose(grouped["w_makespan"], wm)
            & np.isclose(grouped["w_energy"], we)
            & np.isclose(grouped["w_violation"], wv)
        ].sort_values("num_cloudlets")

        h0 = axes[0].plot(x, sub["makespan_sec"], marker="o", linewidth=2.2, color=color, label=label)[0]
        axes[1].plot(x, sub["total_energy_j"], marker="o", linewidth=2.2, color=color, label=label)
        handles.append(h0)

    axes[0].set_title("Makespan trade-off")
    axes[0].set_ylabel("Makespan (s)")
    axes[0].set_yscale("log")

    axes[1].set_title("Energy trade-off")
    axes[1].set_ylabel("Total energy (J)")
    axes[1].set_yscale("log")

    for ax in axes:
        ax.set_xlabel("Number of cloudlets")
        ax.set_xticks(x)
        ax.set_xticklabels([format_cloudlet_label(int(n)) for n in sizes])
        ax.grid(True, which="major", alpha=0.25)
        ax.grid(False, which="minor")

    fig.legend(handles=handles, loc="lower center", ncol=1, frameon=False, bbox_to_anchor=(0.5, -0.05))
    fig.tight_layout(rect=(0, 0.1, 1, 1))
    save(fig, out_path)


# -----------------------------
# Entry point
# -----------------------------

def main() -> None:
    args = parse_args()
    setup_style()

    ensure_files(args.data_dir, ["raw_runs.csv", "param_sensitivity.csv", "weight_sensitivity.csv"])

    raw_runs = pd.read_csv(args.data_dir / "raw_runs.csv")
    param_df = pd.read_csv(args.data_dir / "param_sensitivity.csv")
    weight_df = pd.read_csv(args.data_dir / "weight_sensitivity.csv")

    # Main manuscript line plots (up to 5,000 cloudlets)
    main_sizes = [50, 100, 500, 1000, 2000, 5000]
    plot_metric_vs_cloudlets(
        raw_runs,
        metric="makespan_sec",
        title="Makespan across workload sizes",
        ylabel="Makespan (s)",
        out_path=args.out_dir / "makespan_vs_cloudlets_high_res.png",
        sizes=main_sizes,
    )
    plot_metric_vs_cloudlets(
        raw_runs,
        metric="total_energy_j",
        title="Energy consumption across workload sizes",
        ylabel="Total energy consumption (J)",
        out_path=args.out_dir / "total_energy_vs_cloudlets_high_res.png",
        sizes=main_sizes,
    )
    plot_metric_vs_cloudlets(
        raw_runs,
        metric="avg_waiting_sec",
        title="Average waiting time across workload sizes",
        ylabel="Average waiting time (s)",
        out_path=args.out_dir / "avg_waiting_time_vs_cloudlets_high_res.png",
        sizes=main_sizes,
    )

    # 100,000-cloudlet bar charts
    plot_bar_for_100k(
        raw_runs,
        metric="makespan_sec",
        title="Makespan at 100,000 cloudlets",
        ylabel="Makespan (s)",
        out_path=args.out_dir / "makespan_bar_100000_cloudlets_high_res.png",
        formatter=plt.FuncFormatter(lambda x, _: f"{x:,.0f}"),
        annotate_fmt=lambda x: f"{x:,.0f}",
    )

    if BAR_ENERGY_IN_MJ:
        plot_bar_for_100k(
            raw_runs,
            metric="total_energy_j",
            title="Energy consumption at 100,000 cloudlets",
            ylabel="Total energy consumption (MJ)",
            out_path=args.out_dir / "total_energy_bar_100000_cloudlets_high_res.png",
            formatter=plt.FuncFormatter(lambda x, _: f"{x:,.1f}"),
            value_transform=lambda x: x / 1e6,
            annotate_transform=lambda x: x / 1e6,
            annotate_fmt=lambda x: f"{x:.2f}",
        )
    else:
        plot_bar_for_100k(
            raw_runs,
            metric="total_energy_j",
            title="Energy consumption at 100,000 cloudlets",
            ylabel="Total energy consumption (J)",
            out_path=args.out_dir / "total_energy_bar_100000_cloudlets_high_res.png",
            formatter=plt.FuncFormatter(lambda x, _: f"{x/1e6:.1f}"),
            value_transform=lambda x: x,
            annotate_transform=lambda x: x / 1e6,
            annotate_fmt=lambda x: f"{x:.2f}",
        )

    plot_bar_for_100k(
        raw_runs,
        metric="avg_waiting_sec",
        title="Average waiting time at 100,000 cloudlets",
        ylabel="Average waiting time (s)",
        out_path=args.out_dir / "avg_waiting_time_bar_100000_cloudlets_high_res.png",
        formatter=plt.FuncFormatter(lambda x, _: f"{x:,.0f}"),
        annotate_fmt=lambda x: f"{x:,.0f}",
    )

    # Parameter-sensitivity heatmaps
    plot_heatmaps(
        param_df,
        metric="makespan_sec",
        colorbar_label="Makespan (s)",
        out_path=args.out_dir / "Makespan_comparison_with_units.png",
        value_fmt=".1f",
    )
    plot_heatmaps(
        param_df,
        metric="total_energy_j",
        colorbar_label="Total energy (J)",
        out_path=args.out_dir / "TotalEnergyConsumption_comparison_with_units.png",
        value_fmt=".1f",
    )
    plot_heatmaps(
        param_df,
        metric="avg_waiting_sec",
        colorbar_label="Average waiting time (s)",
        out_path=args.out_dir / "AvgWaitingTime_comparison_with_units.png",
        value_fmt=".2f",
    )

    # Weight-sensitivity figure
    plot_weight_sensitivity(weight_df, args.out_dir / "fig_weight_sensitivity.png")


if __name__ == "__main__":
    main()
