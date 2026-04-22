package com.example.ai.aistudy.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/structured")
public class StructuredOutputController {

    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 结构化输出示例：提取人物信息
     * POST /api/structured/person
     * Body: {"text": "张三，男，28岁，在北京工作，是一名Java开发工程师"}
     */
    @PostMapping("/person")
    public PersonInfo extractPerson(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String prompt = """
                从以下文本中提取人物信息。

                提取规则：
                - name：人物姓名，字符串；
                - gender：性别，只能是 "男" 或 "女"，无法判断则填 null；
                - age：年龄，整数；无法判断则填 null；
                - city：所在城市；无法判断则填 null；
                - occupation：职业描述，尽量简短；无法判断则填 null。

                如果文本中没有明确提及某项信息，对应字段填 null，不要猜测。

                文本：
                %s
                """.formatted(text);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(PersonInfo.class);
    }

    /**
     * 结构化输出示例：提取多个实体
     * POST /api/structured/entities
     * Body: {"text": "苹果公司由乔布斯创立，总部在加州库比蒂诺，微软总部在华盛顿州雷德蒙德"}
     */
    @PostMapping("/entities")
    public EntitiesResult extractEntities(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String prompt = """
                从以下文本中提取所有公司信息。

                提取规则：
                - name：公司名称，字符串；
                - founder：创始人姓名；文本未提及则填 null；
                - headquarters：总部地点；文本未提及则填 null。

                如果文本中没有明确提及某项信息，对应字段填 null，不要猜测。

                文本：
                %s
                """.formatted(text);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(EntitiesResult.class);
    }

    // --- 数据结构定义 ---

    public record PersonInfo(
            String name,
            String gender,
            Integer age,
            String city,
            String occupation
    ) {}

    public record CompanyInfo(
            String name,
            String founder,
            String headquarters
    ) {}

    public record EntitiesResult(
            List<CompanyInfo> companies
    ) {}
}
