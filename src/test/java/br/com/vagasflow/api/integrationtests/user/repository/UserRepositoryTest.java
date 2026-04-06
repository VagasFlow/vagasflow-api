package br.com.vagasflow.api.integrationtests.user.repository;

import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import br.com.vagasflow.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("UserRepository - Integration Test com PostgreSQL")
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withReuse(true);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity githubUser;
    private UserEntity googleUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        githubUser = new UserEntity();
        githubUser.setEmail("lucas@github.com");
        githubUser.setName("Lucas Dev");
        githubUser.setAvatarUrl("https://avatars.githubusercontent.com/u/123");
        githubUser.setProvider(OAuthProvider.GITHUB);
        githubUser.setProviderId("gh-12345");
        githubUser.setPlan(UserPlan.FREE);
        githubUser.setCreatedAt(Instant.now());
        githubUser.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(githubUser);

        googleUser = new UserEntity();
        googleUser.setEmail("lucas@google.com");
        googleUser.setName("Lucas Google");
        googleUser.setAvatarUrl("https://lh3.googleusercontent.com/photo");
        googleUser.setProvider(OAuthProvider.GOOGLE);
        googleUser.setProviderId("google-sub-789");
        googleUser.setPlan(UserPlan.FREE);
        googleUser.setCreatedAt(Instant.now());
        googleUser.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(googleUser);

        entityManager.clear();
    }

    @Nested
    @DisplayName("findByProviderAndProviderId()")
    class FindByProviderAndProviderId {

        @Test
        @DisplayName("deve encontrar usuário GitHub pelo provider e providerId")
        void shouldFindGithubUserByProviderAndProviderId() {
            Optional<UserEntity> result = userRepository
                    .findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-12345");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("lucas@github.com");
            assertThat(result.get().getProvider()).isEqualTo(OAuthProvider.GITHUB);
            assertThat(result.get().getProviderId()).isEqualTo("gh-12345");
        }

        @Test
        @DisplayName("deve encontrar usuário Google pelo provider e providerId")
        void shouldFindGoogleUserByProviderAndProviderId() {
            Optional<UserEntity> result = userRepository
                    .findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-789");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("lucas@google.com");
            assertThat(result.get().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("deve retornar vazio quando providerId nao existe")
        void shouldReturnEmptyWhenProviderIdNotFound() {
            Optional<UserEntity> result = userRepository
                    .findByProviderAndProviderId(OAuthProvider.GITHUB, "non-existent-id");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deve retornar vazio quando provider correto mas providerId errado")
        void shouldReturnEmptyForWrongProviderCombination() {
            Optional<UserEntity> result = userRepository
                    .findByProviderAndProviderId(OAuthProvider.GOOGLE, "gh-12345");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmail {

        @Test
        @DisplayName("deve encontrar usuário pelo email")
        void shouldFindUserByEmail() {
            Optional<UserEntity> result = userRepository.findByEmail("lucas@github.com");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Lucas Dev");
        }

        @Test
        @DisplayName("deve retornar vazio para email inexistente")
        void shouldReturnEmptyForNonExistentEmail() {
            Optional<UserEntity> result = userRepository.findByEmail("naoexiste@email.com");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Persistencia e constraints")
    class PersistenceAndConstraints {

        @Test
        @DisplayName("deve gerar UUID automaticamente ao persistir")
        void shouldGenerateUuidOnPersist() {
            UserEntity newUser = new UserEntity();
            newUser.setEmail("novo@email.com");
            newUser.setName("Novo User");
            newUser.setProvider(OAuthProvider.GITHUB);
            newUser.setProviderId("gh-novo-999");
            newUser.setPlan(UserPlan.FREE);
            newUser.setCreatedAt(Instant.now());
            newUser.setUpdatedAt(Instant.now());

            UserEntity saved = userRepository.saveAndFlush(newUser);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("deve persistir todos os campos corretamente no PostgreSQL")
        void shouldPersistAllFieldsCorrectly() {
            UserEntity user = new UserEntity();
            user.setEmail("complete@test.com");
            user.setName("Complete User");
            user.setAvatarUrl("https://avatar.url/img.png");
            user.setProvider(OAuthProvider.GOOGLE);
            user.setProviderId("google-complete-001");
            user.setPlan(UserPlan.PRO);
            user.setPlanExpiresAt(Instant.parse("2026-12-31T23:59:59Z"));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());

            UserEntity saved = userRepository.saveAndFlush(user);
            entityManager.clear();

            UserEntity found = userRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.getEmail()).isEqualTo("complete@test.com");
            assertThat(found.getName()).isEqualTo("Complete User");
            assertThat(found.getAvatarUrl()).isEqualTo("https://avatar.url/img.png");
            assertThat(found.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
            assertThat(found.getProviderId()).isEqualTo("google-complete-001");
            assertThat(found.getPlan()).isEqualTo(UserPlan.PRO);
            assertThat(found.getPlanExpiresAt()).isEqualTo(Instant.parse("2026-12-31T23:59:59Z"));
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("deve permitir avatar_url nulo")
        void shouldAllowNullAvatarUrl() {
            UserEntity user = new UserEntity();
            user.setEmail("noavatar@test.com");
            user.setName("No Avatar");
            user.setAvatarUrl(null);
            user.setProvider(OAuthProvider.GITHUB);
            user.setProviderId("gh-noavatar-001");
            user.setPlan(UserPlan.FREE);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());

            UserEntity saved = userRepository.saveAndFlush(user);

            assertThat(saved.getAvatarUrl()).isNull();
        }
    }
}
