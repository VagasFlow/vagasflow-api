package br.com.vagasflow.api.integrationtests.user.service;

import br.com.vagasflow.api.integrationtests.support.IntegrationTestBase;
import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.dto.UserResponse;
import br.com.vagasflow.api.user.repository.UserRepository;
import br.com.vagasflow.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserService - Integration Test com PostgreSQL")
class UserServiceTest extends IntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("upsertFromOAuth2()")
    class UpsertFromOAuth2 {

        @Test
        @DisplayName("deve criar novo usuario no primeiro login via GitHub")
        void shouldCreateNewUserOnFirstGithubLogin() {
            var info = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev",
                    "https://avatars.githubusercontent.com/u/123",
                    OAuthProvider.GITHUB, "gh-12345"
            );

            UserEntity result = userService.upsertFromOAuth2(info);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getEmail()).isEqualTo("lucas@github.com");
            assertThat(result.getName()).isEqualTo("Lucas Dev");
            assertThat(result.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/123");
            assertThat(result.getProvider()).isEqualTo(OAuthProvider.GITHUB);
            assertThat(result.getProviderId()).isEqualTo("gh-12345");
            assertThat(result.getPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("deve criar novo usuario no primeiro login via Google")
        void shouldCreateNewUserOnFirstGoogleLogin() {
            var info = new OAuthUserInfo(
                    "lucas@gmail.com", "Lucas Google",
                    "https://lh3.googleusercontent.com/photo",
                    OAuthProvider.GOOGLE, "google-sub-789"
            );

            UserEntity result = userService.upsertFromOAuth2(info);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
            assertThat(result.getProviderId()).isEqualTo("google-sub-789");
            assertThat(result.getPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("deve atualizar nome e avatar em login subsequente")
        void shouldUpdateNameAndAvatarOnSubsequentLogin() {
            var firstLogin = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Antigo",
                    "https://old-avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            userService.upsertFromOAuth2(firstLogin);

            var secondLogin = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Novo",
                    "https://new-avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            UserEntity result = userService.upsertFromOAuth2(secondLogin);

            assertThat(result.getName()).isEqualTo("Lucas Novo");
            assertThat(result.getAvatarUrl()).isEqualTo("https://new-avatar.com");
        }

        @Test
        @DisplayName("nao deve alterar plano em login subsequente")
        void shouldNotChangePlanOnSubsequentLogin() {
            var info = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev",
                    "https://avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            UserEntity created = userService.upsertFromOAuth2(info);

            created.setPlan(UserPlan.PRO);
            userRepository.saveAndFlush(created);

            UserEntity result = userService.upsertFromOAuth2(info);

            assertThat(result.getPlan()).isEqualTo(UserPlan.PRO);
        }

        @Test
        @DisplayName("deve preservar o mesmo ID em logins subsequentes")
        void shouldPreserveIdOnSubsequentLogin() {
            var info = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev",
                    "https://avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            UserEntity first = userService.upsertFromOAuth2(info);
            UserEntity second = userService.upsertFromOAuth2(info);

            assertThat(second.getId()).isEqualTo(first.getId());
        }

        @Test
        @DisplayName("deve criar usuarios separados para providers diferentes")
        void shouldCreateSeparateUsersForDifferentProviders() {
            var githubInfo = new OAuthUserInfo(
                    "lucas-gh@email.com", "Lucas GitHub",
                    "https://github-avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            var googleInfo = new OAuthUserInfo(
                    "lucas-google@email.com", "Lucas Google",
                    "https://google-avatar.com",
                    OAuthProvider.GOOGLE, "google-sub-789"
            );

            UserEntity githubUser = userService.upsertFromOAuth2(githubInfo);
            UserEntity googleUser = userService.upsertFromOAuth2(googleInfo);

            assertThat(githubUser.getId()).isNotEqualTo(googleUser.getId());
            assertThat(githubUser.getProvider()).isEqualTo(OAuthProvider.GITHUB);
            assertThat(googleUser.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("deve persistir dados no banco real com Flyway migration")
        void shouldPersistDataWithFlywayMigration() {
            var info = new OAuthUserInfo(
                    "persist@test.com", "Persist User",
                    "https://avatar.com",
                    OAuthProvider.GITHUB, "gh-persist-001"
            );

            userService.upsertFromOAuth2(info);

            assertThat(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-persist-001"))
                    .isPresent();
            assertThat(userRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar UserResponse para usuario existente")
        void shouldReturnUserResponseForExistingUser() {
            var info = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev",
                    "https://avatar.com",
                    OAuthProvider.GITHUB, "gh-12345"
            );
            UserEntity created = userService.upsertFromOAuth2(info);

            UserResponse response = userService.findById(created.getId());

            assertThat(response.id()).isEqualTo(created.getId());
            assertThat(response.email()).isEqualTo("lucas@github.com");
            assertThat(response.name()).isEqualTo("Lucas Dev");
            assertThat(response.avatarUrl()).isEqualTo("https://avatar.com");
            assertThat(response.plan()).isEqualTo("FREE");
        }

        @Test
        @DisplayName("deve lancar excecao para usuario inexistente")
        void shouldThrowExceptionForNonExistentUser() {
            var nonExistentId = java.util.UUID.randomUUID();

            assertThatThrownBy(() -> userService.findById(nonExistentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
