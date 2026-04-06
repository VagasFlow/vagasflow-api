package br.com.vagasflow.api.unittests.auth.service;

import br.com.vagasflow.api.auth.service.JwtService;
import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService")
public class JwtServiceUnitTest {

    private static final String TEST_SECRET =
            "test-secret-key-that-is-long-enough-for-hs256-algorithm-32chars!!";

    private JwtService jwtService;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 7);

        testUser = new UserEntity();
        testUser.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        testUser.setEmail("lucas@github.com");
        testUser.setName("Lucas Dev");
        testUser.setPlan(UserPlan.FREE);
        testUser.setProvider(OAuthProvider.GITHUB);
        testUser.setProviderId("gh-provider-123");
    }

    // Cenário 1 - Geração de token

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("deve retornar um token JWT não-vazio")
        void shouldReturnNonBlankToken() {
            String token = jwtService.generateToken(testUser);

            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("deve incluir userId (sub) no token")
        void shouldEmbedUserIdAsSubject() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractUserId(token))
                    .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        }

        @Test
        @DisplayName("deve incluir email nas claims do token")
        void shouldEmbedEmailClaim() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractEmail(token)).isEqualTo("lucas@github.com");
        }

        @Test
        @DisplayName("deve incluir plano nas claims do token")
        void shouldEmbedPlanClaim() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractPlan(token)).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("token gerado para usuário PRO deve conter plano PRO")
        void shouldEmbedProPlanWhenUserIsPro() {
            testUser.setPlan(UserPlan.PRO);

            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractPlan(token)).isEqualTo(UserPlan.PRO);
        }
    }

    // Cenário 2 - Validação de token

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("deve retornar true para token válido recém-gerado")
        void shouldReturnTrueForFreshValidToken() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("deve retornar false para token com assinatura adulterada")
        void shouldReturnFalseForTamperedSignature() {
            String token = jwtService.generateToken(testUser);
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "adulteracao";

            assertThat(jwtService.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para token expirado")
        void shouldReturnFalseForExpiredToken() {
            String expiredToken = buildExpiredToken();

            assertThat(jwtService.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para string vazia")
        void shouldReturnFalseForBlankString() {
            assertThat(jwtService.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para formato JWT inválido")
        void shouldReturnFalseForMalformedJwt() {
            assertThat(jwtService.validateToken("nao.e.um.jwt.valido")).isFalse();
        }

        @Test
        @DisplayName("deve retornar false para token com prefixo Bearer incluído por engano")
        void shouldReturnFalseWhenBearerPrefixIncluded() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.validateToken("Bearer " + token)).isFalse();
        }
    }

    // Cenário 3 - Extração de claims

    @Nested
    @DisplayName("Extração de claims")
    class ClaimExtraction {

        @Test
        @DisplayName("deve extrair userId correto do subject do token")
        void shouldExtractCorrectUserId() {
            String token = jwtService.generateToken(testUser);

            UUID extracted = jwtService.extractUserId(token);

            assertThat(extracted).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        }

        @Test
        @DisplayName("deve extrair email correto do token")
        void shouldExtractCorrectEmail() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractEmail(token)).isEqualTo("lucas@github.com");
        }

        @Test
        @DisplayName("deve extrair plano correto do token")
        void shouldExtractCorrectPlan() {
            String token = jwtService.generateToken(testUser);

            assertThat(jwtService.extractPlan(token)).isEqualTo(UserPlan.FREE);
        }
    }

    // Helper - gera token já expirado via JWT diretamente
    private String buildExpiredToken() {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(testUser.getId().toString())
                .claim("email", testUser.getEmail())
                .claim("name", testUser.getName())
                .claim("plan", testUser.getPlan().name())
                .issuedAt(Date.from(Instant.now().minus(10, ChronoUnit.DAYS)))
                .expiration(Date.from(Instant.now().minus(3, ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

}
