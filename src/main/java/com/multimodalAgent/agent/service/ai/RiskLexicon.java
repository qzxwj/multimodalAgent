package com.multimodalAgent.agent.service.ai;

import java.util.List;

/**
 * 风险和咨询关键词词库。
 *
 * <p>它是模型判断前的硬规则兜底，主要用于快速识别明确危险信号。</p>
 */
public final class RiskLexicon {

    private static final List<String> HIGH_RISK_WORDS = List.of(
            "suicide", "kill myself", "self harm", "self-harm", "end my life", "hurt myself",
            "hurt others", "want to die", "do not want to live", "don't want to live",
            "cannot keep going", "can't keep going", "no reason to live", "wish i could disappear",
            "i might hurt myself", "i might hurt someone", "immediate danger"
    );

    private static final List<String> CONSULT_WORDS = List.of(
            "panic attack", "anxiety", "anxious", "stress", "pressure", "overwhelmed",
            "depress", "depressed", "low mood", "sad", "insomnia", "cannot sleep",
            "can't sleep", "panic", "lonely", "breakup", "hopeless", "helpless",
            "worthless", "no motivation", "burnout", "emotion", "counseling", "counsellor",
            "counselor", "mental health", "relationship", "roommate conflict"
    );

    private RiskLexicon() {
    }

    public static boolean hasHighRiskSignal(String text) {
        return containsAny(text, HIGH_RISK_WORDS);
    }

    public static boolean hasConsultSignal(String text) {
        return containsAny(text, CONSULT_WORDS);
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
