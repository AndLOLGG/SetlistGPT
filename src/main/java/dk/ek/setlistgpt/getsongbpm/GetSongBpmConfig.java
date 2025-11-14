package dk.ek.setlistgpt.getsongbpm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(prefix = "app.getsongbpm", name = "url")
public class GetSongBpmConfig {

    @Bean
    public WebClient getSongBpmWebClient(
            @Value("${app.getsongbpm.url}") String url,
            @Value("${app.getsongbpm.api-key:}") String apiKey,
            @Value("${app.getsongbpm.api-header:Authorization}") String apiHeader) {

        WebClient.Builder b = WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            String headerValue = HttpHeaders.AUTHORIZATION.equalsIgnoreCase(apiHeader) ? "Bearer " + apiKey : apiKey;
            b.defaultHeader(apiHeader, headerValue);
        }
        return b.build();
    }
}
