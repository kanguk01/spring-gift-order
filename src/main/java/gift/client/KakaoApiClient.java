package gift.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gift.config.KakaoProperties;
import gift.dto.KakaoUserResponse;
import gift.exception.BusinessException;
import gift.exception.ErrorCode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;

@Component
public class KakaoApiClient {

    private final KakaoProperties kakaoProperties;
    private final WebClient webClient;

    public KakaoApiClient(KakaoProperties kakaoProperties) {
        this.kakaoProperties = kakaoProperties;
        this.webClient = createWebClient();
    }

    private WebClient createWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    public String getAccessToken(String authorizationCode) {
        String body = UriComponentsBuilder.newInstance()
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", kakaoProperties.getClientId())
                .queryParam("redirect_uri", kakaoProperties.getRedirectUri())
                .queryParam("code", authorizationCode)
                .build().toUriString().substring(1);

        return webClient.post()
                .uri(kakaoProperties.getTokenUrl())
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class).flatMap(bodyContent ->
                                Mono.error(new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "HTTP 오류: " + clientResponse.statusCode() + " - " + bodyContent))))
                .bodyToMono(String.class)
                .map(this::extractAccessToken)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "HTTP 오류: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
                })
                .onErrorResume(ReadTimeoutException.class, ex -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "네트워크 타임아웃 오류: " + ex.getMessage());
                })
                .onErrorResume(throwable -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "네트워크 오류: " + throwable.getMessage());
                })
                .block();
    }

    public KakaoUserResponse getUserInfo(String accessToken) {
        return webClient.get()
                .uri(kakaoProperties.getInfoUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class).flatMap(bodyContent ->
                                Mono.error(new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "HTTP 오류: " + clientResponse.statusCode() + " - " + bodyContent))))
                .bodyToMono(KakaoUserResponse.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "HTTP 오류: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
                })
                .onErrorResume(ReadTimeoutException.class, ex -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "네트워크 타임아웃 오류: " + ex.getMessage());
                })
                .onErrorResume(throwable -> {
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "네트워크 오류: " + throwable.getMessage());
                })
                .block();
    }

    private String extractAccessToken(String responseBody) {
        JsonNode jsonNode = parseJson(responseBody);
        return jsonNode.get("access_token").asText();
    }

    private JsonNode parseJson(String body) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "JSON 파싱 오류: " + e.getMessage());
        }
    }
}