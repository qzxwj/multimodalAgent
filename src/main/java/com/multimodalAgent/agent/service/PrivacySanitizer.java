package com.multimodalAgent.agent.service;

import org.springframework.stereotype.Service;

@Service
/**
 * 输入隐私脱敏服务。
 *
 * <p>对发送给模型和评估链路的文本做轻量脱敏，降低敏感标识进入上下文的概率。</p>
 */
public class PrivacySanitizer {

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 模型侧只需要语义，不需要手机号、学号、证件号等敏感标识。
        String sanitized = text;
        sanitized = sanitized.replaceAll("1[3-9]\\d{9}", "[phone number]");
        sanitized = sanitized.replaceAll("(?i)(student\\s*id)[:：\\s]*[A-Za-z0-9_-]{6,20}", "$1:[student id]");
        sanitized = sanitized.replaceAll("(?i)(id\\s*card)[:：\\s]*[0-9xX]{15,18}", "$1:[id number]");
        sanitized = sanitized.replaceAll("(?i)my name is\\s+[A-Za-z][A-Za-z\\s'-]{1,40}", "my name is [name]");
        return sanitized;
    }
}
