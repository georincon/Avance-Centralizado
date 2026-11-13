-- Avisos en tiempo real: cada cambio en las tablas del esquema `auditoria` emite un NOTIFY por el canal
-- `auditoria_cambios`; la API lo escucha (LISTEN) y lo reenvía al dashboard (SSE).
SET search_path = auditoria, public;

CREATE OR REPLACE FUNCTION notificar_cambio() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    fila jsonb := to_jsonb(NEW);
    msg  jsonb := jsonb_build_object('tabla', TG_TABLE_NAME, 'op', TG_OP, 'wallet_id', fila->>'wallet_id');
BEGIN
    IF TG_TABLE_NAME = 'actividad' THEN
        msg := msg || jsonb_build_object(
            'actividad_id', NEW.actividad_id, 'tipo', NEW.tipo_actividad, 'resultado', NEW.resultado,
            'actividad', (SELECT nombre FROM tipo_actividad WHERE codigo = NEW.tipo_actividad));
    END IF;
    PERFORM pg_notify('auditoria_cambios', msg::text);
    RETURN NULL;
END $$;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['actividad','usuario','medio_contacto','wallet','dispositivo','sesion','credencial','contraparte']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON auditoria.%I', t || '_notificar', t);
        EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE ON auditoria.%I FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio()', t || '_notificar', t);
    END LOOP;
END $$;
