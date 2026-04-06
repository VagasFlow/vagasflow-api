package br.com.vagasflow.api.user.service;

import br.com.vagasflow.api.shared.exception.ResourceNotFoundException;
import br.com.vagasflow.api.user.domain.UserEntity;
import br.com.vagasflow.api.user.domain.UserPlan;
import br.com.vagasflow.api.user.dto.OAuthUserInfo;
import br.com.vagasflow.api.user.dto.UserResponse;
import br.com.vagasflow.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity upsertFromOAuth2(OAuthUserInfo request) {
        UserEntity user = userRepository
                .findByProviderAndProviderId(request.provider(), request.providerId())
                .map(existing -> {
                    existing.setName(request.name());
                    existing.setAvatarUrl(request.avatarUrl());
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setEmail(request.email());
                    newUser.setName(request.name());
                    newUser.setAvatarUrl(request.avatarUrl());
                    newUser.setProvider(request.provider());
                    newUser.setProviderId(request.providerId());
                    newUser.setPlan(UserPlan.FREE);
                    newUser.setCreatedAt(Instant.now());
                    newUser.setUpdatedAt(Instant.now());
                    return newUser;
                });

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getPlan().name()
        );
    }
}
