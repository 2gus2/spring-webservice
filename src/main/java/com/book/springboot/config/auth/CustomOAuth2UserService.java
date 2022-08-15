package com.book.springboot.config.auth;

import com.book.springboot.config.auth.dto.OAuthAttributes;
import com.book.springboot.config.auth.dto.SessionUser;
import com.book.springboot.domain.user.User;
import com.book.springboot.domain.user.UserRepository;
import java.util.Collections;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final HttpSession httpSession;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        //현재 로그인 진행중인 서비스 구분(구글, 네이버 등)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        //OAuth2 로그인 시 키 값, primary key
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
            .getUserInfoEndpoint().getUserNameAttributeName();

        //OAuth2User의 정보를 담을 클래스
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        //SessionUser 생성, Session 저장
        /*
            User를 사용하지 않고 SessionUser를 추가로 생성하는 이유:
            1. User 클래스를 세션에 저장하기 위해서는 직렬화 과정이 필요
            2. 엔티티 클래스(User)는 OneToMany, ManyToMany와 같은 관계 형성이 유연해야 함
            3. 만약 새로운 관계가 형성될 때마다 자식 엔티티를 직렬화하게 된다면 성능 이슈, 부수적인 효과가 나타날 수 있음
            4. 따라서 직렬화 기능을 가진 dto를 별도로 만들어 관리하면 엔티티 클래스의 유연함과 dto의 독립성을 확보할 수 있음 -> 운영/유지보수 편리
        */
        User user = saveOrUpdate(attributes);
        httpSession.setAttribute("user", new SessionUser(user)); //'user'로 저장

        return new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())),
            attributes.getAttributes(),
            attributes.getNameAttributeKey());
    }

    private User saveOrUpdate(OAuthAttributes attributes) {
        User user = userRepository.findByEmail(attributes.getEmail())
            .map(entity -> entity.update(attributes.getName(), attributes.getPicture()))
            .orElse(attributes.toEntity());

        return userRepository.save(user);
    }

}
