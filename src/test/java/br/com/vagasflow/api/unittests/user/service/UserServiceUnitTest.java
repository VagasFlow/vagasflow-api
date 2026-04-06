package br.com.vagasflow.api.unittests.user.service;

import br.com.vagasflow.api.user.domain.OAuthProvider;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.repository.UserRepository;
import br.com.vagasflow.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
public class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    public void setUp() {
        userService = new UserService(userRepository);
    }

    private UserEntity savedUserStub(UUID id, String email, String name, String avatarUrl, OAuthProvider provider,  String providerId) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setAvatarUrl(avatarUrl);
        user.setProvider(provider);
        user.setProviderId(providerId);
        user.setPlan(UserPlan.FREE);
        return user;
    }

    // Cenário 1 - Novo usuário no primeiro login

    @Nested
    @DisplayName("Primeiro login OAuth2")
    class FirstLogin {

        @Test
        @DisplayName("Deve criar um novo usuário quando não existe conta para o provider+providerId")
        void shouldCreateNewUserWhenNotFound() {
            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev",
                    "https://avatars.githubusercontent.com/u/123",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123"))
                    .willReturn(Optional.empty());

            var persisted = savedUserStub(
                    UUID.randomUUID(), "lucas@github.com", "Lucas Dev",
                    "https://avatars.githubusercontent.com/u/123",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.save(any(UserEntity.class))).willReturn(persisted);

            UserEntity result = userService.upsertFromOAuth2(request);

            assertThat(result.getEmail()).isEqualTo("lucas@github.com");
            assertThat(result.getName()).isEqualTo("Lucas Dev");
            assertThat(result.getProvider()).isEqualTo(OAuthProvider.GITHUB);
            assertThat(result.getProviderId()).isEqualTo("gh-provider-123");
            assertThat(result.getId()).isNotNull();

        }

        @Test
        @DisplayName("novo usuário deve ter plano FREE por padrão")
        void shouldAssignFreePlanByDefault() {
            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev", "https://avatar.url",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(any(), any()))
                    .willReturn(Optional.empty());

            var persisted = savedUserStub(
                    UUID.randomUUID(), "lucas@github.com", "Lucas Dev",
                    "https://avatar.url", OAuthProvider.GITHUB, "gh-provider-123"
            );
            given(userRepository.save(any(UserEntity.class))).willReturn(persisted);

            UserEntity result = userService.upsertFromOAuth2(request);

            assertThat(result.getPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("deve salvar o usuário com todos os dados do OAuth2")
        void shouldPersistAllOAuthFields() {
            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev", "https://avatar.url",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(any(), any()))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(UserEntity.class))).willAnswer(inv -> inv.getArgument(0));

            userService.upsertFromOAuth2(request);

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            UserEntity saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("lucas@github.com");
            assertThat(saved.getName()).isEqualTo("Lucas Dev");
            assertThat(saved.getAvatarUrl()).isEqualTo("https://avatar.url");
            assertThat(saved.getProvider()).isEqualTo(OAuthProvider.GITHUB);
            assertThat(saved.getProviderId()).isEqualTo("gh-provider-123");
            assertThat(saved.getPlan()).isEqualTo(UserPlan.FREE);
        }
    }

    // Cenário 2 - Login subsequente (usuário já existe)

    @Nested
    @DisplayName("Logins subsequentes")
    class SubsequentLogins {

        @Test
        @DisplayName("deve atualizar nome e avatar quando usuário já existe")
        void shouldUpdateNameAndAvatarForExistingUser() {
            UUID existingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

            var existing = savedUserStub(
                    existingId, "lucas@github.com", "Lucas Antigo",
                    "https://old-avatar.com", OAuthProvider.GITHUB, "gh-provider-123"
            );

            var infoWithNewData = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Novo Nome",
                    "https://new-avatar.com",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123"))
                    .willReturn(Optional.of(existing));

            var updated = savedUserStub(
                    existingId, "lucas@github.com", "Lucas Novo Nome",
                    "https://new-avatar.com", OAuthProvider.GITHUB, "gh-provider-123"
            );
            given(userRepository.save(any(UserEntity.class))).willReturn(updated);

            UserEntity result = userService.upsertFromOAuth2(infoWithNewData);

            assertThat(result.getId()).isEqualTo(existingId);
            assertThat(result.getName()).isEqualTo("Lucas Novo Nome");
            assertThat(result.getAvatarUrl()).isEqualTo("https://new-avatar.com");
        }

        @Test
        @DisplayName("não deve alterar o plano do usuário em logins subsequentes")
        void shouldNotChangePlanOnUpdate() {
            UUID existingId = UUID.randomUUID();

            var existingPro = savedUserStub(
                    existingId, "lucas@github.com", "Lucas Dev",
                    "https://avatar.com", OAuthProvider.GITHUB, "gh-provider-123"
            );
            existingPro.setPlan(UserPlan.PRO);

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123"))
                    .willReturn(Optional.of(existingPro));
            given(userRepository.save(any(UserEntity.class))).willAnswer(inv -> inv.getArgument(0));

            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev Updated", "https://new-avatar.com",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            userService.upsertFromOAuth2(request);

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPlan()).isEqualTo(UserPlan.PRO);
        }

        @Test
        @DisplayName("não deve alterar o id do usuário em logins subsequentes")
        void shouldPreserveUserIdOnUpdate() {
            UUID existingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

            var existing = savedUserStub(
                    existingId, "lucas@github.com", "Lucas Dev",
                    "https://avatar.com", OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123"))
                    .willReturn(Optional.of(existing));
            given(userRepository.save(any(UserEntity.class))).willAnswer(inv -> inv.getArgument(0));

            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev Updated", "https://new-avatar.com",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            userService.upsertFromOAuth2(request);

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(existingId);
        }
    }

    // Cenário 3 - Isolamento por provider (não vincular por email)

    @Nested
    @DisplayName("Isolamento por provider")
    class ProviderIsolation {

        @Test
        @DisplayName("deve criar usuário separado para o mesmo email em providers diferentes")
        void shouldCreateSeparateUserForSameEmailDifferentProvider() {
            var googleInfo = new OAuthUserInfo(
                    "lucas@gmail.com", "Lucas Dev", "https://google-avatar.com",
                    OAuthProvider.GOOGLE, "google-provider-456"
            );

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-provider-456"))
                    .willReturn(Optional.empty());

            var newGoogleUser = savedUserStub(
                    UUID.randomUUID(), "lucas@gmail.com", "Lucas Dev",
                    "https://google-avatar.com", OAuthProvider.GOOGLE, "google-provider-456"
            );
            given(userRepository.save(any(UserEntity.class))).willReturn(newGoogleUser);

            userService.upsertFromOAuth2(googleInfo);

            // Vínculo deve ser feito por (provider + providerId), não por email
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("a busca deve usar provider + providerId como chave de identificação")
        void shouldLookupByProviderAndProviderIdNotByEmail() {
            var request = new OAuthUserInfo(
                    "lucas@github.com", "Lucas Dev", "https://avatar.com",
                    OAuthProvider.GITHUB, "gh-provider-123"
            );

            given(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123"))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(UserEntity.class))).willAnswer(inv -> inv.getArgument(0));

            userService.upsertFromOAuth2(request);

            verify(userRepository).findByProviderAndProviderId(OAuthProvider.GITHUB, "gh-provider-123");
            verify(userRepository, never()).findByEmail(any());
        }
    }

}
