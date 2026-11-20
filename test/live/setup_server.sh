#!/usr/bin/env bash
# 可复现试机环境脚本（Purpur 1.21.11 服务端 + 插件）
# 用法：在有 Java 21 + 网络的机器上执行：bash test/live/setup_server.sh
# 本环境（沙箱）无 JDK21/网络受限，未能实跑；此脚本供真实环境复现，见 06_live_demo.md。
set -euo pipefail

MC_VERSION="1.21.11"
BUILD="#2568"                       # 记录构建号，便于回滚（不静默用 latest）
JAVA_REQUIRED=21
SERVER_DIR="./live-server"
PLUGIN_JAR="../releases/0.1.0-maint1/purpurflow-server-0.1.0-maint1.jar"

echo "== 校验 Java =="
java -version 2>&1 | head -1
JVER=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JVER" != "$JAVA_REQUIRED" ]; then
  echo "需要 Java $JAVA_REQUIRED，当前 $JVER。请先安装 Temurin 21。"; exit 1
fi

echo "== 下载 Purpur $MC_VERSION build $BUILD =="
mkdir -p "$SERVER_DIR" && cd "$SERVER_DIR"
if [ ! -f purpur.jar ]; then
  curl -fL -o purpur.jar \
    "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/${BUILD#\#}/download"
fi

echo "== 首次启动生成 eula.txt =="
java -Xms2G -Xmx2G -jar purpur.jar --nogui || true
echo "请编辑 eula.txt 设 eula=true 后再次运行本脚本的启动步骤。"

echo "== 安装插件 =="
mkdir -p plugins
if [ -f "$PLUGIN_JAR" ]; then cp "$PLUGIN_JAR" plugins/; fi

echo "== 准备完成。启动：java -Xms2G -Xmx2G -jar purpur.jar --nogui =="
echo "== 观察：Done / 插件加载日志 / 端口监听（应仅 25565，验证 H1）=="
echo "== 客户端：Fabric 1.21.11 + Loader 0.18.1 + API 0.139.4 装 Mod；另备 vanilla 1.21.11 做回归 =="
