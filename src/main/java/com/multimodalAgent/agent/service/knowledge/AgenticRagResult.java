package com.multimodalAgent.agent.service.knowledge;

import java.util.List;

/**
 * Agentic RAG 的检索过程结果。
 */
public record AgenticRagResult(
        String plan,
        List<String> queries,
        List<SearchResult> evidence,
        String review,
        boolean sufficient
) {
    public static AgenticRagResult empty() {
        return new AgenticRagResult("RAG was not triggered.", List.of(), List.of(), "None", false);
    }

    public String contextBlock() {
        if (evidence.isEmpty()) {
            return """
                    Agentic RAG plan: %s
                    Agentic RAG review: insufficient knowledge was retrieved. The answer must state that the knowledge base is insufficient and provide safe general guidance.
                    """.formatted(plan);
        }
        String evidenceText = String.join("\n\n", evidence.stream()
                .map(result -> "- [" + result.source() + " | score %.3f] %s"
                        .formatted(result.score(), result.content()))
                .toList());
        return """
                Agentic RAG plan: %s
                Agentic RAG queries: %s
                Agentic RAG review: %s
                Retrieved knowledge:
                %s
                """.formatted(plan, String.join("; ", queries), review, evidenceText);
    }
}
