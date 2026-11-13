-- Esquema para el emulador de la Registraduría Nacional del Estado Civil.
-- Ver CONTEXTO-REGISTRADURIA.md para el contexto completo de este fork.
--
-- Deliberadamente fuera de alcance por ahora (MVP, ver sección 3 del contexto):
-- firma, huella índice derecho, código de barras / PDF417 del reverso.
-- Se podrán agregar más adelante si se implementa algún tipo de verificación
-- adicional sobre esos datos.

CREATE TABLE IF NOT EXISTS cedulas (
    id                              BIGSERIAL PRIMARY KEY,

    -- Frente de la cédula
    documento_numero                VARCHAR(20)   NOT NULL,
    apellidos                       VARCHAR(255)  NOT NULL,
    nombres                         VARCHAR(255)  NOT NULL,

    -- Reverso de la cédula
    fecha_nacimiento                DATE          NOT NULL,
    lugar_nacimiento_ciudad         VARCHAR(255)  NOT NULL,
    lugar_nacimiento_departamento   VARCHAR(255),
    sexo                            CHAR(1)       NOT NULL,
    estatura_cm                     SMALLINT,
    grupo_sanguineo                 VARCHAR(3),
    fecha_expedicion                DATE,
    lugar_expedicion                VARCHAR(255),

    -- No viene impresa en la cédula: nacionalidad asumida, fija para este emulador
    nacionalidad                    CHAR(2)       NOT NULL DEFAULT 'CO',

    -- Foto de referencia subida aparte (no la impresa en la cédula), para que
    -- la wallet la use en su comparación facial. Guardada en la misma base
    -- para no añadir otro almacenamiento al MVP.
    foto_referencia                 BYTEA,
    foto_referencia_tipo            VARCHAR(50),

    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_cedulas_sexo CHECK (sexo IN ('M', 'F')),
    CONSTRAINT chk_cedulas_grupo_sanguineo CHECK (
        grupo_sanguineo IS NULL OR grupo_sanguineo IN ('O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-')
    ),
    CONSTRAINT chk_cedulas_estatura CHECK (estatura_cm IS NULL OR estatura_cm BETWEEN 50 AND 250)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cedulas_documento_numero ON cedulas (documento_numero);

-- Login del panel admin (independiente de Keycloak, ver sección 5 del contexto).
CREATE TABLE IF NOT EXISTS admin_usuarios (
    id                  BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_usuarios_username ON admin_usuarios (username);
