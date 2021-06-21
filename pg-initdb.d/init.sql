CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL DEFAULT 'password',
    created_at TIMESTAMP,
    version INTEGER,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS posts (
    id UUID DEFAULT uuid_generate_v4(),
    title VARCHAR(255),
    content VARCHAR(255),
    status VARCHAR(255) DEFAULT 'DRAFT',
    author_id UUID REFERENCES users,
    created_at TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at TIMESTAMP,
    version INTEGER,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS comments (
    id UUID DEFAULT uuid_generate_v4(),
    content VARCHAR(255),
    post_id UUID REFERENCES posts ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    version INTEGER,
    PRIMARY KEY (id)
);
