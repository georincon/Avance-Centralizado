# coding: latin-1
###############################################################################
# Copyright (c) 2026 European Commission
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
###############################################################################
import logging

import psycopg
from psycopg_pool import ConnectionPool

logger = logging.getLogger(__name__)

_pool: ConnectionPool | None = None


def init_db_status(postgres_config: dict):
    """
    Initializes the connection pool and ensures the required tables exist.
    Call this once at application startup, passing CONFIGURATION["postgres"].
    """
    global _pool

    conninfo = (
        f"host={postgres_config['host']} "
        f"port={postgres_config.get('port', 5432)} "
        f"dbname={postgres_config['dbname']} "
        f"user={postgres_config['user']} "
        f"password={postgres_config['password']}"
    )

    _pool = ConnectionPool(
        conninfo=conninfo,
        min_size=1,
        max_size=10,
        open=True,  # opens and primes the pool immediately, raising here if DB is unreachable
    )
    _create_tables()
    logger.info("Status persistence DB pool initialized and tables ensured.")


def _create_tables():
    with _pool.connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS wia_client_status (
                    session_id UUID PRIMARY KEY,
                    status JSONB,
                    exp BIGINT,
                    created_at TIMESTAMPTZ DEFAULT now(),
                    updated_at TIMESTAMPTZ DEFAULT now()
                );
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS ka_key_storage_status (
                    id SERIAL PRIMARY KEY,
                    session_id UUID REFERENCES wia_client_status(session_id),
                    ka_index INT,
                    status JSONB,
                    created_at TIMESTAMPTZ DEFAULT now(),
                    CONSTRAINT uq_session_ka_index UNIQUE (session_id, ka_index)
                );
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS issued_key_status (
                    id SERIAL PRIMARY KEY,
                    ka_status_id INT REFERENCES ka_key_storage_status(id),
                    session_id UUID REFERENCES wia_client_status(session_id),
                    device_key TEXT,
                    identifier_list JSONB,
                    status_list JSONB,
                    created_at TIMESTAMPTZ DEFAULT now(),
                    CONSTRAINT uq_ka_device_key UNIQUE (ka_status_id, device_key)
                );
                """
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_ka_session ON ka_key_storage_status(session_id);"
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_issued_key_session ON issued_key_status(session_id);"
            )
            # Auditoría local de emisiones (todas las vías: sitio web y API de órdenes): una fila por hecho. Solo se agrega.
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS notaria_auditoria_evento (
                    id BIGSERIAL PRIMARY KEY,
                    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                    tipo TEXT NOT NULL,
                    resultado TEXT NOT NULL,
                    referencia TEXT,
                    credencial_tipo TEXT,
                    sujeto_documento TEXT,
                    sujeto_nombre TEXT,
                    duracion_ms INT,
                    tipo_error TEXT,
                    codigo_error TEXT,
                    mensaje_error TEXT,
                    detalle JSONB
                );
                """
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_notaria_aud_ref ON notaria_auditoria_evento(referencia);"
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_notaria_aud_fecha ON notaria_auditoria_evento(ocurrido_en DESC);"
            )
        conn.commit()


def guardar_evento_auditoria(ev: dict):
    """Guarda un hecho de emisión (oferta, emisión…) en la auditoría local. Quien lo llama ignora cualquier error."""
    sujeto = ev.get("sujeto") or {}
    with _pool.connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO notaria_auditoria_evento
                    (ocurrido_en, tipo, resultado, referencia, credencial_tipo, sujeto_documento, sujeto_nombre,
                     duracion_ms, tipo_error, codigo_error, mensaje_error, detalle)
                VALUES (COALESCE(%s::timestamptz, now()), %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (ev.get("ocurrido_en"), ev.get("tipo"), ev.get("resultado"), ev.get("referencia"), ev.get("credencial_tipo"),
                 sujeto.get("numero_documento"), sujeto.get("nombre"), ev.get("duracion_ms"), ev.get("tipo_error"),
                 ev.get("codigo_error"), (ev.get("mensaje_error") or None) and str(ev.get("mensaje_error"))[:500],
                 to_jsonb(ev.get("detalle"))),
            )
        conn.commit()


def next_protocol_number(year: int) -> str:
    """Consecutivo del protocolo notarial por año ("2026-00001"), atómico en Postgres."""
    with _pool.connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "CREATE TABLE IF NOT EXISTS notary_protocol_sequence "
                "(year INT PRIMARY KEY, last_number INT NOT NULL)"
            )
            cur.execute(
                "INSERT INTO notary_protocol_sequence (year, last_number) VALUES (%s, 1) "
                "ON CONFLICT (year) DO UPDATE SET last_number = notary_protocol_sequence.last_number + 1 "
                "RETURNING last_number",
                (year,),
            )
            number = cur.fetchone()[0]
    return f"{year}-{number:05d}"


def to_jsonb(value):
    """
    Wraps dicts for JSONB columns using psycopg3's Json adapter, so psycopg
    serializes them correctly at bind time. Passes None through untouched so
    psycopg writes a real SQL NULL instead of a JSON 'null' literal.
    """
    return psycopg.types.json.Json(value) if value is not None else None


def persist_client_status(session_id: str, client_status: dict):
    if not client_status:
        return

    try:
        with _pool.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO wia_client_status (session_id, status, exp, updated_at)
                    VALUES (%s, %s, %s, now())
                    ON CONFLICT (session_id) DO UPDATE
                        SET status = EXCLUDED.status,
                            exp = EXCLUDED.exp,
                            updated_at = now()
                    """,
                    (
                        session_id,
                        to_jsonb(client_status.get("status")),
                        client_status.get("exp"),
                    ),
                )

                for ka_index, ka_entry in enumerate(
                    client_status.get("key_storage_statuses", [])
                ):
                    cur.execute(
                        """
                        INSERT INTO ka_key_storage_status (session_id, ka_index, status)
                        VALUES (%s, %s, %s)
                        ON CONFLICT (session_id, ka_index) DO UPDATE
                            SET status = EXCLUDED.status
                        RETURNING id
                        """,
                        (session_id, ka_index, to_jsonb(ka_entry.get("status"))),
                    )
                    ka_status_id = cur.fetchone()[0]

                    for key_entry in ka_entry.get("keys", []):
                        key_status = key_entry.get("key_status") or {}
                        cur.execute(
                            """
                            INSERT INTO issued_key_status
                                (ka_status_id, session_id, device_key, identifier_list, status_list)
                            VALUES (%s, %s, %s, %s, %s)
                            ON CONFLICT (ka_status_id, device_key) DO UPDATE
                                SET identifier_list = EXCLUDED.identifier_list,
                                    status_list = EXCLUDED.status_list
                            """,
                            (
                                ka_status_id,
                                session_id,
                                key_entry.get("key"),
                                to_jsonb(key_status.get("identifier_list")),
                                to_jsonb(key_status.get("status_list")),
                            ),
                        )
            conn.commit()
        logger.info(f"Persisted client_status for session_id {session_id} to Postgres.")
    except Exception:
        logger.exception(f"Failed to persist client_status for session_id {session_id}")
        raise
