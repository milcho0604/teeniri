package com.beyond.teenkiri.user.Handler;

import com.beyond.teenkiri.common.domain.DelYN;
import com.beyond.teenkiri.user.config.JwtTokenprovider;
import com.beyond.teenkiri.user.domain.DelUser;
import com.beyond.teenkiri.user.domain.User;
import com.beyond.teenkiri.user.repository.DelUserRepository;
import com.beyond.teenkiri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private final JwtTokenprovider jwtTokenprovider;
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final DelUserRepository delUserRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response
            , Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            if (kakaoAccount != null && kakaoAccount.containsKey("email")) {
                email = (String) kakaoAccount.get("email");
            }
            if (email == null) {
                Object naverObject = oAuth2User.getAttributes().get("response");
                Map<String, Object> naverAccount = (Map<String, Object>) naverObject;
                if (naverAccount != null && naverAccount.containsKey("email")) {
                    email = (String) naverAccount.get("email");
                }
            }
        }

        System.out.println("핸들러입니다!!! 넘오올까요?");
        System.out.println(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 소셜 회원입니다."));

        if (delUserRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("영구 정지된 계정입니다.");
        }

        if (user.getReportCount() >= 5) {
            user = user.deleteEmail(); // 이메일 삭제
            userRepository.save(user);

            // del_user 테이블에 이메일 삽입
            DelUser delUser = DelUser.toEntity(email);
            delUserRepository.save(delUser);

            throw new RuntimeException("해당 계정은 비활성화 상태입니다.");
        }

        Long getUserId = user.getId();
        Long userId = getUserId;

//        String userIdString = oAuth2User.getAttribute("sub");
//        Long userId = userIdString != null ? Long.valueOf(userIdString) : null;


        String token = jwtTokenprovider.googleToken(email, userId,"STUDENT");
        System.out.println("토큰은!!!!" + token);
        // 리다이렉트 URL 설정 (아래 먼저는 로컬호스트 환경에서, 두번째는 프론트 환경에서)
//        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:8082/loginSuccess")
        String targetUrl = UriComponentsBuilder.fromUriString("https://www.teenkiri.site/loginSuccess")
                .queryParam("token", token)
                .build().toUriString();

        // 리다이렉트 수행
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
