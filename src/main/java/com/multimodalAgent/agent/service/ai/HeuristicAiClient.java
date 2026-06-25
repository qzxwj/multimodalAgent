package com.multimodalAgent.agent.service.ai;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import reactor.core.publisher.Flux;

/**
 * 本地 mock 模型客户端。
 *
 * <p>用于无模型环境下演示完整业务流程，支持意图分类、心理评估和简单回答。</p>
 */
public class HeuristicAiClient implements AiClient {

    @Override
    public String complete(List<AiMessage> messages) {
        String prompt = messages.stream()
                .map(AiMessage::content)
                .reduce("", (left, right) -> left + "\n" + right);
        String input = lastUserMessage(messages);
        if (prompt.contains("intent classifier")) {
            return classify(input);
        }
        if (prompt.contains("strict JSON") || prompt.contains("\"emotion\"")) {
            return analyze(input);
        }
        if (prompt.contains("Agentic RAG planner")) {
            return "{\"reason\":\"Search for the student's current concern, campus support guidance, and safety boundaries.\",\"queries\":[\"campus counseling anxiety stress coping\", \"student emotional distress support\", \"campus mental health crisis safety policy\"]}";
        }
        if (prompt.contains("Agentic RAG evidence reviewer")) {
            return "{\"sufficient\":true,\"reason\":\"The evidence can support a safe and general mental-health response.\",\"followUpQueries\":[]}";
        }
        return answer(input, prompt);
    }

    @Override
    public Flux<String> stream(List<AiMessage> messages) {
        String answer = complete(messages);
        return Flux.fromArray(answer.split("(?<=.)"))
                .delayElements(Duration.ofMillis(12));
    }

    private String classify(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        String current = currentInput(input).toLowerCase(Locale.ROOT);
        if (RiskLexicon.hasHighRiskSignal(current)) {
            return "RISK";
        }
        if (RiskLexicon.hasConsultSignal(current) || RiskLexicon.hasConsultSignal(normalized)) {
            return "CONSULT";
        }
        return "CHAT";
    }

    private String analyze(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        String current = currentInput(input).toLowerCase(Locale.ROOT);
        if (RiskLexicon.hasHighRiskSignal(current)) {
            return "{\"emotion\":\"HIGH_RISK\",\"emotionScore\":4.0,\"risk\":\"HIGH\",\"confidence\":0.92,\"summary\":\"Explicit self-harm or immediate danger signal detected.\"}";
        }
        if (containsAny(current, "depress", "depressed", "hopeless", "low mood", "worthless", "empty", "break down", "cry")) {
            return "{\"emotion\":\"DEPRESSED\",\"emotionScore\":3.2,\"risk\":\"MEDIUM\",\"confidence\":0.82,\"summary\":\"Persistent low mood or depressive expression detected.\"}";
        }
        if (containsAny(current, "anxious", "anxiety", "stress", "pressure", "insomnia", "cannot sleep", "nervous", "overwhelmed")) {
            return "{\"emotion\":\"ANXIETY\",\"emotionScore\":2.2,\"risk\":\"LOW\",\"confidence\":0.78,\"summary\":\"Anxiety, pressure, or sleep difficulty detected.\"}";
        }
        if (RiskLexicon.hasConsultSignal(normalized)) {
            return "{\"emotion\":\"ANXIETY\",\"emotionScore\":2.0,\"risk\":\"LOW\",\"confidence\":0.70,\"summary\":\"Ongoing emotional support context detected.\"}";
        }
        return "{\"emotion\":\"NORMAL\",\"emotionScore\":0.0,\"risk\":\"LOW\",\"confidence\":0.70,\"summary\":\"No obvious psychological risk signal detected.\"}";
    }

    private String answer(String input, String prompt) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "who are you", "what are you", "your name", "introduce yourself")) {
            return """
                    I am SerenAI, a campus wellbeing assistant for student support. I can offer calm companionship, help with common concerns such as stress, anxiety, sleep, study pressure, and relationship difficulties, and route higher-risk situations toward counselor follow-up.

                    I am not a replacement for a professional counselor, but I can help you organize what you are feeling and decide a safer next step.
                    """;
        }
        if (RiskLexicon.hasHighRiskSignal(normalized)) {
            return """
                    I am taking what you said seriously. The most important thing right now is not to explain everything perfectly, but to make sure you are safe in this moment.

                    Please do three things first: move away from anything you could use to hurt yourself or someone else; contact a real person who can be with you now, such as a classmate, roommate, family member, counselor, or the campus counseling center; and if you are in immediate danger, call local emergency services now.

                    You do not have to carry this moment alone. You can reply with a very short answer first: are you alone right now, and is there someone you can contact immediately?
                    """;
        }
        if (RiskLexicon.hasConsultSignal(normalized) || prompt.contains("Retrieved knowledge:")) {
            return consultAnswer(input);
        }
        return """
                I am SerenAI, and I am here with you. You can use me as a campus mental-health support assistant: I can respond naturally to everyday conversation, and when you talk about stress, anxiety, sleep, relationships, or study difficulties, I will route the need and provide more specific support.

                Would you like to keep it light for now, or talk about one specific thing that has been making life feel stuck recently?
                """;
    }

    private String consultAnswer(String input) {
        String focus = focusFrom(input);
        return """
                I can tell this has been taking up a lot of your energy. You do not need to turn it into "something is wrong with me." We can first break it into a smaller, more manageable part: %s.

                Here are a few concrete steps you can try now:
                1. Write down the main trigger in one or two sentences, separating "what happened" from "what I am afraid might happen."
                2. Give your body a short pause: breathe out slowly for 6 seconds, breathe in for 4 seconds, and repeat this for 3 rounds.
                3. Choose one small action for today, such as sending one confirmation message, taking a warm shower, or putting your phone away 20 minutes earlier.
                4. If this has lasted for more than two weeks or is clearly affecting class, sleep, or eating, please contact the campus counseling center or a counselor soon.

                We can continue from the most specific part. When does this difficulty show up most strongly?
                """.formatted(focus);
    }

    private String focusFrom(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "insomnia", "cannot sleep", "sleep", "sleepless")) {
            return "the sleep difficulty you mentioned may be amplifying daytime fatigue and anxiety";
        }
        if (containsAny(normalized, "exam", "study", "assignment", "paper", "revision", "coursework", "fail")) {
            return "the study or exam pressure needs to be broken into tasks you can handle, instead of staying as one heavy weight";
        }
        if (containsAny(normalized, "breakup", "relationship", "roommate", "friend", "social", "conflict")) {
            return "uncertainty and tension in relationships can easily lead to repeated overthinking";
        }
        if (containsAny(normalized, "low mood", "depressed", "sad", "no motivation", "cry", "empty")) {
            return "the low mood you are experiencing deserves to be taken seriously, not dismissed";
        }
        if (containsAny(normalized, "anxious", "nervous", "afraid", "stress", "pressure", "overwhelmed")) {
            return "your body and mind seem to be staying on alert, which can feel exhausting";
        }
        return "starting with one concrete scene is easier than trying to solve every feeling at once";
    }

    private String lastUserMessage(List<AiMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiMessage message = messages.get(index);
            if ("user".equals(message.role())) {
                return message.content();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
    }

    private String currentInput(String input) {
        String marker = "Current input:";
        int index = input.lastIndexOf(marker);
        if (index < 0) {
            return input;
        }
        return input.substring(index + marker.length()).trim();
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
