# FAQ系统实现设计方案

## 1. 目标

基于现有RAG架构扩展FAQ功能，并通过详细的检索/切分日志验证"找到了内容却答错"的问题根源。

## 2. 核心问题

当前RAG系统使用固定500字符切分+50重叠，可能导致：
- 关键词或完整语义被截断
- 检索命中的chunk缺少完整上下文
- LLM基于不完整的上下文给出错误答案

## 3. 实现方案

### 3.1 新增接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/faq/add` | POST | 添加FAQ问答对（打印切分详情） |
| `/api/faq/ask` | POST | FAQ问答（返回答案+来源chunk） |
| `/api/faq/verify` | GET | 验证模式：返回检索详情，不经过LLM |

### 3.2 请求/响应格式

**POST /api/faq/add**
```json
// Request
{
  "question": "Spring Boot有哪些自动配置特性？",
  "answer": "Spring Boot通过starter依赖和自动配置简化了Spring应用开发..."
}

// Response
{
  "success": true,
  "chunks": [
    {"index": 0, "text": "Spring Boot通过starter依赖...", "charRange": "0-487"},
    {"index": 1, "text": "...自动配置特性大大简化...", "charRange": "450-950"}
  ]
}
```

**POST /api/faq/ask**
```json
// Request
{"question": "Spring Boot的自动配置是如何工作的？"}

// Response
{
  "answer": "Spring Boot通过starter依赖和自动配置...",
  "sources": [
    {"chunkIndex": 0, "text": "Spring Boot通过starter依赖...", "score": 0.82},
    {"chunkIndex": 1, "text": "...自动配置特性大大简化...", "score": 0.71}
  ]
}
```

**GET /api/faq/verify**
```
?query=Spring Boot自动配置&topK=3
```
```json
{
  "query": "Spring Boot自动配置",
  "retrievals": [
    {"chunkIndex": 0, "text": "Spring Boot通过starter依赖...", "score": 0.82, "distance": 0.18},
    {"chunkIndex": 1, "text": "...自动配置特性大大简化...", "score": 0.71, "distance": 0.29}
  ]
}
```

### 3.3 数据模型

```java
// 新增 FAQ 问答对模型
public class Faq {
    private String id;
    private String question;
    private String answer;
    private LocalDateTime createdAt;
}

// 响应用的来源chunk
public class ChunkSource {
    private int chunkIndex;
    private String text;
    private double score;
    private double distance;
}
```

### 3.4 核心流程

**添加FAQ (`/api/faq/add`)：**
1. 接收 question + answer
2. TextChunker 切分文本（打印切分边界日志）
3. 存入 VectorStore（与现有RAG共用）
4. 返回切分详情用于验证

**FAQ问答 (`/api/faq/ask`)：**
1. 检索 topK=3 个相关chunk
2. 拼接上下文 + 专用FAQ Prompt
3. 返回答案 + 来源chunk列表

**验证模式 (`/api/faq/verify`)：**
1. 检索 topK 个chunk
2. 返回检索详情（分数、距离、原文）
3. **不经过LLM**，用于诊断检索是否正确

### 3.5 Prompt 模板

```markdown
你是一个严谨的FAQ问答助手。请严格基于【参考文档】回答用户的问题。

规则：
- 只使用参考文档中明确提及的信息作答
- 如果信息不足，回答"根据文档无法确定"
- 引用时指明来源

【参考文档】
{context}

【用户问题】
{question}
```

## 4. 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `model/Faq.java` | 新增 | FAQ数据模型 |
| `model/ChunkSource.java` | 新增 | chunk来源响应模型 |
| `service/FaqService.java` | 新增 | FAQ服务（含切分日志） |
| `controller/FaqController.java` | 新增 | FAQ REST接口 |
| `service/RagService.java` | 修改 | 增加verifyRetrieval方法 |

## 5. 验证步骤

1. 添加一对FAQ，观察切分日志
2. 使用 `/api/faq/verify?query=...` 验证检索到的chunk是否相关
3. 使用 `/api/faq/ask` 进行问答，对比来源是否与问题匹配
4. 如果检索正确但答错 → 问题在Prompt或LLM
5. 如果检索不相关 → 问题在Embedding或切分
