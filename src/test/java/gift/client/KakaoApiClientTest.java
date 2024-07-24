package gift.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.config.KakaoProperties;
import gift.dto.KakaoUserResponse;
import gift.exception.BusinessException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class KakaoApiClientTest {

    @Autowired
    private KakaoProperties kakaoProperties;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private MockWebServer mockWebServer;

    private KakaoApiClient kakaoApiClient;

    @BeforeEach
    public void setUp() {
        mockWebServer = new MockWebServer();
        kakaoProperties = new KakaoProperties(
                "clientId",
                "http://localhost:8080/oauth/kakao/callback",
                "https://kauth.kakao.com/oauth/authorize",
                mockWebServer.url("/oauth/token").toString(),
                mockWebServer.url("/v2/user/me").toString()
        );
        kakaoApiClient = new KakaoApiClient(kakaoProperties, webClientBuilder);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    public void 액세스_토큰_가져오기_성공() throws Exception {
        String authorizationCode = "authCode";
        String accessToken = "accessToken";

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"" + accessToken + "\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        String result = kakaoApiClient.getAccessToken(authorizationCode);
        assertEquals(accessToken, result);
    }

    @Test
    public void 유저_정보_가져오기_성공() throws Exception {
        String accessToken = "accessToken";
        KakaoUserResponse kakaoUserResponse = new KakaoUserResponse(12345L, new KakaoUserResponse.Properties("nickname"));
        ObjectMapper objectMapper = new ObjectMapper();
        String kakaoUserResponseJson = objectMapper.writeValueAsString(kakaoUserResponse);

        mockWebServer.enqueue(new MockResponse()
                .setBody(kakaoUserResponseJson)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        KakaoUserResponse result = kakaoApiClient.getUserInfo(accessToken);
        assertEquals(kakaoUserResponse.getId(), result.getId());
        assertEquals(kakaoUserResponse.getProperties().getNickname(), result.getProperties().getNickname());
    }

    @Test
    public void 액세스_토큰_가져오기_실패() {
        String authorizationCode = "authCode";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"invalid_grant\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThrows(BusinessException.class, () -> kakaoApiClient.getAccessToken(authorizationCode));
    }

    @Test
    public void 유저_정보_가져오기_실패() {
        String accessToken = "accessToken";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"invalid_token\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThrows(BusinessException.class, () -> kakaoApiClient.getUserInfo(accessToken));
    }
}
