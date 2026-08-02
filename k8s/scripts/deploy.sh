#!/bin/bash
# zota-server deploy script
#
# 使用方式：
#   1. （可选）mvn package -DskipTests  产出 JAR
#   2. ./k8s/scripts/deploy.sh 0.0.1           # 构建 + 推送 + 更新 k8s tag
#   3. ./k8s/scripts/deploy.sh 0.0.1 --sync    # 同上 + apply 到 K8s
#
# 前置条件：
#   - docker login harbor.intra.zeron.ai
#   - kubectl 可用（--sync 时需要）
set -euo pipefail

TAG="${1:-latest}"
SYNC="${2:-}"
REPO="harbor.intra.zeron.ai/smartdrive/zota-server"
KUBECONFIG="${KUBECONFIG:-/Users/minyi/kube.conf}"
JAR_PATH="hawkbit-monolith/hawkbit-update-server/target/hawkbit-update-server-0-SNAPSHOT.jar"

cd "$(dirname "$0")/../.."

# ── 1. 检查 JAR 是否已编译 ──
if [ ! -f "$JAR_PATH" ]; then
  echo "=== JAR 不存在，开始 Maven 编译 ==="
  mvn package -DskipTests -pl hawkbit-monolith/hawkbit-update-server -am $([ -f pom.xml ] && echo "" || echo "")
fi

echo "=== Build ${REPO}:${TAG} ==="
docker build --platform linux/amd64 \
  -f k8s/Dockerfile \
  -t "${REPO}:${TAG}" .

echo "=== Push ==="
docker push "${REPO}:${TAG}"

echo "=== Update k8s image tag ==="
cd k8s/overlays/production
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' "s/newTag:.*/newTag: ${TAG}/" kustomization.yaml
else
  sed -i "s/newTag:.*/newTag: ${TAG}/" kustomization.yaml
fi
cd ../../..

if [ "$SYNC" = "--sync" ]; then
  echo "=== Apply to K8s ==="
  export KUBECONFIG="${KUBECONFIG}"
  kubectl apply -k k8s/overlays/production
fi

echo "=== Done ==="
echo "Check: kubectl --kubeconfig=${KUBECONFIG} -n zota get pods -l app=zota-server"
