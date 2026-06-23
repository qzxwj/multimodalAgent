package com.multimodalAgent.agent.service.multimodal;

import com.fasterxml.jackson.databind.JsonNode;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

@Component
/**
 * Whisper 语音转写客户端。
 *
 * <p>配置为 openai 且提供 API Key 时调用 Whisper；否则返回可演示的本地降级文本。</p>
 */
public class WhisperClient {

    private final multimodalAgentProperties properties;
    private final WebClient.Builder webClientBuilder;

    public WhisperClient(multimodalAgentProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<String> transcribe(FilePart audio) {
        multimodalAgentProperties.Whisper whisper = properties.getMultimodal().getWhisper();
        if (!"openai".equalsIgnoreCase(whisper.getMode()) || whisper.getApiKey() == null || whisper.getApiKey().isBlank()) {
            return Mono.just(mockTranscript(audio));
        }

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("model", whisper.getModel());
        body.asyncPart("file", audio.content(), org.springframework.core.io.buffer.DataBuffer.class)
                .filename(audio.filename())
                .contentType(audio.headers().getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : audio.headers().getContentType());

        return webClientBuilder.baseUrl(whisper.getBaseUrl())
                .build()
                .post()
                .uri("/v1/audio/transcriptions")
                .headers(headers -> headers.setBearerAuth(whisper.getApiKey()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.path("text").asText(""))
                .filter(text -> !text.isBlank())
                .onErrorReturn(mockTranscript(audio));
    }

    private String mockTranscript(FilePart audio) {
        String filename = audio == null ? "" : audio.filename().toLowerCase();
        if (filename.contains("risk") || filename.contains("crisis")) {
            return "Mock audio transcript: I feel like I cannot keep going.";
        }
        if (filename.contains("sad") || filename.contains("depress")) {
            return "Mock audio transcript: I have been feeling very low recently.";
        }
        if (filename.contains("anxious") || filename.contains("stress")) {
            return "Mock audio transcript: I have been anxious recently, and my sleep has not been good.";
        }
        return "Mock audio transcript: The student uploaded an audio file and would like to continue the support conversation.";
    }
}
