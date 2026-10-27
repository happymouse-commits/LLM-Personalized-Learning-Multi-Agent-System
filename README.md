# 基于大模型的个性化资源生成与学习多智能体系统

> 师承智导（CRYD）—— 面向编程课程学习场景的多智能体协同自适应学习平台

本项目以大语言模型为核心，通过多个智能体协同完成学生画像分析、知识库管理、个性化学习资源生成、学习路径规划与辅导答疑，为学生提供「测—学—练—评」一体化的个性化学习体验。

## 主要功能

- **学生画像与问卷**：通过问卷与学习行为分析构建学生画像，识别薄弱知识点
- **多智能体协同**：画像分析、知识库管理、课程设计、题目生成、路径规划、AI 导师六个智能体协同工作
- **个性化资源生成**：自动生成学习文档、PPT、思维导图、听学音频与练习题
- **RAG 知识库**：基于讯飞星火 Embedding 的向量检索，失败时自动回退本地哈希向量
- **AI 导师答疑**：流式对话、代码解释、错题本与学习建议
- **学习路径规划**：按掌握度动态生成与调整学习路径，支持今日推荐
- **语音能力**：讯飞语音听写（ASR）+ MiMo 语音克隆 TTS
- **代码实训**：内置代码运行与评测工作台
- **学习分析**：学习时长、掌握度、薄弱点可视化

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Pinia、Vue Router、Element Plus、ECharts、Markmap、Monaco Editor |
| 后端 | Java 21、Spring Boot 3.5、Spring Data JPA、Spring WebSocket、Spring Security |
| 数据库 | MySQL 8.0 |
| AI 能力 | DeepSeek 大模型、讯飞星火 Embedding（RAG）、讯飞语音听写（ASR）、MiMo TTS（语音克隆） |
| 部署 | Docker、Docker Compose、Nginx |

## 目录结构

```
.
├── frontend/                 # Vue 3 前端
│   ├── src/views/student/    # 学生端页面
│   ├── src/views/admin/      # 管理端页面
│   ├── src/components/       # 通用组件
│   └── src/api/              # 接口封装
├── 后端/                     # Spring Boot 后端
│   └── src/main/java/com/happymouse/cryd/
│       ├── agent/            # 多智能体实现
│       ├── controller/       # REST / WebSocket 接口
│       ├── service/          # 业务逻辑
│       ├── repository/       # 数据访问
│       └── model/            # 实体与 DTO
├── spark-server.js           # 讯飞星火 Embedding 签名代理（Node.js）
├── docker-compose.yml        # 生产部署编排
├── Dockerfile / spark-server.Dockerfile
├── start.ps1                 # Windows 一键启动脚本
└── deploy.sh / deploy-aliyun.sh
```

## 快速开始

### 1. 环境要求

- JDK 21
- Maven 3.9+
- Node.js 18+
- MySQL 8.0

### 2. 配置环境变量

复制示例配置并填入自己的密钥：

```bash
cp .env.example .env
```

`.env` 已被 `.gitignore` 忽略，**请勿提交到版本库**。主要变量：

| 变量 | 说明 |
| --- | --- |
| `LLM_API_KEY` | DeepSeek 大模型 API Key |
| `LLM_API_URL` / `LLM_API_MODEL` / `LLM_API_MODEL_PRO` | 模型接口地址与模型名 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 账号密码 |
| `SPARK_APP_ID` / `SPARK_API_KEY` / `SPARK_API_SECRET` | 讯飞星火 Embedding 凭证 |
| `XFYUN_APPID` / `XFYUN_API_KEY` / `XFYUN_API_SECRET` | 讯飞语音听写（ASR）凭证 |
| `MIMO_TTS_API_KEY` / `MIMO_TTS_BASE_URL` / `MIMO_TTS_MODEL` | MiMo 语音克隆 TTS 配置 |
| `RAG_EMBEDDING_PROVIDER` | 向量化提供方（默认 `spark`，失败自动回退本地哈希） |
| `KNOWLEDGE_BASE_PATH` | 知识库文档目录 |

### 3. 本地启动

Windows 一键启动（自动检测 Java / Node / MySQL 并拉起前后端）：

```powershell
powershell -ExecutionPolicy Bypass -File start.ps1
```

或分别启动：

```bash
# 后端（默认端口 8090）
cd 后端
mvn spring-boot:run

# 前端（默认端口 5173）
cd frontend
npm install
npm run dev
```

### 4. Docker 部署

```bash
docker compose up -d --build
```

生产环境请通过 `.env` 注入真实密钥，`docker-compose.yml` 会通过 `env_file` 读取。

## 安全说明

- 所有 API Key 均通过环境变量注入，代码与配置文件中不包含明文密钥
- `.env` 已被 `.gitignore` 忽略；如需提交配置示例，请使用 `.env.example`
- 如果密钥曾出现在提交历史中，请立即前往对应平台轮换密钥，并使用 `git filter-repo` 等工具清理历史

## 说明

本项目为团队学习与竞赛作品，仅供学习交流使用。
