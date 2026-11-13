"""Simulador de un prestador de servicios de firma remota (srv-andes-scd)."""

from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import APIRouter, FastAPI
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

from . import ca, config, csc, db, oauth, paginas, tsa


@asynccontextmanager
async def ciclo(_app: FastAPI):
    db.iniciar()
    db.limpiar_vencidos()
    autoridad = ca.cargar_autoridad()
    oauth.fijar_autoridad(autoridad)
    csc.fijar_autoridad(autoridad)
    tsa.preparar(autoridad)
    yield


app = FastAPI(title="srv-andes-scd", docs_url=None, redoc_url=None, openapi_url=None, lifespan=ciclo)

raiz = APIRouter()


def _estado() -> dict:
    with db.transaccion() as c:
        return {
            "usuarios": c.execute("SELECT COUNT(*) FROM usuarios").fetchone()[0],
            "credenciales": c.execute("SELECT COUNT(*) FROM credenciales").fetchone()[0],
            "firmas": c.execute("SELECT COUNT(*) FROM firmas").fetchone()[0],
        }


@raiz.get("/health")
async def health() -> dict:
    return {"ok": True}


@raiz.get("/estado")
async def estado() -> dict:
    return {"servicio": "srv-andes-scd", "prestador": config.NOMBRE_PRESTADOR, **_estado()}


@raiz.get("/", response_class=HTMLResponse)
async def portada() -> str:
    with db.transaccion() as c:
        filas = c.execute("SELECT fecha, documento, huellas, algoritmo FROM firmas ORDER BY id DESC LIMIT 8").fetchall()
    firmas = [
        {"fecha": f["fecha"], "documento": f["documento"], "algoritmo": f["algoritmo"],
         "huella": (db.loads(f["huellas"]) or [""])[0]}
        for f in filas
    ]
    return paginas.portada(_estado(), firmas)


raiz.include_router(oauth.router)
raiz.include_router(csc.router)

# El nginx central reenvia la ruta COMPLETA (como en las demas entidades): todo cuelga de /<prefijo>.
app.include_router(raiz, prefix=f"/{config.PREFIJO}")
app.mount(f"/{config.PREFIJO}/static", StaticFiles(directory=Path(__file__).parent / "static"), name="static")

# Algunos clientes piden el metadata por especificacion (RFC 8414): /.well-known/<doc>/<issuer-path>.
app.add_api_route(
    f"/.well-known/oauth-authorization-server/{config.PREFIJO}",
    oauth.well_known, methods=["GET"],
)
# Y la salud, tambien en la raiz, para el healthcheck del contenedor.
app.add_api_route("/health", health, methods=["GET"])
