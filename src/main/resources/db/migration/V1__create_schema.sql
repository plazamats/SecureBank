CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    verification_token_hash CHAR(64) NULL,
    verification_token_expires_at DATETIME(6) NULL,
    reset_token_hash CHAR(64) NULL,
    reset_token_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_login_attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_verification_token ON users (verification_token_hash);
CREATE INDEX idx_users_reset_token ON users (reset_token_hash);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

CREATE TABLE bank_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(10) NOT NULL,
    owner_id BIGINT NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'ZAR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_bank_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_bank_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_bank_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_bank_accounts_type CHECK (account_type IN ('SAVINGS', 'CHECKING')),
    CONSTRAINT chk_bank_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_bank_accounts_owner ON bank_accounts (owner_id);

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(40) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,
    source_balance_after DECIMAL(19, 2) NULL,
    destination_balance_after DECIMAL(19, 2) NULL,
    description VARCHAR(255) NULL,
    initiated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_transactions_reference UNIQUE (reference),
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_destination FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_initiator FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT chk_transactions_balances CHECK (
        (source_balance_after IS NULL OR source_balance_after >= 0)
        AND (destination_balance_after IS NULL OR destination_balance_after >= 0)
    ),
    CONSTRAINT chk_transactions_accounts CHECK (
        (type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
        OR (type = 'TRANSFER' AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_transactions_source_created ON transactions (source_account_id, created_at);
CREATE INDEX idx_transactions_destination_created ON transactions (destination_account_id, created_at);
CREATE INDEX idx_transactions_type_created ON transactions (type, created_at);
CREATE INDEX idx_transactions_created ON transactions (created_at);
CREATE INDEX idx_transactions_initiator ON transactions (initiated_by);

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    actor_email VARCHAR(255) NULL,
    action VARCHAR(50) NOT NULL,
    success BOOLEAN NOT NULL,
    details VARCHAR(500) NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
