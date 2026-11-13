\restrict dfpCgCkQWw0rhVuA0difuJqCUBmXhEzIuSDtroSFngGiqd4FIyr6BgCD45wwuAy
CREATE TABLE public.issued_key_status (
    id integer NOT NULL,
    ka_status_id integer,
    session_id uuid,
    device_key text,
    identifier_list jsonb,
    status_list jsonb,
    created_at timestamp with time zone DEFAULT now()
);
CREATE SEQUENCE public.issued_key_status_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.issued_key_status_id_seq OWNED BY public.issued_key_status.id;
CREATE TABLE public.ka_key_storage_status (
    id integer NOT NULL,
    session_id uuid,
    ka_index integer,
    status jsonb,
    created_at timestamp with time zone DEFAULT now()
);
CREATE SEQUENCE public.ka_key_storage_status_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ka_key_storage_status_id_seq OWNED BY public.ka_key_storage_status.id;
CREATE TABLE public.notaria_orden_emision (
    id uuid NOT NULL,
    cliente text NOT NULL,
    referencia text NOT NULL,
    documento text NOT NULL,
    datos jsonb NOT NULL,
    estado text DEFAULT 'CREADA'::text NOT NULL,
    session_id text,
    tx_code text,
    oferta_uri text,
    oferta_generada_en timestamp with time zone,
    oferta_expira_en timestamp with time zone,
    emitida_en timestamp with time zone,
    creada_en timestamp with time zone DEFAULT now() NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    actualizada_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notaria_orden_emision_estado_check CHECK ((estado = ANY (ARRAY['CREADA'::text, 'OFERTA_GENERADA'::text, 'EMITIDA'::text, 'CANCELADA'::text])))
);
CREATE TABLE public.notaria_orden_evento (
    id bigint NOT NULL,
    orden_id uuid NOT NULL,
    evento text NOT NULL,
    detalle jsonb,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);
CREATE SEQUENCE public.notaria_orden_evento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.notaria_orden_evento_id_seq OWNED BY public.notaria_orden_evento.id;
CREATE TABLE public.notary_protocol_sequence (
    year integer NOT NULL,
    last_number integer NOT NULL
);
CREATE TABLE public.wia_client_status (
    session_id uuid NOT NULL,
    status jsonb,
    exp bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);
ALTER TABLE ONLY public.issued_key_status ALTER COLUMN id SET DEFAULT nextval('public.issued_key_status_id_seq'::regclass);
ALTER TABLE ONLY public.ka_key_storage_status ALTER COLUMN id SET DEFAULT nextval('public.ka_key_storage_status_id_seq'::regclass);
ALTER TABLE ONLY public.notaria_orden_evento ALTER COLUMN id SET DEFAULT nextval('public.notaria_orden_evento_id_seq'::regclass);
ALTER TABLE ONLY public.issued_key_status
    ADD CONSTRAINT issued_key_status_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.ka_key_storage_status
    ADD CONSTRAINT ka_key_storage_status_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notaria_orden_emision
    ADD CONSTRAINT notaria_orden_emision_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notaria_orden_evento
    ADD CONSTRAINT notaria_orden_evento_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notary_protocol_sequence
    ADD CONSTRAINT notary_protocol_sequence_pkey PRIMARY KEY (year);
ALTER TABLE ONLY public.issued_key_status
    ADD CONSTRAINT uq_ka_device_key UNIQUE (ka_status_id, device_key);
ALTER TABLE ONLY public.notaria_orden_emision
    ADD CONSTRAINT uq_orden_cliente_referencia UNIQUE (cliente, referencia);
ALTER TABLE ONLY public.ka_key_storage_status
    ADD CONSTRAINT uq_session_ka_index UNIQUE (session_id, ka_index);
ALTER TABLE ONLY public.wia_client_status
    ADD CONSTRAINT wia_client_status_pkey PRIMARY KEY (session_id);
CREATE INDEX idx_issued_key_session ON public.issued_key_status USING btree (session_id);
CREATE INDEX idx_ka_session ON public.ka_key_storage_status USING btree (session_id);
CREATE INDEX idx_orden_evento_orden ON public.notaria_orden_evento USING btree (orden_id);
CREATE INDEX idx_orden_session ON public.notaria_orden_emision USING btree (session_id);
ALTER TABLE ONLY public.issued_key_status
    ADD CONSTRAINT issued_key_status_ka_status_id_fkey FOREIGN KEY (ka_status_id) REFERENCES public.ka_key_storage_status(id);
ALTER TABLE ONLY public.issued_key_status
    ADD CONSTRAINT issued_key_status_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.wia_client_status(session_id);
ALTER TABLE ONLY public.ka_key_storage_status
    ADD CONSTRAINT ka_key_storage_status_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.wia_client_status(session_id);
ALTER TABLE ONLY public.notaria_orden_evento
    ADD CONSTRAINT notaria_orden_evento_orden_id_fkey FOREIGN KEY (orden_id) REFERENCES public.notaria_orden_emision(id) ON DELETE CASCADE;
\unrestrict dfpCgCkQWw0rhVuA0difuJqCUBmXhEzIuSDtroSFngGiqd4FIyr6BgCD45wwuAy
