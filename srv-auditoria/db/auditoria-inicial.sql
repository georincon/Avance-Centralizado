-- Base de datos del centro de auditoría (srv-auditoria) en su estado inicial:
-- estructura completa + catálogo de tipos de actividad. SIN datos de uso (usuarios, wallets, actividades, etc.).
-- Generado el 2026-11-10 desde la base en ejecución. Uso: psql -U auditoria -d auditoria -f auditoria-inicial.sql

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='auditoria_lectura') THEN CREATE ROLE auditoria_lectura NOLOGIN; END IF; END $$;

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: auditoria; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA auditoria;


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA auditoria;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA auditoria;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: bloquear_modificacion(); Type: FUNCTION; Schema: auditoria; Owner: -
--

CREATE FUNCTION auditoria.bloquear_modificacion() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción (auditoría): no se permite %', TG_TABLE_NAME, TG_OP;
END $$;


--
-- Name: notificar_cambio(); Type: FUNCTION; Schema: auditoria; Owner: -
--

CREATE FUNCTION auditoria.notificar_cambio() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: actividad; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.actividad (
    actividad_id bigint NOT NULL,
    evento_id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    usuario_id uuid,
    sesion_id uuid,
    tipo_actividad text NOT NULL,
    contraparte_id uuid,
    credencial_tipo text,
    ocurrido_en timestamp with time zone NOT NULL,
    registrado_en timestamp with time zone DEFAULT now() NOT NULL,
    resultado text NOT NULL,
    tipo_error text,
    codigo_error text,
    mensaje_error text,
    duracion_ms integer,
    canal text,
    direccion_ip inet,
    detalle jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT actividad_error_coherente CHECK (((resultado <> 'exitoso'::text) OR (tipo_error IS NULL))),
    CONSTRAINT actividad_resultado_check CHECK ((resultado = ANY (ARRAY['exitoso'::text, 'fallido'::text, 'cancelado'::text, 'en_proceso'::text])))
);


--
-- Name: actividad_actividad_id_seq; Type: SEQUENCE; Schema: auditoria; Owner: -
--

ALTER TABLE auditoria.actividad ALTER COLUMN actividad_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auditoria.actividad_actividad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: actividad_elemento; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.actividad_elemento (
    actividad_id bigint NOT NULL,
    sentido text NOT NULL,
    tipo_credencial text NOT NULL,
    elemento text NOT NULL,
    CONSTRAINT actividad_elemento_sentido_check CHECK ((sentido = ANY (ARRAY['compartido'::text, 'recibido'::text])))
);


--
-- Name: consulta_dashboard; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.consulta_dashboard (
    consulta_id bigint NOT NULL,
    consultado_en timestamp with time zone DEFAULT now() NOT NULL,
    direccion_ip inet,
    ruta text NOT NULL,
    parametros jsonb
);


--
-- Name: consulta_dashboard_consulta_id_seq; Type: SEQUENCE; Schema: auditoria; Owner: -
--

ALTER TABLE auditoria.consulta_dashboard ALTER COLUMN consulta_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auditoria.consulta_dashboard_consulta_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: contraparte; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.contraparte (
    contraparte_id uuid DEFAULT gen_random_uuid() NOT NULL,
    tipo text NOT NULL,
    identificador text NOT NULL,
    nombre text,
    primera_vez_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT contraparte_tipo_check CHECK ((tipo = ANY (ARRAY['emisor'::text, 'verificador'::text, 'desconocida'::text])))
);


--
-- Name: credencial; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.credencial (
    credencial_id uuid DEFAULT gen_random_uuid() NOT NULL,
    wallet_id uuid NOT NULL,
    tipo_credencial text NOT NULL,
    nombre text,
    emisor_id uuid,
    estado text DEFAULT 'vigente'::text NOT NULL,
    recibida_en timestamp with time zone DEFAULT now() NOT NULL,
    eliminada_en timestamp with time zone,
    CONSTRAINT credencial_estado_check CHECK ((estado = ANY (ARRAY['vigente'::text, 'eliminada'::text, 'revocada'::text, 'vencida'::text])))
);


