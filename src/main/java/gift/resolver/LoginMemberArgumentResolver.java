package gift.resolver;

import gift.entity.User;
import gift.entity.KakaoUser;
import gift.repository.UserRepository;
import gift.repository.KakaoUserRepository;
import gift.service.TokenService;
import gift.util.AuthorizationHeaderProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Map;
import java.util.Optional;

@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;
    private final KakaoUserRepository kakaoUserRepository;
    private final TokenService tokenService;
    private final AuthorizationHeaderProcessor authorizationHeaderProcessor;

    @Autowired
    public LoginMemberArgumentResolver(UserRepository userRepository, KakaoUserRepository kakaoUserRepository, TokenService tokenService, AuthorizationHeaderProcessor authorizationHeaderProcessor) {
        this.userRepository = userRepository;
        this.kakaoUserRepository = kakaoUserRepository;
        this.tokenService = tokenService;
        this.authorizationHeaderProcessor = authorizationHeaderProcessor;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(gift.annotation.LoginMember.class) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String token = authorizationHeaderProcessor.extractToken(request);
        Map<String, String> userInfo = tokenService.extractUserInfo(token);
        String userType = userInfo.get("userType");
        String id = userInfo.get("id");

        Optional<User> user;
        if ("kakao".equals(userType)) {
            Optional<KakaoUser> kakaoUser = kakaoUserRepository.findByKakaoId(Long.parseLong(id));
            user = kakaoUser.map(KakaoUser::getUser);
        } else {
            user = userRepository.findByEmail(id);
        }

        if (user.isEmpty()) {
            return null;
        }

        return user.get();
    }
}
