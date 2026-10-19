#!/usr/bin/env bash
# 让每个 JSON 库各占一个干净的 JVM 跑，避免同一进程里 JIT 互相污染。
# 用法： ./gradlew benchClasspath && tools/bench.sh
set -euo pipefail
cd "$(dirname "$0")/.."
CP="$(cat build/bench-cp.txt)"
for lib in strapper jackson gson fastjson2; do
  java -cp "$CP" top.x0a.strapper.bench.JsonBench "$lib"
done
