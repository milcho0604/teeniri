package com.beyond.teenkiri.user.service;


import com.beyond.teenkiri.user.domain.User;
import com.beyond.teenkiri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import javax.annotation.PostConstruct;
import java.util.Collections;

import java.util.List;
import java.util.UUID;


@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Autowired
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String NICKNAME_SET = "used_nicknames";

    @PostConstruct
    public void initializeNicknameSetInRedis() {
        List<String> allNicknames = userRepository.findAllNicknames();
        allNicknames.forEach(nickname -> redisTemplate.opsForSet().add(NICKNAME_SET, nickname));
    }


    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        // OAuth2UserSevice를 통해 가져온 OAuth2User의 attribute를 저장
        OAuthAttributes attributes = OAuthAttributes.of(registrationId,
                userNameAttributeName, oAuth2User.getAttributes());

        System.out.println("서비스입니다!!!!!!!!!!! 왓나요!!!!!!!!!!1");
        System.out.println(attributes);

        User user = null;
        if ("kakao".equals(registrationId)) {
            user = kakaoSaveOrUpdate(attributes);
        }else {
            user = saveOrUpdate(attributes);
        }
        if ("naver".equals(registrationId)) {
            user = naverSaveOrUpdate(attributes);
        }else {
            user = saveOrUpdate(attributes);
        }



        if (user.getName() == null){
            String temp = "이름을 변경해주세요";
            user.updateName(temp);
        }
        if (user.getNickname() == null) {
            String nick;
            do {
                nick = randomNickname();
            } while (isNicknameUsed(nick)); // 닉네임 중복 체크
            System.out.println(nick);

            user.updateNick(nick);
            saveNicknameToRedis(nick); // 중복이 아니라면 Redis에 저장
        }
        if (user.getPassword() == null) {
            String uuidPass = String.valueOf(UUID.randomUUID());
            user.updatePass(uuidPass);
        }

        if (user.getAddress() == null){
            String address = "임시주소입니다. 변경해주세요";
            user.updateAddress(address);
        }

        userRepository.save(user);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())),
                attributes.getAttributes(), attributes.getNameAttributeKey());
    }

    // 새로운 닉네임을 Redis에 저장하는 메서드
    private void saveNicknameToRedis(String nickname) {
        // Redis Set에 닉네임 저장
        redisTemplate.opsForSet().add(NICKNAME_SET, nickname);
    }

    // 닉네임 중복 여부를 확인하는 메서드
    private boolean isNicknameUsed(String nickname) {
        // Redis Set에서 닉네임 존재 여부 확인
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(NICKNAME_SET, nickname));
    }

    private User saveOrUpdate(OAuthAttributes attributes) {

        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> entity.update(attributes.getName()))
                .orElse(attributes.toEntity());
        return userRepository.save(user);
    }

    private User kakaoSaveOrUpdate(OAuthAttributes attributes) {
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> entity.update(attributes.getName()))
                .orElse(attributes.toEntity());

        return userRepository.save(user);
    }
    private User naverSaveOrUpdate(OAuthAttributes attributes){
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> entity.update(attributes.getName()))
                .orElse(attributes.toEntity());
        return  userRepository.save(user);
    }

    private String randomNickname() {
        List<String> adjective = List.of("민첩한", "야망 있는", "서투른", "너그러운", "용감한",
                "어설픈", "공손한", "정중한", "성실한", "관대한", "상냥한", "다정한", "외향적인", "깨발랄한");
        List<String> name = List.of("창현", "예나", "정은", "한아", "요한");
        String number = String.valueOf((int)(Math.random() * 99) + 1);
        Collections.shuffle(adjective);
        Collections.shuffle(name);
        return adjective.get(0) + name.get(0) + number;
    }
}