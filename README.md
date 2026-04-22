# Spring AI Alibaba 学习项目

Spring AI + 阿里云百炼 (DashScope) / OpenAI 学习示例，涵盖对话、RAG、结构化输出等核心场景。

## 技术栈

- **Spring Boot 3.5** - Web 框架
- **Spring AI 1.1.2** - AI 抽象层
- **Spring AI Alibaba 1.1.2.0** - 阿里云百炼适配
- **Java 17** - 开发语言

## 功能示例

| 接口 | 说明 |
|------|------|
| `POST /api/chat/stream` | 流式对话 |
| `POST /api/chat/memory/stream` | 带会话记忆的流式对话 |
| `GET /api/chat/sessions` | 获取会话列表 |
| `POST /api/rag/documents` | 添加文档到向量库 |
| `GET /api/rag/search` | 检索相似文档 |
| `POST /api/rag/ask` | RAG 问答 |
| `POST /api/structured/person` | 结构化输出 - 提取人物信息 |
| `POST /api/structured/entities` | 结构化输出 - 提取多个实体 |

## 快速开始

### 1. 配置 API Key

通过环境变量配置：

```bash
# 阿里云百炼
export SPRING_AI_MODEL=dashscope
export DASHSCOPE_API_KEY=your-api-key

# 或 OpenAI (智谱 GLM)
export SPRING_AI_MODEL=openai
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
```

或在 `src/main/resources/application.yml` 中修改默认值（仅供本地开发）。

### 2. 启动项目

```bash
./mvnw spring-boot:run
```

### 3. 访问示例

- Web 界面：http://localhost:8080/chat.html
- API 文档：自行使用 curl 或 Postman 测试

## 项目结构

```
src/main/java/com/example/ai/aistudy/
├── AiStudyApplication.java    # 启动类
├── config/
│   └── BigModelConfig.java    # ChatClient 配置
├── controller/
│   ├── ChatController.java    # 对话接口
│   ├── RagController.java     # RAG 接口
│   └── StructuredOutputController.java  # 结构化输出
├── model/
│   ├── Conversation.java      # 会话模型
│   └── Message.java           # 消息模型
├── rag/
│   └── VectorStoreConfig.java # 向量存储配置
├── service/
│   ├── ChatService.java       # 对话服务
│   └── RagService.java        # RAG 服务
└── function/
    └── WeatherFunction.java   # 函数调用示例
```

## 接口示例

### 流式对话

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，介绍一下 Spring AI"}'
```

### RAG 问答

```bash
# 添加文档
curl -X POST http://localhost:8080/api/rag/documents \
  -H "Content-Type: application/json" \
  -d '{"texts": ["Spring AI 是 Spring 官方推出的 AI 框架", "它提供了统一抽象的 AI 接口"]}'

# 问答
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Spring AI 是什么？"}'
```

## 文档

- [提示词模板](docs/提示词模板.md) - 常用提示词模式
- [提示词编写原则](docs/提示词编写原则.md) - 编写技巧