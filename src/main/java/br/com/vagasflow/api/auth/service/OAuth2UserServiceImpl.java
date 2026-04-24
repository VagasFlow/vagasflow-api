package br.com.vagasflow.api.auth.service;

import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserServiceImpl.class);

    private final UserService userService;
    private final RestClient restClient;

    public OAuth2UserServiceImpl(UserService userService, RestClient.Builder restClientBuilder) {
        this.userService = userService;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String accessToken = userRequest.getAccessToken().getTokenValue();

        OAuthUserInfo request = extractUserInfo(registrationId, oAuth2User, accessToken);
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

    private OAuthUserInfo extractUserInfo(String registrationId, OAuth2User oAuth2User, String accessToken) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        return switch (registrationId.toLowerCase()) {
            case "github" -> {
                String email = (String) attrs.get("email");

                if (email == null || email.isBlank()) {
                    email = fetchGitHubPrimaryEmail(accessToken);
                }

                if (email == null || email.isBlank()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("email_not_found"),
                            "Não foi possível obter o e-mail da conta GitHub."
                    );
                }

                String name = (String) attrs.get("name");
                if (name == null || name.isBlank()) {
                    name = (String) attrs.get("login");
                }

                yield new OAuthUserInfo(
                        email,
                        name,
                        (String) attrs.get("avatar_url"),
                        OAuthProvider.GITHUB,
                        String.valueOf(attrs.get("id"))
                );
            }

            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        };
    }

    private String fetchGitHubPrimaryEmail(String accessToken) {
        try {

            List<Map<String, Object>> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (emails != null) {
                return emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                        .map(e -> (String) e.get("email"))
                        .findFirst()
                        .orElseGet(() -> emails.stream()
                                .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                                .map(e -> (String) e.get("email"))
                                .findFirst()
                                .orElse(null));
            }
        } catch (Exception e) {
            log.error("Erro ao buscar e-mails do usuário no GitHub: {}", e.getMessage());
        }
        return null;
    }
}