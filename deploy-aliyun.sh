#!/bin/bash
# ============================================================
# 师承智导 - 阿里云 ECS 一键部署脚本
# 用法: bash deploy-aliyun.sh
# 服务器: Alibaba Cloud Linux (8.156.64.232)
# ============================================================
set -e

REPO_URL="https://github.com/happymouse-commits/CRYD-Final.git"
DEPLOY_DIR="/root/SGHR"

echo "=========================================="
echo "🚀 开始部署: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

# ---- 1. 检查/安装 Docker ----
echo "[1/6] 检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "   安装 Docker..."
    dnf install -y docker
    systemctl start docker
    systemctl enable docker
    echo "   ✅ Docker 安装完成"
else
    echo "   ✅ Docker 已安装: $(docker --version)"
fi

# 检查 docker compose 插件
if ! docker compose version &> /dev/null; then
    echo "   安装 docker compose 插件..."
    dnf install -y docker-compose-plugin
fi
echo "   ✅ Docker Compose: $(docker compose version)"

# ---- 2. 拉取代码 ----
echo "[2/6] 拉取代码..."
if [ -d "$DEPLOY_DIR/.git" ]; then
    cd "$DEPLOY_DIR"
    git pull origin master
    echo "   ✅ 代码已更新"
else
    git clone "$REPO_URL" "$DEPLOY_DIR"
    cd "$DEPLOY_DIR"
    echo "   ✅ 代码已克隆"
fi
COMMIT=$(git log -1 --format="%h %s")
echo "   最新提交: $COMMIT"

# ---- 3. 检查 .env 文件 ----
echo "[3/6] 检查 .env 配置..."
if [ ! -f "$DEPLOY_DIR/.env" ]; then
    echo "   ❌ .env 文件不存在！"
    echo "   请先从本地上传 .env 文件:"
    echo "   scp .env root@8.156.64.232:$DEPLOY_DIR/.env"
    exit 1
fi
echo "   ✅ .env 文件存在"

# ---- 4. 创建数据目录 ----
echo "[4/6] 创建数据目录..."
mkdir -p data/resources data/audio/chapters data/knowledge-base data/ppt-cache
echo "   ✅ 数据目录已创建"

# ---- 5. 构建并启动服务 ----
echo "[5/6] 构建并启动 Docker 服务..."
docker compose down 2>/dev/null || true
docker compose up -d --build
echo "   ✅ 服务已启动"

# ---- 6. 健康检查 ----
echo "[6/6] 健康检查..."
sleep 10
SUCCESS=false
for i in $(seq 1 15); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" != "000" ] && [ "$HTTP_CODE" != "" ]; then
        echo "   ✅ 前端正常: HTTP $HTTP_CODE (第${i}次)"
        SUCCESS=true
        break
    fi
    echo "   等待中... ($i/15)"
    sleep 3
done

echo ""
echo "=========================================="
if [ "$SUCCESS" = true ]; then
    echo "✅ 部署完成: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "   提交: $COMMIT"
    echo "   访问: http://8.156.64.232"
    echo "=========================================="
    echo ""
    echo "📋 容器状态:"
    docker compose ps
    echo ""
    echo "📄 查看日志: docker compose logs -f backend"
else
    echo "❌ 前端启动失败，请检查日志"
    echo "   docker compose logs"
    echo "=========================================="
    exit 1
fi
