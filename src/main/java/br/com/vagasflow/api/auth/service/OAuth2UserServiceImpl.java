package br.com.vagasflow.api.auth.service;

import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.service.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserService userService;

    public OAuth2UserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthUserInfo request = extractUserInfo(registrationId, oAuth2User);
        UserEntity user = userService.upsertFromOAuth2(request);

        Map<String, Object> attributes = Map.of(
                "id", user.getId().toString(),
                "email", user.getEmail(),
                "name", user.getName(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "plan", user.getPlan().name()
        );

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                attributes,
                "id"
        );
    }

    private OAuthUserInfo extractUserInfo(String registrationId, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        return switch (registrationId.toLowerCase()) {
            case "github" -> new OAuthUserInfo(
                    (String) attrs.get("email"),
                    (String) attrs.get("name"),
                    (String) attrs.get("avatar_url"),
                    OAuthProvider.GITHUB,
                    String.valueOf(attrs.get("id"))
            );
            case "google" -> new OAuthUserInfo(
                    (String) attrs.get("email"),
                    (String) attrs.get("name"),
                    (String) attrs.get("picture"),
                    OAuthProvider.GOOGLE,
                    (String) attrs.get("sub")
            );
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        };
    }
}
