package dk.ek.setlistgpt.groq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GroqClient {
    private final WebClient webClient;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public GroqClient(WebClient webClient,
                      @Value("${app.model}") String model,
                      @Value("${app.max_tokens:300}") int maxTokens,
                      @Value("${app.temperature:0.8}") double temperature) {
        this.webClient = webClient;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public Map<String, Object> generateChatCompletion(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", new Object[] { Map.of("role", "user", "content", userPrompt) },
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        return webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}