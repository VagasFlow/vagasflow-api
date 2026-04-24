package br.com.vagasflow.api.auth.service;

import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.service.UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OidcUserServiceImpl extends OidcUserService {

    private final UserService userService;

    public OidcUserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Map<String, Object> attrs = oidcUser.getAttributes();

        OAuthUserInfo info = new OAuthUserInfo(
                (String) attrs.get("email"),
                (String) attrs.get("name"),
                (String) attrs.get("picture"),
                OAuthProvider.GOOGLE,
                (String) attrs.get("sub")
        );

        UserEntity user = userService.upsertFromOAuth2(info);

        Map<String, Object> customAttrs = Map.of(
                "id", user.getId().toString(),
                "email", user.getEmail(),
                "name", user.getName(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "plan", user.getPlan().name(),
                "sub", (String) attrs.get("sub")
        );

        return new DefaultOidcUser(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        ) {
            @Override
            public Map<String, Object> getAttributes() {
                return customAttrs;
            }

            @Override
            public String getName() {
                return customAttrs.get("id").toString();
            }
        };
    }
}
