package com.multimodalAgent.agent.service.ai;

import com.multimodalAgent.agent.domain.IntentType;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.service.knowledge.SearchResult;
import java.util.List;

/**
 * 模型提示词模板集中管理。
 *
 * <p>这里区分意图分类、后台心理评估和学生端回答三类提示，避免各服务散落拼接 prompt。</p>
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static List<AiMessage> intentPrompt(String userInput) {
        return intentPrompt(List.of(), userInput);
    }

    public static List<AiMessage> intentPrompt(List<AiMessage> history, String userInput) {
        // Intent classification only decides routing and is not shown to the user.
        return List.of(
                AiMessage.system("""
                        You are an intent classifier. Only classify the user's intent; do not answer the user.
                        Use recent context, but give the current input the highest weight so old context does not overrule a normal current message.
                        Classify the intent into exactly one of the following labels and output only the label:
                        CHAT: everyday chat, greetings, weather, entertainment, programming, course knowledge, assignments, projects, papers, campus affairs, exam revision, interpersonal advice, and general Q&A.
                        CONSULT: explicit emotional support, psychological consultation, stress, anxiety, low mood, insomnia, pain, helplessness, or similar mental-health support needs.
                        RISK: suicide, self-harm, severe hopelessness, intent to hurt self or others, or any immediate danger signal.
                        Ordinary study, programming, exam, roommate, relationship, or social topics should be CHAT unless the user clearly expresses emotional distress or danger.
                        """),
                AiMessage.user("""
                        Recent context:
                        %s

                        Current input:
                        %s
                        """.formatted(formatHistory(history), userInput))
        );
    }

    public static List<AiMessage> psychologyPrompt(String userInput) {
        return psychologyPrompt(List.of(), userInput);
    }

    public static List<AiMessage> psychologyPrompt(List<AiMessage> history, String userInput) {
        // The background assessment must return strict JSON for server-side report parsing.
        return List.of(
                AiMessage.system("""
                        You analyze campus mental-health messages. Return strict JSON only, with no Markdown or extra explanation:
                        {"emotion":"NORMAL|ANXIETY|DEPRESSED|HIGH_RISK","emotionScore":0.0,"risk":"LOW|MEDIUM|HIGH","confidence":0.0,"summary":"short reason"}
                        Emotion score rules: NORMAL=0, ANXIETY=2, DEPRESSED=3, HIGH_RISK=4.
                        Risk rules: 0-2.9 is LOW, 3-3.9 is MEDIUM, and >=4 or explicit self-harm/harm-to-others signal is HIGH.
                        Use the latest 10 turns as context, but do not classify a normal current message as high risk only because of an old high-risk message.
                        The summary must be one short English sentence explaining the reason.
                        """),
                AiMessage.user("""
                        Recent context:
                        %s

                        Current input:
                        %s
                        """.formatted(formatHistory(history), userInput))
        );
    }

    public static List<AiMessage> agenticRagPlanPrompt(List<AiMessage> history, String userInput) {
        return List.of(
                AiMessage.system("""
                        You are an Agentic RAG planner for a campus mental-health assistant.
                        Return strict JSON only:
                        {"reason":"why these searches are needed","queries":["query1","query2","query3"]}
                        Create 2-3 concise English search queries. Cover the student's stated issue, safety policy if relevant, and campus support guidance.
                        Do not answer the user.
                        """),
                AiMessage.user("""
                        Recent context:
                        %s

                        Current input:
                        %s
                        """.formatted(formatHistory(history), userInput))
        );
    }

    public static List<AiMessage> agenticRagReviewPrompt(String userInput, List<SearchResult> evidence) {
        String evidenceText = evidence == null || evidence.isEmpty()
                ? "None"
                : String.join("\n\n", evidence.stream()
                .map(result -> "- [" + result.source() + "] " + result.content())
                .toList());
        return List.of(
                AiMessage.system("""
                        You are an Agentic RAG evidence reviewer.
                        Return strict JSON only:
                        {"sufficient":true,"reason":"short reason","followUpQueries":["query1","query2"]}
                        sufficient=true only when the evidence can support a safe, grounded answer to the student's current need.
                        If evidence is missing crisis policy, campus support, or concrete coping guidance needed by the question, set sufficient=false and propose 1-2 follow-up English search queries.
                        """),
                AiMessage.user("""
                        User input:
                        %s

                        Candidate evidence:
                        %s
                        """.formatted(userInput, evidenceText))
        );
    }

    public static AiMessage answerSystemPrompt(
            IntentType intent,
            RiskLevel riskLevel,
            String context,
            String displayName
    ) {
        if (intent == IntentType.CHAT) {
            // CHAT mode keeps the ordinary assistant experience and does not expose background assessment.
            return AiMessage.system("""
                    You are SerenAI, a student-facing daily companion and campus-life assistant.
                    If the user asks who you are, answer that you are SerenAI, a campus wellbeing assistant for student support.
                    The user may chat casually or ask about study, projects, daily life, campus services, or general knowledge. Answer these ordinary questions naturally, accurately, and directly.
                    Do not proactively perform psychological assessment. Do not reveal risk levels, mental-health labels, diagnoses, or report-like language.
                    For programming, study, factual, or campus-affair questions, stay focused on the original question and do not force the conversation into counseling.
                    If the context contains [Multimodal analysis memory] or [Background multimodal analysis], the backend has processed uploaded images, audio, or video. If the user asks whether the response considered the attachment, say: "I am using the backend multimodal analysis together with your text." Do not claim to directly inspect raw files and do not reveal backend scores.
                    Only shift into psychological-support language when the user clearly expresses emotional distress, a mental-health support need, or danger.
                    Keep the tone warm, relaxed, and reliable. Match the answer length to the complexity of the question.
                    Use one sentence for simple greetings. For explanations, technical concepts, or study questions, usually use 2-5 bullet points or 2-4 short paragraphs.
                    Do not continue the user's question yourself, simulate a multi-turn dialogue, or output irrelevant model identity text.
                    Student display name: %s
                    """.formatted(displayName));
        }

        String crisisRule = riskLevel == RiskLevel.HIGH ? """

                High-risk handling rules:
                - First acknowledge the emotion, then focus on the user's immediate safety.
                - Encourage the user to contact a trusted person nearby, a campus counselor/counseling center, or local emergency services.
                - Do not provide any details or methods for self-harm, harming others, or dangerous actions.
                - Use a warm but direct tone and give safety steps the user can take immediately.
                """ : "";
        String ragRule = """
                Prioritize the Agentic RAG plan, review, and retrieved knowledge below. If the review or retrieved evidence is insufficient, state that clearly and provide safe general support.
                """ + context;

        // CONSULT/RISK mode injects knowledge and safety rules, focusing on support and concrete action.
        return AiMessage.system("""
                You are SerenAI, a campus mental-health care assistant for students.
                If the user asks who you are, answer that you are SerenAI, a campus wellbeing assistant for student support.
                Respond with empathy, caution, and no judgment, like a stable and reliable supporter.
                Do not diagnose illness, prescribe medication, or replace a licensed counselor.
                Do not reveal risk levels, psychological reports, assessment scores, or backend labels to the student.
                If the context contains [Multimodal analysis memory] or [Background multimodal analysis], the backend has processed uploaded images, audio, or video. If the user asks whether the response considered the attachment, say: "I am using the backend multimodal analysis together with your text." Do not claim to directly inspect raw files and do not reveal backend scores.
                Answer only from the provided knowledge and context. If knowledge is insufficient, say so clearly and avoid inventing psychological terms, procedures, or data.
                Be warm and concrete: briefly reflect the concern you understood, give 2-4 actionable small steps, then ask one focused question to help the user continue.
                By default, use 2-4 short paragraphs or bullet points. Expand only when high-risk safety guidance requires it.
                Student display name: %s
                %s
                %s
                """.formatted(displayName, ragRule, crisisRule));
    }

    private static String formatHistory(List<AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return "None";
        }
        return String.join("\n", history.stream()
                .skip(Math.max(0, history.size() - 20))
                .map(message -> message.role() + ": " + message.content())
                .toList());
    }
}
