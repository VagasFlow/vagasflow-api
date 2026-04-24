package br.com.vagasflow.api.auth.service;

import br.com.vagasflow.api.shared.exception.ResourceNotFoundException;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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

    public static final String SESSION_COOKIE_NAME = "SESSION_TOKEN";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String frontendUrl;
    private final boolean secureCookie;
    private final int cookieMaxAgeSeconds;

    public OAuth2SuccessHandler(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.cors.allowed-origins}") String frontendUrl,
            @Value("${app.cookie.secure:true}") boolean secureCookie,
            @Value("${app.jwt.expiration-days}") int expirationDays) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl.split(",")[0].trim();
        this.secureCookie = secureCookie;
        this.cookieMaxAgeSeconds = expirationDays * 24 * 60 * 60;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String userId = oAuth2User.getAttribute("id");
        UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String token = jwtService.generateToken(user);
        addSessionCookie(response, token);

        String redirectUrl = frontendUrl + "/auth/callback";
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private void addSessionCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
