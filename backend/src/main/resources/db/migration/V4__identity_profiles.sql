-- M2: users and child profiles (spec: Data model, FR-1 to FR-3).
-- Users are provisioned on first sign-in from the identity provider's subject.
-- Roles come from the identity provider on each sign-in and are not stored here.

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    oidc_subject  VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(320),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A profile holds a display label only (e.g. "Child 1"): no name or date of
-- birth (spec: data minimization). Deleting a profile is a soft delete so the
-- acknowledgment audit (M3) can keep pointing at it.
CREATE TABLE child_profile (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES app_user (id),
    label              VARCHAR(40) NOT NULL CHECK (length(btrim(label)) > 0),
    -- Incremented on every avoid-list change; acknowledgment tokens (M3) bind to it
    -- so a changed avoid list voids earlier acknowledgments (FR-9).
    avoid_list_version INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    row_version        BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX child_profile_user_idx ON child_profile (user_id) WHERE deleted_at IS NULL;

CREATE TABLE avoid_list_entry (
    profile_id  UUID        NOT NULL REFERENCES child_profile (id),
    allergen_id VARCHAR(64) NOT NULL REFERENCES allergen (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (profile_id, allergen_id)
);
