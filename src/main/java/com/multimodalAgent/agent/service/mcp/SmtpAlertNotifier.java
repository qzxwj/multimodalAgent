package com.multimodalAgent.agent.service.mcp;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.domain.AlertRecord;
import com.multimodalAgent.agent.domain.PsychologicalReport;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 邮件预警实现。
 *
 * <p>高风险报告触发后，把摘要信息发送给配置的辅导员或心理中心邮箱。</p>
 */
public class SmtpAlertNotifier implements AlertNotifier {

    private final JavaMailSender mailSender;
    private final multimodalAgentProperties properties;

    public SmtpAlertNotifier(JavaMailSender mailSender, multimodalAgentProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void notify(AlertRecord alertRecord, PsychologicalReport report) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMcp().getEmail().getFrom());
        message.setTo(alertRecord.getRecipient());
        message.setSubject("[High-Risk Mental Health Alert] Student %s needs attention".formatted(report.getUser().getUsername()));
        message.setText("""
                The system detected a high-risk mental-health signal in a student conversation. Please review and follow up promptly.

                Alert details:
                Report ID: %s
                User ID: %s
                Student: %s
                Conversation content: %s
                Emotion: %s
                Emotion score: %.2f
                Risk level: %s
                Assessment summary: %s

                """.formatted(
                report.getId(),
                report.getUser().getUsername(),
                report.getUser().getDisplayName(),
                report.getContent(),
                report.getEmotion(),
                report.getEmotionScore(),
                report.getRiskLevel(),
                report.getSummary()));
        mailSender.send(message);
    }
}
