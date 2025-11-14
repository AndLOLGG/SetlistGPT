package dk.ek.setlistgpt.getsongbpm;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class GetSongBpmClient {

    private final WebClient webClient;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public GetSongBpmClient(WebClient getSongBpmWebClient) {
        this.webClient = getSongBpmWebClient;
    }

    /**
     * Calls a search/track endpoint with optional query parameters.
     * Returns an empty map on error or timeout. Replace return type with DTO after inspecting API schema.
     */
    public Map<String, Object> searchTracks(Map<String, String> queryParams) {
        final Map<String, String> params = queryParams == null ? new HashMap<>() : queryParams;

        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/tracks");
                        params.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(MAP_TYPE);
                        } else {
                            return response.createException()
                                    .flatMap(Mono::error);
                        }
                    })
                    .block(BLOCK_TIMEOUT);
        } catch (Exception e) {

            return Collections.emptyMap();
        }
    }
}