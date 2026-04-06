package br.com.vagasflow.api.auth.service;

import br.com.vagasflow.api.shared.exception.ResourceNotFoundException;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.cors.allowed-origins}") String frontendUrl) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String userId = oAuth2User.getAttribute("id");
        UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String token = jwtService.generateToken(user);
        String redirectUrl = frontendUrl + "/auth/callback?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
