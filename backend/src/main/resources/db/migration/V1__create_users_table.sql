CREATE TYPE user_role AS ENUM ('READER', 'COLLABORATOR', 'ADMIN');
CREATE TYPE user_status AS ENUM ('PENDING', 'ACTIVE', 'INACTIVE');

CREATE TABLE users
(
    id                   UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    email                VARCHAR(255)             NOT NULL UNIQUE,
    username             VARCHAR(100)             NOT NULL UNIQUE,
    password_hash        VARCHAR(255)             NOT NULL,
    avatar_url           VARCHAR(500),
    presentation_message TEXT                     NOT NULL,
    role                 user_role                NOT NULL DEFAULT 'READER',
    status               user_status              NOT NULL DEFAULT 'PENDING',
    quota_gb             DECIMAL(10, 2)           NOT NULL DEFAULT 2.00,
    last_access_at       TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
