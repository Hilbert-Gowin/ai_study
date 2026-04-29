# AI 学习项目

Spring AI 学习示例，涵盖对话、RAG、结构化输出、函数调用等核心场景。

## 技术栈

- **Spring Boot 3.5** - Web 框架
- **Spring AI 1.1.2** - AI 抽象层
- **Spring AI Alibaba 1.1.2.0** - 阿里云百炼适配
- **Java 17** - 开发语言

## 功能特性

### 对话
| 接口 | 说明 |
|------|------|
| `POST /api/chat/stream` | 流式对话 |
| `POST /api/chat/memory/stream` | 带会话记忆的流式对话 |
| `GET /api/chat/sessions` | 获取会话列表 |
| `GET /api/chat/sessions/{id}` | 获取指定会话消息历史 |
| `DELETE /api/chat/sessions/{id}` | 删除会话 |

### RAG 检索
| 接口 | 说明 |
|------|------|
| `POST /api/rag/documents` | 添加文档到向量库 |
| `GET /api/rag/search` | 检索相似文档 (topK, similarityThreshold) |
| `POST /api/rag/ask` | RAG 问答 |

### FAQ 问答
| 接口 | 说明 |
|------|------|
| `POST /api/faq/add` | 添加FAQ问答对（返回切分详情） |
| `POST /api/faq/ask` | FAQ问答（返回答案+来源chunk） |
| `GET /api/faq/verify` | 验证模式：返回检索详情，不经过LLM |

### 结构化输出
| 接口 | 说明 |
|------|------|
| `POST /api/structured/person` | 提取人物信息 |
| `POST /api/structured/entities` | 提取多个实体 |

## 快速开始

### 1. 配置 API Key

环境变量配置：

```bash
# 阿里云百炼
export SPRING_AI_MODEL=dashscope
export AI_DASHSCOPE_API_KEY=your-api-key

# 或 OpenAI (智谱 GLM)
export SPRING_AI_MODEL=openai
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
export SPRING_AI_EMBEDDING_TYPE=zhipu   # embedding 使用智谱
```

### 2. 启动项目

```bash
./mvnw spring-boot:run
```

## 项目结构

```
src/main/java/com/example/ai/aistudy/
├── AiStudyApplication.java          # 启动类
├── config/
│   └── BigModelConfig.java          # ChatClient 和 EmbeddingModel 配置
├── controller/
│   ├── ChatController.java          # 对话接口
│   ├── FaqController.java           # FAQ 接口
│   ├── RagController.java           # RAG 接口
│   └── StructuredOutputController.java  # 结构化输出
├── model/
│   ├── Conversation.java            # 会话模型
│   ├── Message.java                 # 消息模型
│   ├── Faq.java                    # FAQ 数据模型
│   ├── FaqRequest.java             # FAQ 请求模型
│   ├── FaqAddResponse.java         # FAQ 添加响应
│   ├── FaqAskResponse.java        # FAQ 问答响应
│   ├── VerifyResponse.java         # 验证响应
│   ├── ChunkSource.java            # chunk 来源
│   └── ChunkInfo.java              # 切分信息
├── rag/
│   └── VectorStoreConfig.java       # 向量存储配置
├── chunk/
│   └── SimpleTextChunker.java       # 文本切分 (500字符, 50重叠，含切分日志)
├── service/
│   ├── ChatService.java             # 对话服务
│   ├── FaqService.java             # FAQ 服务
│   ├── RagService.java              # RAG 服务 (相似度阈值 0.5)
│   └── ToolService.java             # 工具服务
└── function/
    └── WeatherFunction.java         # 函数调用示例
```

## RAG 配置说明

- **向量存储**: SimpleVectorStore (内存)，生产环境可换 Milvus/Chroma
- **Embedding**: 支持 dashscope / zhipu / local (本地模拟)
- **相似度阈值**: 0.5 (余弦相似度)
- **TopK**: 默认 3
- **文本切分**: 固定 500 字符，50 字符重叠

## 接口示例

### 流式对话

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，介绍一下 Spring AI"}'
```

### 带记忆的对话

```bash
curl -X POST http://localhost:8080/api/chat/memory/stream \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "user-001", "message": "我叫张三"}'

curl -X POST http://localhost:8080/api/chat/memory/stream \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "user-001", "message": "我叫什么？"}'
```

### RAG 问答

```bash
# 添加文档
curl -X POST http://localhost:8080/api/rag/documents \
  -H "Content-Type: application/json" \
  -d '{"texts": ["Spring AI 是 Spring 官方推出的 AI 框架", "它提供了统一抽象的 AI 接口"]}'

# 检索
curl "http://localhost:8080/api/rag/search?query=Spring%20AI&topK=3"

# 问答
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Spring AI 是什么？"}'
```

### FAQ 问答

```bash
# 添加FAQ
curl -X POST http://localhost:8080/api/faq/add \
  -H "Content-Type: application/json" \
  -d '{"question": "Spring Boot有哪些自动配置特性？", "answer": "Spring Boot通过starter依赖和自动配置简化了Spring应用开发..."}'

# FAQ问答
curl -X POST http://localhost:8080/api/faq/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Spring Boot的自动配置是如何工作的？"}'

# 验证检索（不经过LLM，用于诊断）
curl "http://localhost:8080/api/faq/verify?query=Spring%20Boot自动配置&topK=3"
```

## 文档

- [提示词模板](docs/提示词模板.md)
- [提示词编写原则](docs/提示词编写原则.md)