package com.multimodalAgent.agent.service.multimodal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.EmotionLabel;
import com.multimodalAgent.agent.domain.RiskLevel;
import com.multimodalAgent.agent.service.PsychologyAssessment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
/**
 * 多模态情绪融合引擎。
 */
public class MultimodalFusionService {

    private final multimodalAgentProperties properties;
    private final ObjectMapper objectMapper;

    public MultimodalFusionService(multimodalAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MultimodalAnalysis fuse(String userText, List<MultimodalSignal> signals) {
        if (signals.isEmpty()) {
            PsychologyAssessment assessment = new PsychologyAssessment(
                    EmotionLabel.NORMAL,
                    0.0,
                    RiskLevel.LOW,
                    0.6,
                    "No multimodal signal.");
            return new MultimodalAnalysis(userText, userText, assessment, signals, "No multimodal attachment.", "[]");
        }

        double fusedScore = signals.stream()
                .mapToDouble(signal -> signal.score() * weight(signal.modality()))
                .sum();
        double confidence = signals.stream()
                .mapToDouble(signal -> signal.confidence() * weight(signal.modality()))
                .sum();
        EmotionLabel emotion = strongestEmotion(signals, fusedScore);
        RiskLevel risk = riskFromScore(fusedScore);
        if (signals.stream().anyMatch(signal -> signal.emotion() == EmotionLabel.HIGH_RISK && signal.confidence() >= 0.75)) {
            risk = RiskLevel.HIGH;
            emotion = EmotionLabel.HIGH_RISK;
            fusedScore = Math.max(fusedScore, 4.0);
        }

        String summary = "Multimodal fusion: " + signals.stream()
                .map(signal -> signal.modality() + "=" + signal.emotion() + "(" + String.format("%.1f", signal.score()) + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("none")
                + ", weightedScore=" + String.format("%.2f", fusedScore)
                + ", risk=" + risk + ".";
        String tagsJson = tagsJson(signals, fusedScore, risk);
        PsychologyAssessment assessment = new PsychologyAssessment(
                emotion,
                fusedScore,
                risk,
                Math.max(0.0, Math.min(1.0, confidence)),
                summary);
        String modelText = enrichForModel(userText, summary, signals);
        return new MultimodalAnalysis(userText, modelText, assessment, signals, summary, tagsJson);
    }

    private String enrichForModel(String userText, String summary, List<MultimodalSignal> signals) {
        String text = userText == null || userText.isBlank() ? "The student uploaded multimodal content and would like support." : userText;
        String evidence = signals.stream()
                .map(signal -> "- " + signal.modality() + ": " + signal.evidence())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return text + "\n\n[Background multimodal analysis]\n" + summary + "\n" + evidence;
    }

    private double weight(String modality) {
        multimodalAgentProperties.Multimodal multimodal = properties.getMultimodal();
        return switch (modality) {
            case "text" -> Math.max(0.0, multimodal.getTextWeight());
            case "audio" -> Math.max(0.0, multimodal.getAudioWeight());
            case "visual" -> Math.max(0.0, multimodal.getVisualWeight());
            default -> 0.1;
        };
    }

    private EmotionLabel strongestEmotion(List<MultimodalSignal> signals, double score) {
        return signals.stream()
                .max((left, right) -> Double.compare(left.score() * left.confidence(), right.score() * right.confidence()))
                .map(MultimodalSignal::emotion)
                .orElse(score >= 3.0 ? EmotionLabel.DEPRESSED : score >= 2.0 ? EmotionLabel.ANXIETY : EmotionLabel.NORMAL);
    }

    private RiskLevel riskFromScore(double score) {
        if (score >= 2.0) {
            return RiskLevel.HIGH;
        }
        if (score >= 1.0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String tagsJson(List<MultimodalSignal> signals, double fusedScore, RiskLevel risk) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("signals", signals);
            payload.put("fusedScore", fusedScore);
            payload.put("risk", risk);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
