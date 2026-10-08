#!/usr/bin/env python3
"""Bikin grafik dari hasil eksperimen.

Jalankan dari root repo (folder yang sama dengan pom.xml):
    python3 plot.py                         # baca hasil.csv dan hasil_sintetis.csv (yang ada saja)
    python3 plot.py a.csv b.csv             # atau tentukan file sendiri

Output:
    grafik/<tipe>_<metrik>.png   (X = jumlah task, Y = metrik, 1 garis per algoritma,
                                  error bar = std antar run)
    ringkasan_rata2.csv          (rata-rata dan std per dataset x algoritma)
"""
import os
import sys

import matplotlib
matplotlib.use("Agg")  # tanpa GUI, aman di terminal
import matplotlib.pyplot as plt
import pandas as pd

CSVS = sys.argv[1:] or ["hasil.csv", "hasil_sintetis.csv"]
OUT = "grafik"

METRICS = {
    "makespan":   "Makespan (detik)",
    "di":         "Degree of Imbalance",
    "ru":         "Resource Utilization (%)",
    "throughput": "Throughput (task/detik)",
    "art":        "Avg. Response Time (detik)",
    "sched_ms":   "Waktu scheduling (ms)",
}
DATASETS = [("gocj", "GoCJ"), ("sintetis", "Sintetis")]

ada = [f for f in CSVS if os.path.exists(f)]
if not ada:
    sys.exit(f"Tidak ada file CSV ({', '.join(CSVS)}). Jalankan dulu java -jar target/soka-simulasi.jar --eksperimen / --sintetis")
for f in CSVS:
    if f not in ada:
        print(f"[skip] {f} tidak ditemukan")

df = pd.concat([pd.read_csv(f) for f in ada], ignore_index=True)
os.makedirs(OUT, exist_ok=True)

ringkasan = []
for dtype, label in DATASETS:
    d = df[df["dataset_type"] == dtype]
    if d.empty:
        print(f"[skip] tidak ada data untuk dataset {label}")
        continue

    # rata-rata dan std antar run, per (jumlah task, algoritma)
    agg = d.groupby(["size", "algorithm"])[list(METRICS)].agg(["mean", "std"])
    agg.columns = [f"{m}_{s}" for m, s in agg.columns]
    agg = agg.reset_index()
    agg.insert(0, "dataset_type", dtype)
    ringkasan.append(agg)

    for col, ylabel in METRICS.items():
        plt.figure(figsize=(7.5, 4.8))
        for algo, g in agg.groupby("algorithm"):
            g = g.sort_values("size")
            plt.errorbar(g["size"], g[f"{col}_mean"], yerr=g[f"{col}_std"].fillna(0),
                         marker="o", capsize=3, linewidth=1.8, label=algo)
        plt.xlabel(f"Jumlah task ({label})")
        plt.ylabel(ylabel)
        plt.title(f"{ylabel} vs jumlah task, dataset {label}")
        plt.grid(alpha=0.3)
        plt.legend()
        plt.tight_layout()
        path = os.path.join(OUT, f"{dtype}_{col}.png")
        plt.savefig(path, dpi=200)
        plt.close()
        print(f"[OK] {path}")

if ringkasan:
    pd.concat(ringkasan).to_csv("ringkasan_rata2.csv", index=False, float_format="%.4f")
    print("[OK] ringkasan_rata2.csv")