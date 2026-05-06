package com.example.ai.aistudy.service;

import com.example.ai.aistudy.chunk.SimpleTextChunker;
import com.example.ai.aistudy.model.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExperimentService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final SimpleTextChunker textChunker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 预设FAQ：基于完整长文切分，同一主题的碎片在同一篇长文中，跨chunk边界有语义连贯性
    // 知识库：一篇关于RAG系统调优的完整文章，分12个chunk（chunkSize=500），只有topK足够大时才能召回多个相关chunk
    private static final String[][] PRESET_FAQS = {
        // Chunk0: RAG概述 + Chunk策略总述
        {"RAG系统由哪几个核心模块组成？", "RAG（检索增强生成）系统由三个核心模块组成：1）检索模块，负责从向量数据库中召回相关文档；2）增强模块，将检索结果与原始问题拼接成上下文；3）生成模块，由LLM基于上下文生成答案。Chunk策略是影响检索模块效果的关键因素之一。"},
        // Chunk1: Chunk太小的问题
        {"Chunk太小会有什么具体问题？", "Chunk太小（单块<200字符）会导致以下问题：1）上下文碎片化，每个chunk包含的语义不完整；2）检索时容易召回不相关的内容，因为单个chunk只包含部分语义；3）LLM无法理解完整的逻辑链条，因为关键信息被切断在不同chunk中；4）overlap机制可以部分缓解但无法根本解决。"},
        // Chunk2: Chunk太大的问题
        {"Chunk太大会有什么具体问题？", "Chunk太大（单块>1000字符）会导致：1）引入过多无关噪声，相关内容被淹没在大量无关内容中；2）超过LLM上下文窗口上限，导致文档被截断，有效信息反而丢失；3）相似度计算时，主题不明确导致召回精度下降。推荐中文场景使用400-600字符的chunk大小。"},
        // Chunk3: Overlap的作用和推荐值
        {"Overlap设置为多少字符比较合适？", "Overlap（重叠字符数）的作用是在相邻chunk之间保留上下文连贯性，避免关键语义在chunk边界被切断。推荐设置50-100字符。overlap太小无法有效保持连贯性，overlap太大会增加冗余。配合chunkSize=400-600使用时，overlap=50是性价比最高的选择。"},
        // Chunk4: TopK太小的后果
        {"TopK太小为什么会导致答案不完整？", "TopK太小（如设置为1-2）会导致答案不完整，原因在于：1）当用户问题涉及多个相关知识点时，单一最近邻检索可能召回的是次优chunk，真正相关的chunk被漏掉；2）有些知识点在不同文档中分散存储，需要多个chunk才能拼出完整答案；3）某些查询的语义复杂，单一chunk无法覆盖所有意图。"},
        // Chunk5: TopK太大的问题
        {"TopK太大会引入什么风险？", "TopK太大（如设置为10以上）会引入以下风险：1）召回过多噪声chunk，相关性低的干扰内容会淹没正确答案；2）上下文总长度快速膨胀，有效信息密度下降；3）LLM处理更多噪声内容会增加推理成本和延迟；4）实验表明K>10后召回率提升微弱，但噪声却显著增加。"},
        // Chunk6: TopK与maxTokens的配合
        {"TopK和maxTokens如何配合使用？", "TopK和maxTokens需要配合调参：1）TopK控制召回的chunk数量，maxTokens控制最终输给LLM的上下文总长度；2）假设chunk平均500字符，TopK=10意味着可能召回5000字符，但maxTokens=1000时只有前2个chunk能进入上下文；3）大chunk配小TopK（chunk>800时用K=2-3），小chunk配大TopK（chunk<300时用K=8-10）；4）推荐从TopK=5、maxTokens=2000开始调。"},
        // Chunk7: 如何确定最优TopK
        {"如何系统性地确定最优TopK值？", "确定最优TopK的系统性方法：1）先固定maxTokens=2000，从TopK=2开始每次+1，观察答案完整性变化；2）当TopK增加但答案质量不再提升时的K值就是最优；3）一般K=3-5是大多数场景的平衡点；4）最终要结合具体场景——知识库小（<1000篇）可以小K，知识库大（>10000篇）需要适当增大K来对抗噪声。"},
        // Chunk8: 相似度阈值的概念
        {"相似度阈值高低对召回有什么影响？", "相似度阈值（similarityThreshold）控制召回精准度：高阈值（0.7-0.9）= 严格召回，只有高度相关的内容被召回，精度高但召回率低，可能漏掉相关文档；低阈值（0.3-0.5）= 宽松召回，召回更多内容但可能包含噪声。没有绝对好坏，取决于场景——医疗法律等高精度场景用高阈值，开放式问答可用低阈值。"},
        // Chunk9: 如何判断检索结果可靠性
        {"如何判断检索到的chunk是否真正可靠？", "判断chunk可靠性的方法：1）看相似度分数——>0.7为高置信（强相关），0.5-0.7为中置信（可能相关），<0.5为低置信（可能不相关）；2）看内容匹配度——chunk内容是否和query的意图核心相关；3）看召回完整性——相关知识点是否分散在多个chunk中，只有凑齐才能完整回答；4）结合验证接口观察score分布来调阈值。"},
        // Chunk10: 检索失败的常见原因
        {"为什么RAG系统有时检索不到任何相关文档？", "RAG检索失败的常见原因：1）Embedding模型对语义理解不准确——用户问题与文档用词差异大（如'电脑'vs'计算机'）；2）相似度阈值设得太高，把相关但不完全匹配的文档过滤了；3）chunk边界把关键信息切断，相关内容分散在不同chunk中；4）知识库本身没有相关内容。可以用查询扩展/改写、多路召回等方法改善。"},
        // Chunk11: 综合调优思路
        {"RAG系统调优的整体思路是什么？", "RAG调优遵循'先召回后精调'原则：第一步调检索（chunk大小→overlap→TopK→相似度阈值），确保相关文档能被召回；第二步调生成（maxTokens→提示词），确保上下文信息能被有效利用。关键洞察：当答案不完整时，通常是检索端问题（TopK太小或阈值太高）；当答案有幻觉时，通常是生成端问题（上下文噪声太多或提示词不当）。"}
    };

    // 综合测试问题：必须召回多个chunk（至少3个以上）才能完整回答
    private static final String[] TEST_QUESTIONS = {
        "请详细说明RAG系统中Chunk参数和TopK参数如何配合调优，并给出具体的参数配置思路",
        "我的RAG系统检索效果不稳定，有时召回太多噪声，有时又召回太少，应该怎么调整？",
        "如果发现答案不完整，可能有哪些原因？应该如何系统性地排查和优化？"
    };

    public ExperimentService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                            EmbeddingModel embeddingModel, SimpleTextChunker textChunker) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
    }

    public String[] getTestQuestions() {
        return TEST_QUESTIONS;
    }

    public InitResponse initFaqData(boolean clearFirst) {
        if (clearFirst) {
            clearVectorStore();
        }

        List<String> topics = new ArrayList<>();
        int totalChunks = 0;

        for (String[] faq : PRESET_FAQS) {
            String question = faq[0];
            String answer = faq[1];
            String content = "问题: " + question + "\n答案: " + answer;

            List<Document> documents = textChunker.chunkToDocuments(List.of(content));
            totalChunks += documents.size();
            vectorStore.add(documents);

            String topic = question.length() > 20 ? question.substring(0, 20) + "..." : question;
            topics.add(topic);
        }

        return new InitResponse(true, PRESET_FAQS.length, totalChunks, topics);
    }

    private void clearVectorStore() {
        try {
            var field = vectorStore.getClass().getDeclaredField("documents");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var documents = (List<Document>) field.get(vectorStore);
            documents.clear();
        } catch (Exception e) {
            System.err.println("清空向量库失败: " + e.getMessage());
        }
    }

    public List<ExperimentResult> runExperiments(ExperimentRequest request) {
        List<ExperimentResult> results = new ArrayList<>();

        for (ExperimentRequest.ExperimentConfig config : request.getExperiments()) {
            List<EvaluationResult> evaluations = new ArrayList<>();

            for (String question : request.getQuestions()) {
                List<Document> documents = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(config.getTopK())
                                .similarityThreshold(0.5f)
                                .build()
                );

                String context = buildContext(documents, config.getMaxTokens());

                List<ChunkSource> sources = documents.stream()
                        .map(doc -> {
                            double score = getSimilarityScore(doc);
                            return new ChunkSource(
                                    (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0),
                                    doc.getText().substring(0, Math.min(100, doc.getText().length())) + "...",
                                    score,
                                    1.0 - score
                            );
                        })
                        .collect(Collectors.toList());

                String answer = callLlm(question, context);
                Scores scores = evaluateAnswer(question, context, answer);

                evaluations.add(new EvaluationResult(question, answer, sources, scores));
            }

            Scores avgScores = calculateAverageScores(evaluations);

            results.add(new ExperimentResult(
                    config.getName(),
                    config.getTopK(),
                    config.getMaxTokens(),
                    evaluations,
                    avgScores
            ));
        }

        return results;
    }

    private String buildContext(List<Document> documents, int maxTokens) {
        int maxChars = maxTokens * 2;
        StringBuilder sb = new StringBuilder();

        for (Document doc : documents) {
            if (sb.length() + doc.getText().length() + 10 > maxChars) {
                break;
            }
            if (sb.length() > 0) sb.append("\n---\n");
            sb.append(doc.getText());
        }

        return sb.toString();
    }

    private String callLlm(String question, String context) {
        String prompt = """
                你是一个严谨的问答助手。请严格基于以下【参考文档】回答用户的问题。

                规则：
                - 只使用参考文档中明确提及的信息作答；
                - 如果参考文档中信息不足，直接回答"根据现有文档无法确定"；
                - 禁止推测或补充文档中未提及的内容。

                【参考文档】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private Scores evaluateAnswer(String question, String context, String answer) {
        String judgePrompt = """
                你是一个严格的RAG系统评估员。请严格评估答案质量。

                【评分规则】
                - 准确性（accuracy）：答案有无事实错误？是否基于上下文而非自行发挥？
                - 完整性（completeness）：答案是否涵盖了问题所需的关键信息点？只回答一半给5分
                - 一致性（consistency）：答案是否与上下文矛盾？上下文没有的信息答案有就扣分
                - 简洁性（conciseness）：是否简洁？冗长废话扣分，必要的展开不扣分

                【特别注意】
                - 如果答案过于简短（<30字），完整性最多给4分
                - 如果答案出现上下文未提及的信息（幻觉），准确性最多给5分
                - 如果答案只引用了部分相关chunk就停止，完整性至少扣2分
                - 10分制：9-10=优秀，7-8=良好，5-6=及格，<5=差

                【问题】
                %s

                【检索到的上下文】
                %s

                【待评估答案】
                %s

                请以JSON格式输出：
                {
                  "accuracy": <分数>,
                  "completeness": <分数>,
                  "consistency": <分数>,
                  "conciseness": <分数>
                }
                """.formatted(question, context, answer);

        String response = chatClient.prompt()
                .user(judgePrompt)
                .call()
                .content();

        try {
            String jsonStr = extractJson(response);
            var scoresMap = objectMapper.readValue(jsonStr, Map.class);

            int accuracy = ((Number) scoresMap.get("accuracy")).intValue();
            int completeness = ((Number) scoresMap.get("completeness")).intValue();
            int consistency = ((Number) scoresMap.get("consistency")).intValue();
            int conciseness = ((Number) scoresMap.get("conciseness")).intValue();
            double total = (accuracy + completeness + consistency + conciseness) / 4.0;

            return new Scores(accuracy, completeness, consistency, conciseness, total);
        } catch (Exception e) {
            System.err.println("LLM 裁判打分解析失败: " + e.getMessage());
            System.err.println("原始响应: " + response);
            return new Scores(5, 5, 5, 5, 5.0);
        }
    }

    private String extractJson(String response) {
        int jsonStart = response.indexOf("```json");
        if (jsonStart >= 0) {
            int start = response.indexOf("{", jsonStart);
            int end = response.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return response.substring(start, end + 1);
            }
        }
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private Scores calculateAverageScores(List<EvaluationResult> evaluations) {
        double sumAccuracy = 0, sumCompleteness = 0, sumConsistency = 0, sumConciseness = 0;

        for (EvaluationResult eval : evaluations) {
            sumAccuracy += eval.getScores().getAccuracy();
            sumCompleteness += eval.getScores().getCompleteness();
            sumConsistency += eval.getScores().getConsistency();
            sumConciseness += eval.getScores().getConciseness();
        }

        int n = evaluations.size();
        double avgAccuracy = sumAccuracy / n;
        double avgCompleteness = sumCompleteness / n;
        double avgConsistency = sumConsistency / n;
        double avgConciseness = sumConciseness / n;
        double total = (avgAccuracy + avgCompleteness + avgConsistency + avgConciseness) / 4;

        return new Scores(
                (int) Math.round(avgAccuracy),
                (int) Math.round(avgCompleteness),
                (int) Math.round(avgConsistency),
                (int) Math.round(avgConciseness),
                Math.round(total * 100) / 100.0
        );
    }

    private double getSimilarityScore(Document doc) {
        Object distance = doc.getMetadata().get("distance");
        if (distance != null) {
            return 1.0 - ((Number) distance).doubleValue();
        }
        return 0.5;
    }

    public VerifyResponse verifyRetrieval(String query, int topK) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.3f)
                        .build()
        );

        List<ChunkSource> retrievals = documents.stream()
                .map(doc -> {
                    double score = getSimilarityScore(doc);
                    return new ChunkSource(
                            (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0),
                            doc.getText().substring(0, Math.min(150, doc.getText().length())) + "...",
                            score,
                            1.0 - score
                    );
                })
                .collect(Collectors.toList());

        return new VerifyResponse(query, retrievals);
    }
}
