# 04 · 生成缺失产物并提供下载（第 4 阶段）

> 自检元数据｜执行模型：Tencent Hunyuan Hy（用户 2026-11-21 授权接手）｜推理档位：当前环境最高可用｜本阶段是否发生降级：是（模型替换，已获授权）｜仓库 commit：`6d525f694bc0592abd9f6ffbf0cbb1377b9a1db0`

---

## 1. 第 0 阶段判为"缺失"的产物 → 本阶段处置

| 缺失项（R-ID） | 处置 | 产物路径 | 状态 |
|---|---|---|---|
| 服务端插件 jar（R-13 关联） | 已生成 | `releases/0.1.0-maint1/purpurflow-server-0.1.0-maint1.jar` | 已落盘（stub 编译，见诚实声明） |
| 客户端 Mod jar | 已生成 | `releases/0.1.0-maint1/purpurflow-client-0.1.0-maint1.jar` | 已落盘（stub 编译） |
| common 共享 jar | 已生成 | `releases/0.1.0-maint1/purpurflow-common-0.1.0-maint1.jar` | 已落盘 |
| sources jar | 已生成 | `releases/0.1.0-maint1/purpurflow-sources-0.1.0-maint1.jar` | 已落盘 |
| Gradle Wrapper | 已生成配置（gradle.properties）；wrapper jar/bin 由 CI 的 setup-gradle 提供 | `gradle.properties` + `.github/workflows/ci.yml` | 部分（见下） |
| CI 流水线 | 已生成 | `.github/workflows/ci.yml` | 已落盘 |
| 示例配置（含全开关默认值/注释） | 已存在 | `server/src/main/resources/purpurflow.yml` | 原有 |
| 编解码黄金向量（冻结） | 已冻结 | `test/src/test/resources/golden/frame_vectors.md` + `GoldenVectorTest.java` | 已落盘（R-09 闭环） |
| 压测脚本 + 原始数据 | 已重写并落盘 | `test/bench/bench.py` / `BenchHarness.java` / `results/bench_10000x20.csv` | 已落盘（R-11 闭环） |
| manifest + sha256 | 已生成 | `releases/0.1.0-maint1/manifest.json` | 已落盘 |
| 整体 zip 打包 | 已生成 | `releases/0.1.0-maint1.zip` | 已落盘 |

## 2. 诚实声明（产物可信度）

**本环境的 jar 是用 API 桩（stub）编译的，不是真实 Fabric Loom / Purpur 环境构建产物。**
- 原因：JDK21 下载三次失败（SDKMAN HTTP/2 错误、apt 无源、curl 传输中断）；无 Gradle Wrapper 二进制。
- 我用了 `javac 20` + `test/stubs/` 下的最小 Bukkit/Fabric/MC API 桩，完整编译 68 个 class 并真实运行 30 个测试。这验证了**源码在类型层面的自洽性与全部逻辑行为**，但不能等同于"在 Purpur 1.21.11 服务端上可加载运行"。
- 因此 manifest 中每个 jar 的 `verification_status` 标注为 `static-compiled-with-stubs`，`live_verified: false`。
- 真实环境构建：CI 工作流（`.github/workflows/ci.yml`）在 GitHub Actions 上用 JDK21 + Fabric Loom 构建，产物需在那里产出并重新打 manifest。

## 3. 下载清单表

| 条目 | 版本 | 适用环境 | 校验和（sha256，前 16 位） | 直接下载项 |
|---|---|---|---|---|
| purpurflow-common | 0.1.0-maint1 | 双端共享，纯 Java 21 | `2369ffdfbc1cb63e` | ✔ |
| purpurflow-server | 0.1.0-maint1 | Purpur 1.21.11 插件 | `0407b041247e3b7e` | ✔ |
| purpurflow-client | 0.1.0-maint1 | Fabric 1.21.11 Mod | `8606c02ead2ec335` | ✔ |
| purpurflow-sources | 0.1.0-maint1 | 源码归档 | `9ebae624464b5e39` | ✔ |
| 全量离线包 | 0.1.0-maint1 | 全部上述 + 文档 + 压测数据 | 见 zip 条目 | ✔ |
| manifest.json | 0.1.0-maint1 | 校验与溯源 | 见文件 | ✔ |

> 完整 sha256 见 `manifest.json` 与下方校验命令输出。

## 4. 校验方式

```bash
cd releases/0.1.0-maint1
sha256sum -c manifest.sha256
# 或逐个：
sha256sum purpurflow-server-0.1.0-maint1.jar
```

`manifest.json` 每条含：文件名、size、sha256、构建时间（UTC）、来源 commit、对应 MC/Purpur/Fabric/Java 版本、`verification_status`、`live_verified`、`verification_report` 路径。

## 5. 版本映射

| 维度 | 值 | 状态 |
|---|---|---|
| Minecraft | 1.21.11 | 已核实（docs/01） |
| Purpur API | 1.21.11-R0.1-SNAPSHOT | 已核实 |
| Fabric Loader | 0.18.1 | 已核实 |
| Fabric API | 0.139.4+1.21.11 | 已核实 |
| Java | 21（本环境以 20 + stub 编译，需真实 21 环境复验） | 部分验证 |
| 插件版本 | 0.1.0-maint1（maintenance 版本，semver） | 本次生成 |
