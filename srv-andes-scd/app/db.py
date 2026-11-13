"""Almacen SQLite del simulador: usuarios, credenciales de firma, flujo OAuth2 y registro de firmas."""

import json
import sqlite3
import threading
import time
from contextlib import contextmanager

from . import config

_candado = threading.RLock()
_conexion: sqlite3.Connection | None = None

ESQUEMA = """
CREATE TABLE IF NOT EXISTS usuarios (
    documento   TEXT PRIMARY KEY,
    nombre      TEXT NOT NULL,
    pin_sal     BLOB NOT NULL,
    pin_hash    BLOB NOT NULL,
    intentos    INTEGER NOT NULL DEFAULT 0,
    creado      REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS credenciales (
    id          TEXT PRIMARY KEY,
    documento   TEXT NOT NULL REFERENCES usuarios(documento),
    serie       TEXT NOT NULL,
    cert_pem    BLOB NOT NULL,
    clave_cifrada BLOB NOT NULL,
    estado      TEXT NOT NULL DEFAULT 'valid',
    creado      REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS solicitudes (
    id          TEXT PRIMARY KEY,
    parametros  TEXT NOT NULL,
    expira      REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS codigos (
    codigo      TEXT PRIMARY KEY,
    documento   TEXT NOT NULL,
    parametros  TEXT NOT NULL,
    expira      REAL NOT NULL,
    usado       INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS tokens (
    token       TEXT PRIMARY KEY,
    tipo        TEXT NOT NULL,
    documento   TEXT,
    scope       TEXT,
    detalles    TEXT,
    expira      REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS firmas (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha       REAL NOT NULL,
    documento   TEXT NOT NULL,
    credencial  TEXT NOT NULL,
    huellas     TEXT NOT NULL,
    algoritmo   TEXT NOT NULL
);
"""


def iniciar() -> None:
    global _conexion
    config.DIR_DATOS.mkdir(parents=True, exist_ok=True)
    _conexion = sqlite3.connect(config.DIR_DATOS / "andes-scd.db", check_same_thread=False)
    _conexion.row_factory = sqlite3.Row
    with _candado:
        _conexion.executescript(ESQUEMA)
        _conexion.commit()


@contextmanager
def transaccion():
    """Una operacion a la vez: el simulador es de pruebas, no de carga."""
    assert _conexion is not None, "db.iniciar() no se ha llamado"
    with _candado:
        try:
            yield _conexion
            _conexion.commit()
        except Exception:
            _conexion.rollback()
            raise


def ahora() -> float:
    return time.time()


def dumps(valor) -> str:
    return json.dumps(valor, separators=(",", ":"), ensure_ascii=False)


def loads(texto: str | None):
    return json.loads(texto) if texto else None


def limpiar_vencidos() -> None:
    t = ahora()
    with transaccion() as c:
        c.execute("DELETE FROM solicitudes WHERE expira < ?", (t,))
        c.execute("DELETE FROM codigos WHERE expira < ?", (t,))
        c.execute("DELETE FROM tokens WHERE expira < ?", (t,))
