-- Esquema para el emulador de la Dirección de Tránsito de Bucaramanga (licencias de conducción).
-- Ver README.md para el contexto completo de este fork.
--
-- Fuera de alcance por ahora: firma manuscrita, restricciones por categoría y datos de residencia.

CREATE TABLE IF NOT EXISTS licencias (
    id                              BIGSERIAL PRIMARY KEY,

    -- Titular (el número de la licencia en Colombia es el de la cédula)
    documento_numero                VARCHAR(20)   NOT NULL,
    apellidos                       VARCHAR(255)  NOT NULL,
    nombres                         VARCHAR(255)  NOT NULL,
    fecha_nacimiento                DATE          NOT NULL,
    lugar_nacimiento_ciudad         VARCHAR(255)  NOT NULL,
    lugar_nacimiento_departamento   VARCHAR(255),
    sexo                            CHAR(1)       NOT NULL,
    estatura_cm                     SMALLINT,
    grupo_sanguineo                 VARCHAR(3),
    nacionalidad                    CHAR(2)       NOT NULL DEFAULT 'CO',

    -- Licencia
    organismo_transito              VARCHAR(150)  NOT NULL DEFAULT 'Dirección de Tránsito de Bucaramanga',
    fecha_expedicion                DATE          NOT NULL,
    estado                          VARCHAR(12)   NOT NULL DEFAULT 'ACTIVA',
    usa_lentes                      BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Foto del titular (la de la licencia). Se guarda en la misma base para no añadir otro almacenamiento.
    foto_referencia                 BYTEA,
    foto_referencia_tipo            VARCHAR(50),

    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_licencias_sexo CHECK (sexo IN ('M', 'F')),
    CONSTRAINT chk_licencias_estado CHECK (estado IN ('ACTIVA', 'SUSPENDIDA', 'CANCELADA')),
    CONSTRAINT chk_licencias_grupo_sanguineo CHECK (
        grupo_sanguineo IS NULL OR grupo_sanguineo IN ('O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-')
    ),
    CONSTRAINT chk_licencias_estatura CHECK (estatura_cm IS NULL OR estatura_cm BETWEEN 50 AND 250)
);

-- Categorías habilitadas por titular (Ley 769 de 2002, art. 19): A1, A2, B1, B2, B3, C1, C2, C3.
CREATE TABLE IF NOT EXISTS licencia_categorias (
    id                  BIGSERIAL PRIMARY KEY,
    licencia_id         BIGINT      NOT NULL REFERENCES licencias (id) ON DELETE CASCADE,
    categoria           VARCHAR(2)  NOT NULL,
    fecha_expedicion    DATE        NOT NULL,
    fecha_vencimiento   DATE        NOT NULL,
    CONSTRAINT chk_licencia_categorias_categoria CHECK (categoria IN ('A1', 'A2', 'B1', 'B2', 'B3', 'C1', 'C2', 'C3')),
    CONSTRAINT chk_licencia_categorias_fechas CHECK (fecha_vencimiento > fecha_expedicion),
    CONSTRAINT uq_licencia_categoria UNIQUE (licencia_id, categoria)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_licencias_documento_numero ON licencias (documento_numero);

-- Login del panel admin (independiente de Keycloak, ver sección 5 del contexto).
CREATE TABLE IF NOT EXISTS admin_usuarios (
    id                  BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_usuarios_username ON admin_usuarios (username);
