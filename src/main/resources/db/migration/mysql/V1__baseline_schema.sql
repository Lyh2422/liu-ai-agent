CREATE TABLE IF NOT EXISTS user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(11),
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    grade VARCHAR(32),
    college VARCHAR(80),
    signature VARCHAR(300),
    avatar_url VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_username UNIQUE (username),
    CONSTRAINT uk_user_account_public_id UNIQUE (public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_conversations (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    app_type VARCHAR(16) NOT NULL,
    title VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_conversation_owner_app (user_id, app_type, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_turns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(36) NOT NULL,
    user_content TEXT NOT NULL,
    assistant_content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_turn_conversation (conversation_id, id),
    CONSTRAINT fk_chat_turn_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS conversation_memories (
    conversation_id VARCHAR(36) NOT NULL,
    summary TEXT NOT NULL,
    summarized_through_turn_id BIGINT NOT NULL,
    version BIGINT,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_conversation_memory_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_memory_facts (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    fact_type VARCHAR(32) NOT NULL,
    fact_key VARCHAR(96) NOT NULL,
    fact_value VARCHAR(500) NOT NULL,
    source_conversation_id VARCHAR(36) NOT NULL,
    source_turn_id BIGINT NOT NULL,
    confidence DOUBLE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_memory_fact_key UNIQUE (user_id, fact_key),
    INDEX idx_user_memory_fact_user_updated (user_id, updated_at),
    INDEX idx_user_memory_fact_source (source_conversation_id),
    CONSTRAINT fk_user_memory_fact_user FOREIGN KEY (user_id)
        REFERENCES user_accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_memory_fact_conversation FOREIGN KEY (source_conversation_id)
        REFERENCES chat_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id VARCHAR(36) NOT NULL,
    version BIGINT,
    title VARCHAR(120) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    builtin BOOLEAN NOT NULL,
    deleted BOOLEAN NOT NULL,
    updated_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS friendships (
    id VARCHAR(36) NOT NULL,
    lower_user_id BIGINT NOT NULL,
    higher_user_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_friendship_pair UNIQUE (lower_user_id, higher_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS social_chat_rooms (
    id VARCHAR(36) NOT NULL,
    type VARCHAR(12) NOT NULL,
    name VARCHAR(80),
    owner_id BIGINT,
    direct_key VARCHAR(48),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_room_direct_key UNIQUE (direct_key),
    INDEX idx_social_room_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS social_chat_members (
    id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    last_read_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_social_chat_member UNIQUE (room_id, user_id),
    INDEX idx_social_member_user (user_id),
    INDEX idx_social_member_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS social_chat_messages (
    id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_social_message_room_time (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
