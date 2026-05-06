package com.example.ai.aistudy.controller;

import com.example.ai.aistudy.model.*;
import com.example.ai.aistudy.service.ExperimentService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/experiment")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @PostMapping("/init")
    public InitResponse init(@RequestBody(required = false) Map<String, Boolean> body) {
        boolean clearFirst = body != null && Boolean.TRUE.equals(body.get("clearFirst"));
        return experimentService.initFaqData(clearFirst);
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody ExperimentRequest request) {
        // 如果没有提供问题，使用内置测试问题
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            request.setQuestions(Arrays.asList(experimentService.getTestQuestions()));
        }

        List<ExperimentResult> results = experimentService.runExperiments(request);

        Map<String, Object> summary = new HashMap<>();
        String bestExperiment = null;
        double bestScore = 0;

        for (ExperimentResult result : results) {
            if (result.getAverageScores().getTotal() > bestScore) {
                bestScore = result.getAverageScores().getTotal();
                bestExperiment = result.getExperimentName();
            }
        }

        summary.put("runId", "exp-" + System.currentTimeMillis());
        summary.put("results", results);
        summary.put("summary", Map.of(
                "bestExperiment", bestExperiment != null ? bestExperiment : "N/A",
                "bestScores", Map.of("total", bestScore),
                "totalQuestions", request.getQuestions().size(),
                "totalExperiments", request.getExperiments().size()
        ));

        return summary;
    }

    @GetMapping("/verify")
    public VerifyResponse verify(@RequestParam String query,
                                 @RequestParam(defaultValue = "3") int topK) {
        return experimentService.verifyRetrieval(query, topK);
    }
}
