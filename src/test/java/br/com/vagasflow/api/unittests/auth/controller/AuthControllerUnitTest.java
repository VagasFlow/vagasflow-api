package br.com.vagasflow.api.unittests.auth.controller;

import br.com.vagasflow.api.auth.controller.AuthController;
import br.com.vagasflow.api.auth.service.JwtAuthFilter;
import br.com.vagasflow.api.auth.service.JwtService;
import br.com.vagasflow.api.auth.service.OAuth2SuccessHandler;
import br.com.vagasflow.api.auth.service.OAuth2UserServiceImpl;
import br.com.vagasflow.api.shared.config.SecurityConfig;
import br.com.vagasflow.api.shared.exception.ApiErrorResponse;
import br.com.vagasflow.api.user.dto.UserResponse;
import br.com.vagasflow.api.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(value = AuthController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, JwtAuthFilter.class}
))
@ActiveProfiles("test")
@DisplayName("AuthController - Cookie-based BFF Auth")
public class AuthControllerUnitTest {

    @Autowired
    private MockMvcTester mvcTester;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OAuth2UserServiceImpl oAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/logout").permitAll()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, res, authEx) -> {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                var body = new ApiErrorResponse(
                                        HttpStatus.UNAUTHORIZED.value(),
                                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                        "Authentication required",
                                        req.getRequestURI()
                                );
                                objectMapper.writeValue(res.getOutputStream(), body);
                            })
                    )
                    .build();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me - Requisicao autenticada")
    class AuthenticatedRequest {

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("deve retornar 200 com dados completos do usuario autenticado")
        void shouldReturn200WithUserData() {
            var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            var expectedResponse = new UserResponse(
                    userId,
                    "lucas@github.com",
                    "Lucas Dev",
                    "https://avatars.githubusercontent.com/u/123",
                    "FREE"
            );
            given(userService.findById(userId)).willReturn(expectedResponse);

            assertThat(mvcTester.get().uri("/api/v1/auth/me"))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(UserResponse.class)
                    .satisfies(response -> {
                        assertThat(response.id()).isEqualTo(userId);
                        assertThat(response.email()).isEqualTo("lucas@github.com");
                        assertThat(response.name()).isEqualTo("Lucas Dev");
                        assertThat(response.avatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/123");
                        assertThat(response.plan()).isEqualTo("FREE");
                    });
        }

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("deve retornar Content-Type application/json")
        void shouldReturnJsonContentType() {
            var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            given(userService.findById(userId)).willReturn(
                    new UserResponse(userId, "lucas@github.com", "Lucas Dev", null, "FREE")
            );

            assertThat(mvcTester.get().uri("/api/v1/auth/me"))
                    .hasStatusOk()
                    .hasContentTypeCompatibleWith(
                            org.springframework.http.MediaType.APPLICATION_JSON);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me - Requisicao sem autenticacao")
    class UnauthenticatedRequest {

        @Test
        @DisplayName("deve retornar 401 quando nao ha cookie de sessao")
        void shouldReturn401WhenNoSessionCookie() {
            assertThat(mvcTester.get().uri("/api/v1/auth/me"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("deve retornar 401 com cookie de nome incorreto")
        void shouldReturn401WithWrongCookieName() {
            assertThat(mvcTester.get().uri("/api/v1/auth/me")
                    .cookie(new Cookie("WRONG_COOKIE", "some.jwt.token")))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutEndpoint {

        @Test
        @DisplayName("deve retornar 204 e cookie de sessao com maxAge=0")
        void shouldReturn204AndClearSessionCookie() {
            assertThat(mvcTester.post().uri("/api/v1/auth/logout"))
                    .hasStatus(HttpStatus.NO_CONTENT)
                    .cookies()
                    .containsKey(OAuth2SuccessHandler.SESSION_COOKIE_NAME);
        }

        @Test
        @DisplayName("deve ser acessivel sem autenticacao")
        void shouldBeAccessibleWithoutAuth() {
            assertThat(mvcTester.post().uri("/api/v1/auth/logout"))
                    .hasStatus(HttpStatus.NO_CONTENT);
        }
    }
}
