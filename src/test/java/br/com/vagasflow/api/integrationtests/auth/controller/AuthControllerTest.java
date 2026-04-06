package br.com.vagasflow.api.integrationtests.auth.controller;

import br.com.vagasflow.api.auth.service.JwtService;
import br.com.vagasflow.api.integrationtests.support.IntegrationTestBase;
import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import br.com.vagasflow.api.user.dto.UserResponse;
import br.com.vagasflow.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthController - Integration Test com PostgreSQL + JWT")
class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setEmail("lucas@github.com");
        testUser.setName("Lucas Dev");
        testUser.setAvatarUrl("https://avatars.githubusercontent.com/u/123");
        testUser.setProvider(OAuthProvider.GITHUB);
        testUser.setProviderId("gh-12345");
        testUser.setPlan(UserPlan.FREE);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.saveAndFlush(testUser);

        validToken = jwtService.generateToken(testUser);
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me - Requisicao autenticada")
    class AuthenticatedRequest {

        @Test
        @DisplayName("deve retornar 200 com dados do usuario autenticado via JWT")
        void shouldReturn200WithAuthenticatedUserData() {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(validToken);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(testUser.getId());
            assertThat(response.getBody().email()).isEqualTo("lucas@github.com");
            assertThat(response.getBody().name()).isEqualTo("Lucas Dev");
            assertThat(response.getBody().avatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/123");
            assertThat(response.getBody().plan()).isEqualTo("FREE");
        }

        @Test
        @DisplayName("deve retornar Content-Type application/json")
        void shouldReturnJsonContentType() {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(validToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getHeaders().getContentType())
                    .isNotNull()
                    .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
        }

        @Test
        @DisplayName("deve retornar plano PRO para usuario PRO")
        void shouldReturnProPlanForProUser() {
            testUser.setPlan(UserPlan.PRO);
            testUser = userRepository.saveAndFlush(testUser);
            String proToken = jwtService.generateToken(testUser);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(proToken);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().plan()).isEqualTo("PRO");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me - Requisicao sem autenticacao")
    class UnauthenticatedRequest {

        @Test
        @DisplayName("deve retornar 401 sem header Authorization")
        void shouldReturn401WithoutAuthorizationHeader() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/auth/me", String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("deve retornar 401 com token invalido")
        void shouldReturn401WithInvalidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth("token.invalido.aqui");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("deve retornar 401 com header Authorization sem prefixo Bearer")
        void shouldReturn401WithoutBearerPrefix() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Endpoints publicos")
    class PublicEndpoints {

        @Test
        @DisplayName("deve permitir acesso ao health check sem autenticacao")
        void shouldAllowHealthCheckWithoutAuth() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/actuator/health", String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Fluxo completo JWT")
    class FullJwtFlow {

        @Test
        @DisplayName("deve validar fluxo completo: gerar token -> autenticar -> retornar dados")
        void shouldValidateFullJwtFlow() {
            UserEntity newUser = new UserEntity();
            newUser.setEmail("full-flow@test.com");
            newUser.setName("Full Flow User");
            newUser.setAvatarUrl("https://avatar.com/full");
            newUser.setProvider(OAuthProvider.GOOGLE);
            newUser.setProviderId("google-full-flow-001");
            newUser.setPlan(UserPlan.FREE);
            newUser.setCreatedAt(Instant.now());
            newUser.setUpdatedAt(Instant.now());
            newUser = userRepository.saveAndFlush(newUser);

            String token = jwtService.generateToken(newUser);

            assertThat(jwtService.validateToken(token)).isTrue();
            assertThat(jwtService.extractUserId(token)).isEqualTo(newUser.getId());
            assertThat(jwtService.extractEmail(token)).isEqualTo("full-flow@test.com");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    "/api/v1/auth/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().email()).isEqualTo("full-flow@test.com");
            assertThat(response.getBody().name()).isEqualTo("Full Flow User");
        }
    }
}
