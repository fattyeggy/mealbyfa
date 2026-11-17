#!/usr/bin/env bash
# 统一工具链入口：便携 JDK 17 + Maven 3.10，均位于 sancan-coach/tools/
# 用法： source scripts/env.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SANAN_ROOT="$ROOT_DIR"

# macOS 的 Adoptium 压缩包解压后 JDK_HOME 在 Contents/Home 下
if [ -d "$ROOT_DIR/tools/jdk-17.0.21+9/Contents/Home" ]; then
  export JAVA_HOME="$ROOT_DIR/tools/jdk-17.0.21+9/Contents/Home"
elif [ -d "$ROOT_DIR/tools/jdk-17.0.21+9" ]; then
  export JAVA_HOME="$ROOT_DIR/tools/jdk-17.0.21+9"
else
  echo "[env] 未找到便携 JDK，请检查 tools/ 目录" >&2
fi

export MAVEN_HOME="$ROOT_DIR/tools/apache-maven-3.10.0"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"

# 依赖与构建缓存留在工作区内，避免污染用户主目录（~/.m2 不可写）
export MAVEN_REPO="$ROOT_DIR/tools/m2"
mkdir -p "$MAVEN_REPO"
export MAVEN_ARGS="-Dmaven.repo.local=$MAVEN_REPO -Dmaven.artifact.threads=8"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}"
