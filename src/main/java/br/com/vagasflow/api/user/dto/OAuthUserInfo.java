package br.com.vagasflow.api.user.dto;

import br.com.vagasflow.api.user.domain.OAuthProvider;

public record OAuthUserInfo(
        String email,
        String name,
        String avatarUrl,
        OAuthProvider provider,
        String providerId
) {}