--
-- Name: dispositivo; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.dispositivo (
    wallet_id uuid NOT NULL,
    identificador_android text,
    fabricante text,
    marca text,
    modelo text,
    producto text,
    hardware text,
    version_android text,
    nivel_api smallint,
    parche_seguridad text,
    version_app text,
    codigo_version_app integer,
    arquitectura text,
    memoria_ram_mb integer,
    pantalla text,
    idioma text,
    zona_horaria text,
    biometria_disponible boolean,
    bloqueo_pantalla boolean,
    registrado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: medio_contacto; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.medio_contacto (
    contacto_id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    tipo text NOT NULL,
    valor text NOT NULL,
    verificado boolean DEFAULT false NOT NULL,
    principal boolean DEFAULT true NOT NULL,
    registrado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT medio_contacto_tipo_check CHECK ((tipo = ANY (ARRAY['celular'::text, 'correo'::text])))
);


--
-- Name: sesion; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.sesion (
    sesion_id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    usuario_id uuid,
    metodo_autenticacion text,
    inicio_en timestamp with time zone NOT NULL,
    fin_en timestamp with time zone,
    motivo_cierre text,
    direccion_ip inet
);


--
-- Name: tipo_actividad; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.tipo_actividad (
    codigo text NOT NULL,
    nombre text NOT NULL,
    categoria text NOT NULL,
    descripcion text NOT NULL,
    icono text DEFAULT 'circle'::text NOT NULL,
    CONSTRAINT tipo_actividad_categoria_check CHECK ((categoria = ANY (ARRAY['sesion'::text, 'credencial'::text, 'conexion'::text, 'perfil'::text, 'sistema'::text])))
);


--
-- Name: usuario; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.usuario (
    usuario_id uuid DEFAULT gen_random_uuid() NOT NULL,
    tipo_documento text DEFAULT 'CC'::text NOT NULL,
    numero_documento text NOT NULL,
    nombres text NOT NULL,
    apellidos text NOT NULL,
    nombre_completo text GENERATED ALWAYS AS (((nombres || ' '::text) || apellidos)) STORED,
    fecha_nacimiento date,
    lugar_nacimiento text,
    genero text,
    grupo_sanguineo text,
    estatura_cm smallint,
    fecha_expedicion date,
    lugar_expedicion text,
    estado text DEFAULT 'activo'::text NOT NULL,
    validacion_decision text,
    validacion_puntaje_rostro numeric(5,2),
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT usuario_estado_check CHECK ((estado = ANY (ARRAY['activo'::text, 'suspendido'::text, 'bloqueado'::text])))
);


--
-- Name: v_extracto; Type: VIEW; Schema: auditoria; Owner: -
--

CREATE VIEW auditoria.v_extracto AS
 SELECT a.actividad_id,
    a.evento_id,
    a.ocurrido_en,
    a.registrado_en,
    a.wallet_id,
    a.usuario_id,
    u.nombre_completo,
    u.numero_documento,
    a.tipo_actividad,
    t.nombre AS actividad,
    t.categoria,
    c.tipo AS contraparte_tipo,
    c.nombre AS contraparte,
    c.identificador AS contraparte_identificador,
    a.credencial_tipo,
    a.resultado,
    a.tipo_error,
    a.codigo_error,
    a.mensaje_error,
    a.duracion_ms,
    a.canal,
    a.sesion_id,
    a.detalle
   FROM (((auditoria.actividad a
     JOIN auditoria.tipo_actividad t ON ((t.codigo = a.tipo_actividad)))
     LEFT JOIN auditoria.usuario u ON ((u.usuario_id = a.usuario_id)))
     LEFT JOIN auditoria.contraparte c ON ((c.contraparte_id = a.contraparte_id)));


--
-- Name: wallet; Type: TABLE; Schema: auditoria; Owner: -
--

CREATE TABLE auditoria.wallet (
    wallet_id uuid NOT NULL,
    usuario_id uuid,
    estado text DEFAULT 'activa'::text NOT NULL,
    alta_completa boolean DEFAULT false NOT NULL,
    primer_registro_en timestamp with time zone DEFAULT now() NOT NULL,
    alta_en timestamp with time zone,
    ultima_actividad_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT wallet_estado_check CHECK ((estado = ANY (ARRAY['activa'::text, 'revocada'::text, 'inactiva'::text])))
);


--
-- Name: v_wallet; Type: VIEW; Schema: auditoria; Owner: -
--

CREATE VIEW auditoria.v_wallet AS
 SELECT w.wallet_id,
    w.estado,
    w.alta_completa,
    w.primer_registro_en,
    w.alta_en,
    w.ultima_actividad_en,
    w.usuario_id,
    u.nombre_completo,
    u.numero_documento,
    d.identificador_android,
    d.fabricante,
    d.marca,
    d.modelo,
    d.producto,
    d.hardware,
    d.version_android,
    d.nivel_api,
    d.parche_seguridad,
    d.version_app,
    d.codigo_version_app,
    d.arquitectura,
    d.memoria_ram_mb,
    d.pantalla,
    d.idioma,
    d.zona_horaria,
    d.biometria_disponible,
    d.bloqueo_pantalla,
    (( SELECT count(*) AS count
           FROM auditoria.actividad a
          WHERE (a.wallet_id = w.wallet_id)))::integer AS actividades,
    (( SELECT count(*) AS count
           FROM auditoria.actividad a
          WHERE ((a.wallet_id = w.wallet_id) AND (a.resultado = 'fallido'::text))))::integer AS fallidas,
    (( SELECT count(*) AS count
           FROM auditoria.credencial c
          WHERE ((c.wallet_id = w.wallet_id) AND (c.estado = 'vigente'::text))))::integer AS credenciales
   FROM ((auditoria.wallet w
     LEFT JOIN auditoria.usuario u ON ((u.usuario_id = w.usuario_id)))
     LEFT JOIN auditoria.dispositivo d ON ((d.wallet_id = w.wallet_id)));


--
-- Name: actividad_elemento actividad_elemento_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad_elemento
    ADD CONSTRAINT actividad_elemento_pkey PRIMARY KEY (actividad_id, sentido, tipo_credencial, elemento);


--
-- Name: actividad actividad_evento_id_key; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_evento_id_key UNIQUE (evento_id);


--
-- Name: actividad actividad_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_pkey PRIMARY KEY (actividad_id);


--
-- Name: consulta_dashboard consulta_dashboard_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.consulta_dashboard
    ADD CONSTRAINT consulta_dashboard_pkey PRIMARY KEY (consulta_id);


--
-- Name: contraparte contraparte_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.contraparte
    ADD CONSTRAINT contraparte_pkey PRIMARY KEY (contraparte_id);


--
-- Name: contraparte contraparte_uk; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.contraparte
    ADD CONSTRAINT contraparte_uk UNIQUE (tipo, identificador);


--
-- Name: credencial credencial_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.credencial
    ADD CONSTRAINT credencial_pkey PRIMARY KEY (credencial_id);


--
-- Name: dispositivo dispositivo_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.dispositivo
    ADD CONSTRAINT dispositivo_pkey PRIMARY KEY (wallet_id);


--
-- Name: medio_contacto medio_contacto_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.medio_contacto
    ADD CONSTRAINT medio_contacto_pkey PRIMARY KEY (contacto_id);


--
-- Name: medio_contacto medio_contacto_uk; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.medio_contacto
    ADD CONSTRAINT medio_contacto_uk UNIQUE (usuario_id, tipo, valor);


--
-- Name: sesion sesion_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.sesion
    ADD CONSTRAINT sesion_pkey PRIMARY KEY (sesion_id);


--
-- Name: tipo_actividad tipo_actividad_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.tipo_actividad
    ADD CONSTRAINT tipo_actividad_pkey PRIMARY KEY (codigo);


--
-- Name: usuario usuario_documento_uk; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.usuario
    ADD CONSTRAINT usuario_documento_uk UNIQUE (tipo_documento, numero_documento);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (usuario_id);


--
-- Name: wallet wallet_pkey; Type: CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.wallet
    ADD CONSTRAINT wallet_pkey PRIMARY KEY (wallet_id);


--
-- Name: actividad_contraparte_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_contraparte_ix ON auditoria.actividad USING btree (contraparte_id, ocurrido_en DESC);


--
-- Name: actividad_fecha_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_fecha_ix ON auditoria.actividad USING btree (ocurrido_en DESC);


--
-- Name: actividad_mensaje_trgm; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_mensaje_trgm ON auditoria.actividad USING gin (mensaje_error auditoria.gin_trgm_ops);


--
-- Name: actividad_resultado_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_resultado_ix ON auditoria.actividad USING btree (resultado, ocurrido_en DESC);


--
-- Name: actividad_tipo_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_tipo_ix ON auditoria.actividad USING btree (tipo_actividad, ocurrido_en DESC);


--
-- Name: actividad_usuario_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_usuario_ix ON auditoria.actividad USING btree (usuario_id, ocurrido_en DESC);


--
-- Name: actividad_wallet_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX actividad_wallet_ix ON auditoria.actividad USING btree (wallet_id, ocurrido_en DESC);


--
-- Name: credencial_wallet_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX credencial_wallet_ix ON auditoria.credencial USING btree (wallet_id);


--
-- Name: dispositivo_modelo_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX dispositivo_modelo_ix ON auditoria.dispositivo USING btree (fabricante, modelo);


--
-- Name: medio_contacto_valor_trgm; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX medio_contacto_valor_trgm ON auditoria.medio_contacto USING gin (valor auditoria.gin_trgm_ops);


--
-- Name: sesion_wallet_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX sesion_wallet_ix ON auditoria.sesion USING btree (wallet_id, inicio_en DESC);


--
-- Name: usuario_documento_trgm; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX usuario_documento_trgm ON auditoria.usuario USING gin (numero_documento auditoria.gin_trgm_ops);


--
-- Name: usuario_nombre_trgm; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX usuario_nombre_trgm ON auditoria.usuario USING gin (nombre_completo auditoria.gin_trgm_ops);


--
-- Name: wallet_usuario_ix; Type: INDEX; Schema: auditoria; Owner: -
--

CREATE INDEX wallet_usuario_ix ON auditoria.wallet USING btree (usuario_id);


--
-- Name: actividad_elemento actividad_elemento_solo_insercion; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER actividad_elemento_solo_insercion BEFORE DELETE OR UPDATE ON auditoria.actividad_elemento FOR EACH ROW EXECUTE FUNCTION auditoria.bloquear_modificacion();


--
-- Name: actividad actividad_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER actividad_notificar AFTER INSERT OR UPDATE ON auditoria.actividad FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: actividad actividad_solo_insercion; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER actividad_solo_insercion BEFORE DELETE OR UPDATE ON auditoria.actividad FOR EACH ROW EXECUTE FUNCTION auditoria.bloquear_modificacion();


--
-- Name: consulta_dashboard consulta_dashboard_solo_insercion; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER consulta_dashboard_solo_insercion BEFORE DELETE OR UPDATE ON auditoria.consulta_dashboard FOR EACH ROW EXECUTE FUNCTION auditoria.bloquear_modificacion();


--
-- Name: contraparte contraparte_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER contraparte_notificar AFTER INSERT OR UPDATE ON auditoria.contraparte FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: credencial credencial_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER credencial_notificar AFTER INSERT OR UPDATE ON auditoria.credencial FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: dispositivo dispositivo_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER dispositivo_notificar AFTER INSERT OR UPDATE ON auditoria.dispositivo FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: medio_contacto medio_contacto_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER medio_contacto_notificar AFTER INSERT OR UPDATE ON auditoria.medio_contacto FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: sesion sesion_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER sesion_notificar AFTER INSERT OR UPDATE ON auditoria.sesion FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: usuario usuario_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER usuario_notificar AFTER INSERT OR UPDATE ON auditoria.usuario FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: wallet wallet_notificar; Type: TRIGGER; Schema: auditoria; Owner: -
--

CREATE TRIGGER wallet_notificar AFTER INSERT OR UPDATE ON auditoria.wallet FOR EACH ROW EXECUTE FUNCTION auditoria.notificar_cambio();


--
-- Name: actividad actividad_contraparte_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_contraparte_id_fkey FOREIGN KEY (contraparte_id) REFERENCES auditoria.contraparte(contraparte_id);


--
-- Name: actividad_elemento actividad_elemento_actividad_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad_elemento
    ADD CONSTRAINT actividad_elemento_actividad_id_fkey FOREIGN KEY (actividad_id) REFERENCES auditoria.actividad(actividad_id);


--
-- Name: actividad actividad_sesion_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_sesion_id_fkey FOREIGN KEY (sesion_id) REFERENCES auditoria.sesion(sesion_id);


--
-- Name: actividad actividad_tipo_actividad_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_tipo_actividad_fkey FOREIGN KEY (tipo_actividad) REFERENCES auditoria.tipo_actividad(codigo);


--
-- Name: actividad actividad_usuario_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES auditoria.usuario(usuario_id);


--
-- Name: actividad actividad_wallet_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.actividad
    ADD CONSTRAINT actividad_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES auditoria.wallet(wallet_id);


--
-- Name: credencial credencial_emisor_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.credencial
    ADD CONSTRAINT credencial_emisor_id_fkey FOREIGN KEY (emisor_id) REFERENCES auditoria.contraparte(contraparte_id);


--
-- Name: credencial credencial_wallet_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.credencial
    ADD CONSTRAINT credencial_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES auditoria.wallet(wallet_id);


--
-- Name: dispositivo dispositivo_wallet_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.dispositivo
    ADD CONSTRAINT dispositivo_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES auditoria.wallet(wallet_id);


--
-- Name: medio_contacto medio_contacto_usuario_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.medio_contacto
    ADD CONSTRAINT medio_contacto_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES auditoria.usuario(usuario_id) ON DELETE CASCADE;


--
-- Name: sesion sesion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.sesion
    ADD CONSTRAINT sesion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES auditoria.usuario(usuario_id);


--
-- Name: sesion sesion_wallet_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.sesion
    ADD CONSTRAINT sesion_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES auditoria.wallet(wallet_id);


--
-- Name: wallet wallet_usuario_id_fkey; Type: FK CONSTRAINT; Schema: auditoria; Owner: -
--

ALTER TABLE ONLY auditoria.wallet
    ADD CONSTRAINT wallet_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES auditoria.usuario(usuario_id);


--
-- Name: SCHEMA auditoria; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA auditoria TO auditoria_lectura;


--
-- Name: TABLE actividad; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.actividad TO auditoria_lectura;


--
-- Name: SEQUENCE actividad_actividad_id_seq; Type: ACL; Schema: auditoria; Owner: -
--

GRANT USAGE ON SEQUENCE auditoria.actividad_actividad_id_seq TO auditoria_lectura;


--
-- Name: TABLE actividad_elemento; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.actividad_elemento TO auditoria_lectura;


--
-- Name: TABLE consulta_dashboard; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT,INSERT ON TABLE auditoria.consulta_dashboard TO auditoria_lectura;


--
-- Name: SEQUENCE consulta_dashboard_consulta_id_seq; Type: ACL; Schema: auditoria; Owner: -
--

GRANT USAGE ON SEQUENCE auditoria.consulta_dashboard_consulta_id_seq TO auditoria_lectura;


--
-- Name: TABLE contraparte; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.contraparte TO auditoria_lectura;


--
-- Name: TABLE credencial; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.credencial TO auditoria_lectura;


--
-- Name: TABLE dispositivo; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.dispositivo TO auditoria_lectura;


--
-- Name: TABLE medio_contacto; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.medio_contacto TO auditoria_lectura;


--
-- Name: TABLE sesion; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.sesion TO auditoria_lectura;


--
-- Name: TABLE tipo_actividad; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.tipo_actividad TO auditoria_lectura;


--
-- Name: TABLE usuario; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.usuario TO auditoria_lectura;


--
-- Name: TABLE v_extracto; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.v_extracto TO auditoria_lectura;


--
-- Name: TABLE wallet; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.wallet TO auditoria_lectura;


--
-- Name: TABLE v_wallet; Type: ACL; Schema: auditoria; Owner: -
--

GRANT SELECT ON TABLE auditoria.v_wallet TO auditoria_lectura;


--
-- PostgreSQL database dump complete
--



-- ── Datos iniciales ──
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: tipo_actividad; Type: TABLE DATA; Schema: auditoria; Owner: -
--

INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('registro_usuario', 'Registro de usuario', 'perfil', 'La persona validó su identidad y creó su usuario en la wallet', 'user-plus');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('actualizacion_perfil', 'Actualización de datos', 'perfil', 'Cambió o confirmó sus datos personales o de contacto', 'user-pen');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('inicio_sesion', 'Inicio de sesión', 'sesion', 'Ingresó a la wallet con PIN o biometría', 'log-in');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('cierre_sesion', 'Cierre de sesión', 'sesion', 'Salió o se desconectó de la wallet', 'log-out');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('creacion_pin', 'Creación de PIN', 'sesion', 'Creó el PIN de acceso de la wallet', 'key');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('solicitud_credencial', 'Solicitud de credencial', 'credencial', 'Solicitó una credencial a un emisor', 'download');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('emision_credencial', 'Recepción de credencial', 'credencial', 'Recibió y guardó una credencial emitida', 'badge-check');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('eliminacion_credencial', 'Eliminación de credencial', 'credencial', 'Borró una credencial de su wallet', 'trash');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('solicitud_presentacion', 'Solicitud de un verificador', 'conexion', 'Un verificador pidió datos a la wallet', 'inbox');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('presentacion_credencial', 'Presentación de credencial', 'conexion', 'Compartió datos de sus credenciales con un verificador', 'send');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('conexion_proximidad', 'Conexión por proximidad', 'conexion', 'Conexión cara a cara (QR/NFC/BLE) con un verificador', 'wifi');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('firma_documento', 'Firma de documento', 'conexion', 'Firmó un documento con su credencial', 'pen-tool');
INSERT INTO auditoria.tipo_actividad (codigo, nombre, categoria, descripcion, icono) VALUES ('error_aplicacion', 'Error de la aplicación', 'sistema', 'Falla no asociada a un trámite concreto', 'alert-triangle');


--
-- PostgreSQL database dump complete
--


