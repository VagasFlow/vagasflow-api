CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    provider        VARCHAR(20)  NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    plan            VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    plan_expires_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);
