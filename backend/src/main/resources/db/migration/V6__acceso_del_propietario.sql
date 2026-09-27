-- Acceso del propietario con passkeys y sesiones en PostgreSQL (docs/STACK.md, "Identidad";
-- docs/MODELO_DATOS.md, pieza 3). Las cuatro primeras tablas son los esquemas oficiales de
-- Spring Security y Spring Session para PostgreSQL; se crean aquí porque las tablas son cosa de Flyway.

-- 1. Usuarios de WebAuthn (Spring Security). Solo habrá uno: el propietario.
--    "id" es el identificador interno (user handle), aleatorio; "name" es el nombre de usuario.
CREATE TABLE user_entities (
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);

-- 2. Passkeys registradas (Spring Security): la clave pública de cada una, nunca la privada.
CREATE TABLE user_credentials (
    credential_id                VARCHAR(1000) NOT NULL,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BYTEA         NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL,
    PRIMARY KEY (credential_id)
);

-- 3. Tokens de un solo uso para el enlace de alta y de recuperación (Spring Security).
--    El esquema oficial usa varchar_ignorecase, un tipo de HSQLDB; en PostgreSQL es VARCHAR.
CREATE TABLE one_time_tokens (
    token_value VARCHAR(36) NOT NULL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL
);

-- 4. Sesiones (Spring Session JDBC). Guardarlas aquí permite al comando "acceso" cerrarlas todas.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- 5. El propietario de la instalación (L-11): quién puede acceder a todos los datos.
--    Se identifica por el user handle de sus passkeys, nunca por nombre ni correo.
CREATE TABLE owner_account (
    user_handle VARCHAR(1000) PRIMARY KEY REFERENCES user_entities (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Una sola fila: índice único sobre una constante. Una segunda fila choca con la primera (L-12).
CREATE UNIQUE INDEX owner_account_una_sola_fila ON owner_account ((true));
