#!/usr/bin/env python3
"""
PurpurFlow 真实压测汇总（第 3 阶段，修复 R-11）。

重要口径（诚实性）：
  * 本脚本不再自己估算字节数。真实编码字节由 Java 探针 BenchHarness.java 直接调用
    FrameCodec/Snapshot/Delta/DictSync 的真实实现产生，本脚本只做汇总与 CSV 落盘。
  * 有效逻辑字节与链路字节用同一定义（帧内 delta 有效载荷），payload_ratio <= 1，
    不再出现旧脚本 >1 的不合理值。
  * 延迟：本环境无真实 Netty 链路，只统计编码 CPU 耗时（nanoTime），
    P50/P99 链路延迟、丢包率、重连恢复时间标为「未验证」，见 06_live_demo.md。

用法（推荐：先编译 Java 探针，再把 TSV 喂进来）：
  javac -cp <classes> -d <classes> test/bench/BenchHarness.java
  java -cp <classes> BenchHarness 10000 20 25 > bench_real.tsv
  python3 bench.py --tsv bench_real.tsv --out bench_result.csv

也可直接手工给 TSV 十列（兼容无 --tsv 场景）。
"""
import argparse
import csv
import sys
from datetime import datetime, timezone

FIELDS = [
    "updates", "fields_per_update", "dirty_ratio_pct",
    "baseline_total_bytes", "optimized_total_bytes", "logical_payload_bytes",
    "baseline_packets", "optimized_packets",
    "baseline_encode_nanos", "optimized_encode_nanos",
]


def parse_tsv(path):
    with open(path, encoding="utf-8") as f:
        line = f.readline().strip()
    parts = line.split()
    if len(parts) < 10:
        raise ValueError(f"TSV 需至少 10 列，实际 {len(parts)}: {line}")
    vals = [int(x) for x in parts[:10]]
    return dict(zip(FIELDS, vals))


def derive(r):
    up = r["updates"]
    b, o, logical = r["baseline_total_bytes"], r["optimized_total_bytes"], r["logical_payload_bytes"]
    out = dict(r)
    out["byte_reduction_pct"] = round(100 * (b - o) / b, 2) if b else 0
    out["packet_reduction_pct"] = round(
        100 * (r["baseline_packets"] - r["optimized_packets"]) / r["baseline_packets"], 2
    ) if r["baseline_packets"] else 0
    # 有效载荷比：同一口径下应 <= 1
    out["baseline_payload_ratio"] = round(logical / b, 4) if b else 0
    out["optimized_payload_ratio"] = round(logical / o, 4) if o else 0
    out["baseline_bytes_per_update"] = round(b / up, 2)
    out["optimized_bytes_per_update"] = round(o / up, 2)
    out["baseline_encode_us_per_update"] = round(r["baseline_encode_nanos"] / up / 1000, 3)
    out["optimized_encode_us_per_update"] = round(r["optimized_encode_nanos"] / up / 1000, 3)
    out["measured_at_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    out["source"] = "BenchHarness.java (real FrameCodec/Snapshot/Delta encode)"
    # 未验证指标：明确留空标注，禁止编造
    out["p50_p99_link_latency_ms"] = "UNVERIFIED(no real network)"
    out["packet_loss_rate"] = "UNVERIFIED(no real network)"
    out["reconnect_recovery_ms"] = "UNVERIFIED(no real server)"
    out["cpu_heap_delta"] = "UNVERIFIED(no soak run)"
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", help="BenchHarness.java 输出的单行 TSV 文件")
    ap.add_argument("--inline", help="直接给 10 列 TSV 字符串")
    ap.add_argument("--out", default="bench_result.csv")
    args = ap.parse_args()

    if args.tsv:
        raw = parse_tsv(args.tsv)
    elif args.inline:
        vals = [int(x) for x in args.inline.split()[:10]]
        raw = dict(zip(FIELDS, vals))
    else:
        print("需提供 --tsv 或 --inline（本脚本不做估算）", file=sys.stderr)
        sys.exit(2)

    row = derive(raw)
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(row.keys()))
        w.writeheader()
        w.writerow(row)

    print(f"已输出 {args.out}")
    print(f"  字节: {row['baseline_total_bytes']} → {row['optimized_total_bytes']} "
          f"(↓{row['byte_reduction_pct']}%)")
    print(f"  包数: {row['baseline_packets']} → {row['optimized_packets']} "
          f"(↓{row['packet_reduction_pct']}%)")
    print(f"  有效载荷比: {row['baseline_payload_ratio']} → {row['optimized_payload_ratio']} (均<=1)")
    print(f"  每更新字节: {row['baseline_bytes_per_update']} → {row['optimized_bytes_per_update']}")


if __name__ == "__main__":
    main()
