-- ============================================================================
-- Centro de auditoría centralizado de las wallets del ecosistema Avance
-- Motor: PostgreSQL 15+  ·  Todo en español (tablas, columnas, restricciones)
--
-- Modelo: una "cuenta bancaria" por wallet. Cada usuario tiene su ficha
-- (datos personales + medios de contacto), sus wallets, sus sesiones y un
-- extracto histórico de ACTIVIDADES, solo de inserción (nada se edita ni se borra).
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS auditoria;
SET search_path = auditoria, public;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- búsquedas por texto parcial

-- ── Catálogos ───────────────────────────────────────────────────────────────
CREATE TABLE tipo_actividad (
    codigo        text PRIMARY KEY,
    nombre        text NOT NULL,
    categoria     text NOT NULL CHECK (categoria IN ('sesion','credencial','conexion','perfil','sistema')),
    descripcion   text NOT NULL,
    icono         text NOT NULL DEFAULT 'circle'
);
INSERT INTO tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES
 ('registro_usuario',      'Registro de usuario',        'perfil',     'La persona validó su identidad y creó su usuario en la wallet',     'user-plus'),
 ('actualizacion_perfil',  'Actualización de datos',     'perfil',     'Cambió o confirmó sus datos personales o de contacto',              'user-pen'),
 ('inicio_sesion',         'Inicio de sesión',           'sesion',     'Ingresó a la wallet con PIN o biometría',                           'log-in'),
 ('cierre_sesion',         'Cierre de sesión',           'sesion',     'Salió o se desconectó de la wallet',                                'log-out'),
 ('creacion_pin',          'Creación de PIN',            'sesion',     'Creó el PIN de acceso de la wallet',                                'key'),
 ('solicitud_credencial',  'Solicitud de credencial',    'credencial', 'Solicitó una credencial a un emisor',                               'download'),
 ('emision_credencial',    'Recepción de credencial',    'credencial', 'Recibió y guardó una credencial emitida',                           'badge-check'),
 ('eliminacion_credencial','Eliminación de credencial',  'credencial', 'Borró una credencial de su wallet',                                 'trash'),
 ('solicitud_presentacion','Solicitud de un verificador','conexion',   'Un verificador pidió datos a la wallet',                            'inbox'),
 ('presentacion_credencial','Presentación de credencial','conexion',   'Compartió datos de sus credenciales con un verificador',            'send'),
 ('conexion_proximidad',   'Conexión por proximidad',    'conexion',   'Conexión cara a cara (QR/NFC/BLE) con un verificador',              'wifi'),
 ('firma_documento',       'Firma de documento',         'conexion',   'Firmó un documento con su credencial',                              'pen-tool'),
 ('error_aplicacion',      'Error de la aplicación',     'sistema',    'Falla no asociada a un trámite concreto',                           'alert-triangle')
ON CONFLICT DO NOTHING;

-- ── Personas ────────────────────────────────────────────────────────────────
CREATE TABLE usuario (
    usuario_id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_documento      text NOT NULL DEFAULT 'CC',
    numero_documento    text NOT NULL,
    nombres             text NOT NULL,
    apellidos           text NOT NULL,
    nombre_completo     text GENERATED ALWAYS AS (nombres || ' ' || apellidos) STORED,
    fecha_nacimiento    date,
    lugar_nacimiento    text,
    genero              text,
    grupo_sanguineo     text,
    estatura_cm         smallint,
    fecha_expedicion    date,
    lugar_expedicion    text,
    estado              text NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo','suspendido','bloqueado')),
    validacion_decision text,                      -- resultado de la validación de identidad (APPROVED...)
    validacion_puntaje_rostro numeric(5,2),
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuario_documento_uk UNIQUE (tipo_documento, numero_documento)
);
CREATE INDEX usuario_nombre_trgm ON usuario USING gin (nombre_completo gin_trgm_ops);
CREATE INDEX usuario_documento_trgm ON usuario USING gin (numero_documento gin_trgm_ops);

CREATE TABLE medio_contacto (
    contacto_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    tipo            text NOT NULL CHECK (tipo IN ('celular','correo')),
    valor           text NOT NULL,
    verificado      boolean NOT NULL DEFAULT false,
    principal       boolean NOT NULL DEFAULT true,
    registrado_en   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT medio_contacto_uk UNIQUE (usuario_id, tipo, valor)
);
CREATE INDEX medio_contacto_valor_trgm ON medio_contacto USING gin (valor gin_trgm_ops);

