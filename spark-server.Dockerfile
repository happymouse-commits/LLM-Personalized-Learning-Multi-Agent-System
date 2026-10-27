# 讯飞星火 Embedding 代理服务
# 接收 Java 后端的裸 POST，用 HMAC-SHA256 签名后转发到讯飞 emb-cn-huabei-1.xf-yun.com
# spark-server.js 仅用 Node 内置模块（crypto/https/http），无需 npm install
FROM node:20-alpine
WORKDIR /app
COPY spark-server.js .
EXPOSE 9876
CMD ["node", "spark-server.js"]
