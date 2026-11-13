-- ============================================================================
-- Auditoría de EMISORES y VERIFICADORES (el lado de los servicios).
--
-- Las wallets ya reportan lo que hacen (tabla `actividad`). Esta parte registra lo que ocurre
-- en el OTRO extremo: cada emisor cuando ofrece, autoriza o entrega una credencial, y cada
-- verificador cuando pide y valida una presentación; exitoso o con error.
--
-- Es solo monitoreo: los servicios REPORTAN (sin esperar respuesta y sin que un fallo del centro
-- los afecte). Solo inserción, como el extracto de las wallets. Es aditiva: no toca ninguna tabla
-- existente. Si la base ya existe, se aplica a mano:
--   docker exec -i auditoria-db psql -U auditoria -d auditoria < srv-auditoria/db/03-servicios.sql
-- ============================================================================
SET search_path = auditoria, public;

-- Un emisor o un verificador (portal, issuer del ecosistema, verifier…).
CREATE TABLE IF NOT EXISTS servicio (
    servicio_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rol             text NOT NULL CHECK (rol IN ('emisor','verificador')),
    identificador   text NOT NULL,                  -- URL pública del servicio (credential_issuer / verifier)
    nombre          text,                           -- nombre legible: «Banco Cívica», «Registraduría»…
    proyecto        text,                           -- carpeta del repositorio: verifiers/banco, eudi-srv-issuer-pid-transito…
    primera_vez_en  timestamptz NOT NULL DEFAULT now(),
    ultima_vez_en   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT servicio_uk UNIQUE (rol, identificador)
);

-- Catálogo de eventos de servicio.
CREATE TABLE IF NOT EXISTS tipo_evento_servicio (
    codigo       text PRIMARY KEY,
    rol          text NOT NULL CHECK (rol IN ('emisor','verificador')),
    nombre       text NOT NULL,
    fase         text NOT NULL,                     -- orden lógico del trámite (1 = primero)
    descripcion  text NOT NULL
);
INSERT INTO tipo_evento_servicio (codigo, rol, nombre, fase, descripcion) VALUES
 ('oferta_credencial',      'emisor',      'Oferta de credencial',        '1', 'El emisor preparó una oferta (QR o enlace) para que una wallet reciba una credencial'),
 ('autorizacion_emision',   'emisor',      'Autorización de la wallet',   '2', 'La wallet canjeó la oferta: se validó el código, el PIN, la atestación de la wallet y DPoP'),
 ('emision_credencial',     'emisor',      'Emisión de credencial',       '3', 'El emisor firmó y entregó (o rechazó entregar) la credencial'),
 ('revocacion_credencial',  'emisor',      'Revocación de credencial',    '4', 'El emisor anuló o reemplazó una credencial ya emitida'),
 ('solicitud_verificacion', 'verificador', 'Solicitud de verificación',   '1', 'El verificador abrió una transacción pidiendo datos a una wallet (QR o enlace)'),
 ('verificacion_credencial','verificador', 'Resultado de la verificación','2', 'El verificador validó (o rechazó) lo que presentó la wallet: firma, emisor de confianza, estado y prueba de posesión')
ON CONFLICT DO NOTHING;

-- El extracto de los servicios: una fila por cosa que ocurre. Solo inserción.
CREATE TABLE IF NOT EXISTS actividad_servicio (
    actividad_servicio_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id        uuid NOT NULL UNIQUE,            -- idempotencia: el servicio puede reintentar el envío
    servicio_id      uuid NOT NULL REFERENCES servicio(servicio_id),
    rol              text NOT NULL CHECK (rol IN ('emisor','verificador')),
    tipo_evento      text NOT NULL REFERENCES tipo_evento_servicio(codigo),
    ocurrido_en      timestamptz NOT NULL,            -- reloj del servicio
    registrado_en    timestamptz NOT NULL DEFAULT now(),
    resultado        text NOT NULL CHECK (resultado IN ('exitoso','fallido','cancelado','en_proceso')),
    tipo_error       text,                            -- red | autenticacion | validacion | confianza | revocacion | emisor | verificador | usuario | interno
    codigo_error     text,
    mensaje_error    text,
    referencia       text,                            -- id de la oferta / transacción: une los pasos de un mismo trámite
    credencial_tipo  text,                            -- vct / docType / configuración
    credencial_nombre text,
    sujeto_documento text,                            -- persona atendida, si el servicio la conoce (cédula)
    sujeto_nombre    text,
    usuario_id       uuid REFERENCES usuario(usuario_id),   -- enlazado por cédula si ya existe en el centro
    wallet_id        uuid,                            -- solo si el servicio sabe qué wallet es
    canal            text,                            -- qr | mismo_dispositivo | enlace | api
    duracion_ms      integer,
    direccion_ip     inet,
    detalle          jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT actividad_servicio_error_coherente CHECK (resultado <> 'exitoso' OR tipo_error IS NULL)
);
CREATE INDEX IF NOT EXISTS actsrv_rol_fecha_ix   ON actividad_servicio (rol, ocurrido_en DESC);
CREATE INDEX IF NOT EXISTS actsrv_servicio_ix    ON actividad_servicio (servicio_id, ocurrido_en DESC);
CREATE INDEX IF NOT EXISTS actsrv_resultado_ix   ON actividad_servicio (rol, resultado, ocurrido_en DESC);
CREATE INDEX IF NOT EXISTS actsrv_referencia_ix  ON actividad_servicio (referencia);
CREATE INDEX IF NOT EXISTS actsrv_usuario_ix     ON actividad_servicio (usuario_id);
CREATE INDEX IF NOT EXISTS actsrv_mensaje_trgm   ON actividad_servicio USING gin (mensaje_error gin_trgm_ops);