CREATE TABLE wallet (
    wallet_id           uuid PRIMARY KEY,           -- lo genera la app la primera vez que se abre
    usuario_id          uuid REFERENCES usuario(usuario_id),
    estado              text NOT NULL DEFAULT 'activa' CHECK (estado IN ('activa','revocada','inactiva')),
    alta_completa       boolean NOT NULL DEFAULT false,   -- true cuando terminó el onboarding y se enlazó con su usuario
    primer_registro_en  timestamptz NOT NULL DEFAULT now(),
    alta_en             timestamptz,
    ultima_actividad_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX wallet_usuario_ix ON wallet (usuario_id);

-- El teléfono donde corre cada wallet (una fila por wallet; se actualiza cuando cambia algo, p. ej. la versión de la app).
CREATE TABLE dispositivo (
    wallet_id            uuid PRIMARY KEY REFERENCES wallet(wallet_id),
    identificador_android text,                     -- ANDROID_ID: propio de la app y del teléfono
    fabricante           text,
    marca                text,
    modelo               text,
    producto             text,
    hardware             text,
    version_android      text,
    nivel_api            smallint,
    parche_seguridad     text,
    version_app          text,
    codigo_version_app   integer,
    arquitectura         text,
    memoria_ram_mb       integer,
    pantalla             text,                      -- 1080x2400 @ 420 dpi
    idioma               text,
    zona_horaria         text,
    biometria_disponible boolean,
    bloqueo_pantalla     boolean,
    registrado_en        timestamptz NOT NULL DEFAULT now(),
    actualizado_en       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dispositivo_modelo_ix ON dispositivo (fabricante, modelo);

CREATE TABLE sesion (
    sesion_id        uuid PRIMARY KEY,              -- lo genera la app en cada ingreso
    wallet_id        uuid NOT NULL REFERENCES wallet(wallet_id),
    usuario_id       uuid REFERENCES usuario(usuario_id),
    metodo_autenticacion text,                      -- pin | biometria | pin_creado
    inicio_en        timestamptz NOT NULL,
    fin_en           timestamptz,
    motivo_cierre    text,                          -- usuario | segundo_plano | expiracion
    direccion_ip     inet
);
CREATE INDEX sesion_wallet_ix ON sesion (wallet_id, inicio_en DESC);

-- ── Contrapartes: emisores y verificadores con los que se conecta la wallet ──
CREATE TABLE contraparte (
    contraparte_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo            text NOT NULL CHECK (tipo IN ('emisor','verificador','desconocida')),
    identificador   text NOT NULL,                  -- URL del emisor o client_id/nombre del verificador
    nombre          text,
    primera_vez_en  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT contraparte_uk UNIQUE (tipo, identificador)
);

-- ── Credenciales que tiene cada wallet ──────────────────────────────────────
CREATE TABLE credencial (
    credencial_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       uuid NOT NULL REFERENCES wallet(wallet_id),
    tipo_credencial text NOT NULL,                  -- docType / vct (p. ej. eu.europa.ec.eudi.pid.1)
    nombre          text,
    emisor_id       uuid REFERENCES contraparte(contraparte_id),
    estado          text NOT NULL DEFAULT 'vigente' CHECK (estado IN ('vigente','eliminada','revocada','vencida')),
    recibida_en     timestamptz NOT NULL DEFAULT now(),
    eliminada_en    timestamptz
);
CREATE INDEX credencial_wallet_ix ON credencial (wallet_id);

-- ── El extracto: histórico de actividades (solo inserción) ──────────────────
CREATE TABLE actividad (
    actividad_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id       uuid NOT NULL UNIQUE,           -- idempotencia: la app puede reintentar el envío
    wallet_id       uuid NOT NULL REFERENCES wallet(wallet_id),
    usuario_id      uuid REFERENCES usuario(usuario_id),
    sesion_id       uuid REFERENCES sesion(sesion_id),
    tipo_actividad  text NOT NULL REFERENCES tipo_actividad(codigo),
    contraparte_id  uuid REFERENCES contraparte(contraparte_id),
    credencial_tipo text,
    ocurrido_en     timestamptz NOT NULL,           -- reloj de la wallet
    registrado_en   timestamptz NOT NULL DEFAULT now(),  -- llegada al centro
    resultado       text NOT NULL CHECK (resultado IN ('exitoso','fallido','cancelado','en_proceso')),
    tipo_error      text,                           -- red | autenticacion | validacion | emisor | verificador | usuario | interno
    codigo_error    text,
    mensaje_error   text,
    duracion_ms     integer,
    canal           text,                           -- internet | qr | nfc | ble | deep_link
    direccion_ip    inet,
    detalle         jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT actividad_error_coherente CHECK (resultado <> 'exitoso' OR tipo_error IS NULL)
);
CREATE INDEX actividad_wallet_ix    ON actividad (wallet_id, ocurrido_en DESC);
CREATE INDEX actividad_usuario_ix   ON actividad (usuario_id, ocurrido_en DESC);
CREATE INDEX actividad_fecha_ix     ON actividad (ocurrido_en DESC);
CREATE INDEX actividad_tipo_ix      ON actividad (tipo_actividad, ocurrido_en DESC);
CREATE INDEX actividad_resultado_ix ON actividad (resultado, ocurrido_en DESC);
CREATE INDEX actividad_contraparte_ix ON actividad (contraparte_id, ocurrido_en DESC);
CREATE INDEX actividad_mensaje_trgm ON actividad USING gin (mensaje_error gin_trgm_ops);

-- Qué elementos (SOLO NOMBRES de atributo, nunca valores) se compartieron o recibieron en una actividad.
CREATE TABLE actividad_elemento (
    actividad_id    bigint NOT NULL REFERENCES actividad(actividad_id),
    sentido         text NOT NULL CHECK (sentido IN ('compartido','recibido')),
    tipo_credencial text NOT NULL,
    elemento        text NOT NULL,                  -- nombre del atributo: family_name, birth_date...
    PRIMARY KEY (actividad_id, sentido, tipo_credencial, elemento)
);

-- ── Seguridad del propio centro ─────────────────────────────────────────────
-- Quién consultó qué en el dashboard (la auditoría de la auditoría).
CREATE TABLE consulta_dashboard (
    consulta_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consultado_en   timestamptz NOT NULL DEFAULT now(),
    direccion_ip    inet,
    ruta            text NOT NULL,
    parametros      jsonb
);

-- Solo inserción: ningún UPDATE ni DELETE sobre el extracto.
CREATE FUNCTION bloquear_modificacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción (auditoría): no se permite %', TG_TABLE_NAME, TG_OP;
END $$;
CREATE TRIGGER actividad_solo_insercion BEFORE UPDATE OR DELETE ON actividad
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();
CREATE TRIGGER actividad_elemento_solo_insercion BEFORE UPDATE OR DELETE ON actividad_elemento
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();
CREATE TRIGGER consulta_dashboard_solo_insercion BEFORE UPDATE OR DELETE ON consulta_dashboard
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();

-- ── Vistas de lectura para el dashboard ─────────────────────────────────────
CREATE VIEW v_wallet AS
SELECT w.wallet_id, w.estado, w.alta_completa, w.primer_registro_en, w.alta_en, w.ultima_actividad_en,
       w.usuario_id, u.nombre_completo, u.numero_documento,
       d.identificador_android, d.fabricante, d.marca, d.modelo, d.producto, d.hardware, d.version_android, d.nivel_api,
       d.parche_seguridad, d.version_app, d.codigo_version_app, d.arquitectura, d.memoria_ram_mb, d.pantalla, d.idioma,
       d.zona_horaria, d.biometria_disponible, d.bloqueo_pantalla,
       (SELECT count(*) FROM actividad a WHERE a.wallet_id = w.wallet_id)::int actividades,
       (SELECT count(*) FROM actividad a WHERE a.wallet_id = w.wallet_id AND a.resultado = 'fallido')::int fallidas,
       (SELECT count(*) FROM credencial c WHERE c.wallet_id = w.wallet_id AND c.estado = 'vigente')::int credenciales
FROM wallet w
LEFT JOIN usuario u ON u.usuario_id = w.usuario_id
LEFT JOIN dispositivo d ON d.wallet_id = w.wallet_id;

CREATE VIEW v_extracto AS
SELECT a.actividad_id, a.evento_id, a.ocurrido_en, a.registrado_en,
       a.wallet_id, a.usuario_id, u.nombre_completo, u.numero_documento,
       a.tipo_actividad, t.nombre AS actividad, t.categoria,
       c.tipo AS contraparte_tipo, c.nombre AS contraparte, c.identificador AS contraparte_identificador,
       a.credencial_tipo, a.resultado, a.tipo_error, a.codigo_error, a.mensaje_error,
       a.duracion_ms, a.canal, a.sesion_id, a.detalle
FROM actividad a
JOIN tipo_actividad t ON t.codigo = a.tipo_actividad
LEFT JOIN usuario u ON u.usuario_id = a.usuario_id
LEFT JOIN contraparte c ON c.contraparte_id = a.contraparte_id;

-- Rol de solo lectura para el dashboard y rol de escritura para la API de ingesta.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auditoria_lectura') THEN CREATE ROLE auditoria_lectura NOLOGIN; END IF;
END $$;
GRANT USAGE ON SCHEMA auditoria TO auditoria_lectura;
GRANT SELECT ON ALL TABLES IN SCHEMA auditoria TO auditoria_lectura;
GRANT INSERT ON consulta_dashboard TO auditoria_lectura;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA auditoria TO auditoria_lectura;
