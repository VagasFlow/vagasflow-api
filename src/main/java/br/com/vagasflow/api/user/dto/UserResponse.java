package br.com.vagasflow.api.user.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        String plan
) {}