-- Nombres de los datos (NUNCA sus valores) que se pidieron, recibieron o emitieron.
CREATE TABLE IF NOT EXISTS actividad_servicio_elemento (
    actividad_servicio_id bigint NOT NULL REFERENCES actividad_servicio(actividad_servicio_id),
    sentido          text NOT NULL CHECK (sentido IN ('solicitado','recibido','emitido')),
    tipo_credencial  text NOT NULL,
    elemento         text NOT NULL,
    PRIMARY KEY (actividad_servicio_id, sentido, tipo_credencial, elemento)
);

DROP TRIGGER IF EXISTS actividad_servicio_solo_insercion ON actividad_servicio;
CREATE TRIGGER actividad_servicio_solo_insercion BEFORE UPDATE OR DELETE ON actividad_servicio
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();
DROP TRIGGER IF EXISTS actividad_servicio_elemento_solo_insercion ON actividad_servicio_elemento;
CREATE TRIGGER actividad_servicio_elemento_solo_insercion BEFORE UPDATE OR DELETE ON actividad_servicio_elemento
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();

-- Vista para el dashboard.
CREATE OR REPLACE VIEW v_actividad_servicio AS
SELECT a.actividad_servicio_id, a.evento_id, a.rol, a.ocurrido_en, a.registrado_en,
       a.servicio_id, s.nombre AS servicio, s.identificador AS servicio_identificador, s.proyecto,
       a.tipo_evento, t.nombre AS evento, t.fase,
       a.resultado, a.tipo_error, a.codigo_error, a.mensaje_error, a.referencia,
       a.credencial_tipo, a.credencial_nombre, a.sujeto_documento, COALESCE(a.sujeto_nombre, u.nombre_completo) AS sujeto_nombre,
       a.usuario_id, a.wallet_id, a.canal, a.duracion_ms, a.detalle
FROM actividad_servicio a
JOIN servicio s USING (servicio_id)
JOIN tipo_evento_servicio t ON t.codigo = a.tipo_evento
LEFT JOIN usuario u ON u.usuario_id = a.usuario_id;

-- Avisos en tiempo real (el dashboard ya escucha el canal `auditoria_cambios`).
CREATE OR REPLACE FUNCTION notificar_cambio() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    fila jsonb := to_jsonb(NEW);
    msg  jsonb := jsonb_build_object('tabla', TG_TABLE_NAME, 'op', TG_OP, 'wallet_id', fila->>'wallet_id');
BEGIN
    IF TG_TABLE_NAME = 'actividad' THEN
        msg := msg || jsonb_build_object(
            'actividad_id', NEW.actividad_id, 'tipo', NEW.tipo_actividad, 'resultado', NEW.resultado,
            'actividad', (SELECT nombre FROM tipo_actividad WHERE codigo = NEW.tipo_actividad));
    ELSIF TG_TABLE_NAME = 'actividad_servicio' THEN
        msg := msg || jsonb_build_object(
            'actividad_id', NEW.actividad_servicio_id, 'rol', NEW.rol, 'tipo', NEW.tipo_evento, 'resultado', NEW.resultado,
            'actividad', (SELECT nombre FROM tipo_evento_servicio WHERE codigo = NEW.tipo_evento),
            'servicio', (SELECT nombre FROM servicio WHERE servicio_id = NEW.servicio_id));
    END IF;
    PERFORM pg_notify('auditoria_cambios', msg::text);
    RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS actividad_servicio_notificar ON actividad_servicio;
CREATE TRIGGER actividad_servicio_notificar AFTER INSERT ON actividad_servicio
    FOR EACH ROW EXECUTE FUNCTION notificar_cambio();

-- Permisos: lectura para el dashboard.
GRANT SELECT ON servicio, tipo_evento_servicio, actividad_servicio, actividad_servicio_elemento, v_actividad_servicio TO auditoria_lectura;
