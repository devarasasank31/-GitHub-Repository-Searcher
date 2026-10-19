CREATE TABLE github_repositories (
    id           BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    owner        VARCHAR(255) NOT NULL,
    language     VARCHAR(100),
    stars        BIGINT       NOT NULL DEFAULT 0,
    forks        BIGINT       NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_github_repositories PRIMARY KEY (id),
    CONSTRAINT chk_github_repositories_stars CHECK (stars >= 0),
    CONSTRAINT chk_github_repositories_forks CHECK (forks >= 0)
);

CREATE INDEX idx_github_repositories_language ON github_repositories (LOWER(language));
CREATE INDEX idx_github_repositories_stars ON github_repositories (stars DESC);
CREATE INDEX idx_github_repositories_forks ON github_repositories (forks DESC);
CREATE INDEX idx_github_repositories_last_updated ON github_repositories (last_updated DESC);
