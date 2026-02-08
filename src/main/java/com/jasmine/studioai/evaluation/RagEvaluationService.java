package com.jasmine.studioai.evaluation;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * Evaluate the faithfulness of a generated answer against provided context.
     */
    public EvalResult evaluateFaithfulness(String answer, String context) {
        String prompt = """
                Evaluate whether the generated answer is faithful to the provided context.
                Rate from 0 to 10 where 10 is fully faithful.
                Reply with ONLY a JSON: {"score": <number>, "explanation": "<brief reason>"}

                Context:
                %s

                Generated Answer:
                %s
                """.formatted(truncate(context, 3000), truncate(answer, 3000));

        return parseEvalResponse(chatLanguageModel.chat(prompt));
    }

    /**
     * Evaluate the relevance of retrieved documents to the query.
     */
    public EvalResult evaluateRelevance(String query, String retrievedDoc) {
        String prompt = """
                Evaluate how relevant the retrieved document is to the query.
                Rate from 0 to 10 where 10 is highly relevant.
                Reply with ONLY a JSON: {"score": <number>, "explanation": "<brief reason>"}

                Query: %s

                Retrieved Document:
                %s
                """.formatted(truncate(query, 500), truncate(retrievedDoc, 3000));

        return parseEvalResponse(chatLanguageModel.chat(prompt));
    }

    /**
     * Compare two prompt templates with A/B testing.
     */
    public AbTestResult comparePromptTemplates(String promptA, String promptB, String input) {
        String evalPrompt = """
                Compare two prompt template outputs for the same input.
                Choose which output (A or B) is better for the given task.
                Reply with ONLY a JSON: {"winner": "A" or "B", "scoreA": <0-10>, "scoreB": <0-10>, "reason": "<brief>"}

                Input: %s

                Output A: %s

                Output B: %s
                """.formatted(truncate(input, 1000), truncate(promptA, 2000), truncate(promptB, 2000));

        String response = chatLanguageModel.chat(evalPrompt);
        try {
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = response.substring(jsonStart, jsonEnd);
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                return new AbTestResult(
                        node.path("winner").asText("tie"),
                        node.path("scoreA").asDouble(5.0),
                        node.path("scoreB").asDouble(5.0),
                        node.path("reason").asText("")
                );
            }
        } catch (Exception e) {
            log.warn("Failed to parse A/B test result: {}", e.getMessage());
        }
        return new AbTestResult("tie", 5.0, 5.0, "Could not evaluate");
    }

    private EvalResult parseEvalResponse(String response) {
        try {
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}') + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = response.substring(jsonStart, jsonEnd);
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                return new EvalResult(
                        node.path("score").asDouble(0.0),
                        node.path("explanation").asText("")
                );
            }
        } catch (Exception e) {
            log.warn("Failed to parse evaluation response: {}", e.getMessage());
        }
        return new EvalResult(0.0, "Could not evaluate");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public record EvalResult(double score, String explanation) {}
    public record AbTestResult(String winner, double scoreA, double scoreB, String reason) {}
}
