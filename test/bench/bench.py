#!/usr/bin/env python3
"""
PurpurFlow 压测口径脚本（第 4 阶段）。
模拟 N 条逻辑更新，对比「逐字段基线」与「本方案：delta+脏位图+字典+合包」的
总字节数、包数、有效载荷比、每逻辑更新平均字节。输出 CSV 供前后对照。

用法: python3 bench.py --updates 1000 --fields 20 --out bench_result.csv
"""
import argparse
import csv
import random

# 基线：每个字段一个 JSON 包
def baseline_bytes(fields):
    total = 0
    for i in range(fields):
        # {"field_12":12345} + 通道/包头摊销估计 40B
        total += len('{"field_' + str(i) + '":' + str(random.randint(0, 999)) + '}') + 40
    return total

# 本方案：脏位图(8B) + 每变更字段 varint(~2B) + 帧头(~20B)，多消息合包
def optimized_bytes(fields, dirty_ratio=0.25, compress=False):
    dirty = max(1, int(fields * dirty_ratio))
    frame = 20 + 8  # 帧头 + 脏位图(u64)
    frame += dirty * 2  # ZigZag varint 变更值
    if compress:
        frame = int(frame * 0.85)  # 骨架估算，真实以实测 A/B 为准
    return frame

def run(updates, fields, compress):
    base_total = 0
    opt_total = 0
    base_pkts = 0
    opt_pkts = 0
    logical_bytes = 0
    for _ in range(updates):
        base_total += baseline_bytes(fields)
        base_pkts += fields                 # 每字段一个包
        opt_total += optimized_bytes(fields, compress=compress)
        opt_pkts += 1                       # 合包：一帧
        # 有效逻辑信息量：每字段真实值按 varint ~2B（示意假设）。
        # 注意：本脚本为口径演示，delta 只传变更字段，故比值可能>1；
        # 真实有效载荷比须用抓包工具测「有效逻辑字节/链路总字节」，见 docs/06。
        logical_bytes += fields * 2

    def payload_ratio(total):
        return logical_bytes / total if total else 0

    return {
        "updates": updates,
        "fields_per_update": fields,
        "baseline_total_bytes": base_total,
        "optimized_total_bytes": opt_total,
        "byte_reduction_pct": round(100 * (base_total - opt_total) / base_total, 2),
        "baseline_packets": base_pkts,
        "optimized_packets": opt_pkts,
        "packet_reduction_pct": round(100 * (base_pkts - opt_pkts) / base_pkts, 2),
        "baseline_payload_ratio": round(payload_ratio(base_total), 4),
        "optimized_payload_ratio": round(payload_ratio(opt_total), 4),
        "baseline_bytes_per_update": round(base_total / updates, 2),
        "optimized_bytes_per_update": round(opt_total / updates, 2),
        "compression": compress,
    }

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--updates", type=int, default=1000)
    ap.add_argument("--fields", type=int, default=20)
    ap.add_argument("--compress", action="store_true")
    ap.add_argument("--out", default="bench_result.csv")
    args = ap.parse_args()

    rows = [run(args.updates, args.fields, False)]
    if args.compress:
        rows.append(run(args.updates, args.fields, True))

    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=rows[0].keys())
        w.writeheader()
        w.writerows(rows)

    print(f"已输出 {args.out}")
    for r in rows:
        print(f"  字节下降 {r['byte_reduction_pct']}% / 包数下降 {r['packet_reduction_pct']}% "
              f"/ 有效载荷比 {r['baseline_payload_ratio']}→{r['optimized_payload_ratio']}")

if __name__ == "__main__":
    main()
