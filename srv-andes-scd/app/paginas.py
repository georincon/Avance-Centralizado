"""Paginas HTML del simulador. Estilo tomado de la identidad visual de la web de Andes SCD
(azules, acento lima, Montserrat/Poppins). No se usan su logo ni sus marcas: es un simulador,
y asi lo dice cada pagina."""

from __future__ import annotations

import html
import json
import time

from . import config

CSS = """
:root{--azul:#336198;--azul2:#3f79ac;--celeste:#93cde9;--lima:#afd81a;--texto:#313131;--suave:#5c5f63;--fondo:#fbfbfb;--borde:#dde6ee}
*{box-sizing:border-box}
body{margin:0;font-family:'Montserrat','Poppins',-apple-system,Segoe UI,Roboto,sans-serif;color:var(--texto);background:var(--fondo)}
.aviso{background:#111;color:#fff;font-size:12px;text-align:center;padding:6px 12px;letter-spacing:.2px}
.aviso b{color:var(--lima)}
header{background:#fff;border-bottom:1px solid var(--borde)}
.cabecera{max-width:960px;margin:0 auto;padding:14px 20px;display:flex;align-items:center;gap:12px}
.marca{font-weight:700;font-size:20px;color:var(--azul);letter-spacing:.3px}
.marca span{color:var(--lima);background:var(--azul);padding:1px 8px;border-radius:6px;margin-left:8px;font-size:12px;vertical-align:middle}
main{max-width:960px;margin:0 auto;padding:28px 20px 48px}
.heroe{background:linear-gradient(135deg,var(--azul),var(--azul2));color:#fff;border-radius:18px;padding:32px;margin-bottom:24px}
.heroe h1{margin:0 0 8px;font-size:26px}
.heroe p{margin:0;opacity:.92;line-height:1.5}
.rejilla{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;margin:18px 0}
.tarjeta{background:#fff;border:1px solid var(--borde);border-radius:14px;padding:18px}
.tarjeta h3{margin:0 0 6px;font-size:15px;color:var(--azul)}
.tarjeta .n{font-size:30px;font-weight:700;color:var(--azul2)}
.tarjeta p{margin:6px 0 0;color:var(--suave);font-size:13px;line-height:1.45}
code{background:#eef3f8;padding:1px 6px;border-radius:5px;font-size:12.5px}
table{width:100%;border-collapse:collapse;font-size:13px}
td,th{padding:8px 6px;border-bottom:1px solid var(--borde);text-align:left}
th{color:var(--suave);font-weight:600}
.caja{max-width:440px;margin:36px auto;background:#fff;border:1px solid var(--borde);border-radius:18px;padding:28px}
.caja h1{font-size:20px;margin:0 0 6px;color:var(--azul)}
.caja .sub{color:var(--suave);font-size:13px;line-height:1.5;margin:0 0 18px}
label{display:block;font-size:12px;font-weight:600;color:var(--suave);margin:14px 0 4px}
input{width:100%;padding:11px 12px;border:1px solid var(--borde);border-radius:10px;font:inherit;font-size:15px}
input:focus{outline:2px solid var(--celeste);border-color:var(--azul2)}
button{width:100%;margin-top:20px;padding:13px;border:0;border-radius:999px;background:var(--azul);color:#fff;font:inherit;font-weight:700;font-size:15px;cursor:pointer}
button:hover{background:var(--azul2)}
.error{background:#fdecea;color:#8a1c12;border-radius:10px;padding:10px 12px;font-size:13px;margin:0 0 14px}
.resumen{background:#eef6fb;border-left:4px solid var(--lima);border-radius:8px;padding:12px 14px;font-size:13px;margin:0 0 6px;line-height:1.5}
.abrir{display:block;text-align:center;text-decoration:none;margin-top:18px;padding:13px;border-radius:999px;background:var(--azul);color:#fff;font-weight:700;font-size:15px}
.abrir:hover{background:var(--azul2)}
.qr{display:block;margin:6px auto;width:220px;max-width:70%;border-radius:12px;border:1px solid var(--borde);padding:6px;background:#fff}
.estado{margin:14px 0 0;padding:10px 12px;border-radius:10px;background:#eef3f8;color:var(--suave);font-size:13px;text-align:center}
.estado.ok{background:#e4f6ec;color:#0f7b4b;font-weight:700}.estado.mal{background:#fdecea;color:#8a1c12}
.pasos{margin:14px 0 0;padding-left:20px;font-size:13px;color:var(--suave);line-height:1.5}
.pie{color:var(--suave);font-size:12px;text-align:center;margin-top:22px;line-height:1.5}
"""


def _e(valor) -> str:
    return html.escape(str(valor), quote=True)


def _marco(titulo: str, cuerpo: str) -> str:
    return f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{_e(titulo)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;700&display=swap" rel="stylesheet">
<style>{CSS}</style></head><body>
<div class="aviso"><b>ENTORNO DE PRUEBAS</b> · simulador de un prestador de servicios de certificación · sin validez legal</div>
<header><div class="cabecera"><div class="marca">Andes SCD<span>SIMULADOR</span></div></div></header>
{cuerpo}
</body></html>"""


def _fecha(t: float) -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(t))


def portada(estado: dict, firmas: list[dict]) -> str:
    filas = "".join(
        f"<tr><td>{_fecha(f['fecha'])}</td><td>CC ···{_e(str(f['documento'])[-3:])}</td>"
        f"<td><code>{_e(f['huella'][:16])}…</code></td><td>{_e(f['algoritmo'])}</td></tr>"
        for f in firmas
    ) or '<tr><td colspan="4">Todavía no hay firmas.</td></tr>'
    cuerpo = f"""<main>
<div class="heroe"><h1>Servicio de firma remota</h1>
<p>Simulador del prestador de servicios de certificación del ecosistema Avance. Emite certificados de
firma con la sub-CA QTSP, firma las huellas que la wallet le envía (API CSC v2) y sella el tiempo
(RFC 3161). La clave de cada firmante vive aquí, nunca en el teléfono.</p></div>
<div class="rejilla">
<div class="tarjeta"><h3>Firmantes</h3><div class="n">{estado['usuarios']}</div><p>Personas registradas en el simulador.</p></div>
<div class="tarjeta"><h3>Certificados</h3><div class="n">{estado['credenciales']}</div><p>Certificados de firma emitidos por la sub-CA QTSP.</p></div>
<div class="tarjeta"><h3>Firmas</h3><div class="n">{estado['firmas']}</div><p>Huellas firmadas en total.</p></div>
</div>
<div class="tarjeta"><h3>Direcciones del servicio</h3>
<p>API CSC v2: <code>{_e(config.URL_PUBLICA)}/csc/v2</code><br>
Servidor OAuth2: <code>{_e(config.URL_PUBLICA)}/.well-known/oauth-authorization-server</code><br>
Sellado de tiempo (RFC 3161): <code>{_e(config.URL_PUBLICA)}/tsa</code><br>
Estado: <code>{_e(config.URL_PUBLICA)}/estado</code></p></div>
<br><div class="tarjeta"><h3>Últimas firmas</h3>
<table><tr><th>Fecha</th><th>Firmante</th><th>Huella</th><th>Algoritmo</th></tr>{filas}</table></div>
<p class="pie">Solo para pruebas del ecosistema EUDI de Avance. No es Andes SCD ni sus certificados
tienen respaldo ONAC. Etiquetado de firma: <b>firma electrónica de prueba</b>.</p>
</main>"""
    return _marco("Andes SCD · Simulador de firma remota", cuerpo)


def autorizacion(rid: str, detalles: list[dict] | None, scope: str, error: str | None = None,
                 documento: str = "", nombre: str = "") -> str:
    if detalles:
        d = detalles[0]
        huellas = d.get("documentDigests") or []
        etiquetas = ", ".join(_e(h.get("label") or "documento") for h in huellas) or "documento"
        titulo = "Autorizar firma"
        resumen = (f"<div class='resumen'>Vas a firmar <b>{len(huellas) or d.get('numSignatures', 1)}</b> "
                   f"documento(s): {etiquetas}.<br>La firma usará tu certificado de firma electrónica de prueba.</div>")
    else:
        titulo = "Iniciar sesión"
        resumen = ("<div class='resumen'>Para ver tu certificado y firmar documentos, identifícate. "
                   "En este prototipo la identidad <b>no se valida</b>; en la siguiente etapa se hará con tu PID.</div>")
    aviso = f"<div class='error'>{_e(error)}</div>" if error else ""
    cuerpo = f"""<div class="caja"><h1>{titulo}</h1>
<p class="sub">Servicio de firma remota (simulador)</p>{aviso}{resumen}
<form method="post">
<input type="hidden" name="rid" value="{_e(rid)}">
<label for="documento">Número de documento</label>
<input id="documento" name="documento" inputmode="numeric" autocomplete="off" value="{_e(documento)}" required>
<label for="pin">PIN de firma (4 a 8 dígitos)</label>
<input id="pin" name="pin" type="password" inputmode="numeric" autocomplete="off" required>
<label for="nombre">Nombre completo <span style="font-weight:400">(solo la primera vez)</span></label>
<input id="nombre" name="nombre" autocomplete="off" value="{_e(nombre)}">
<button type="submit">{'Firmar' if detalles else 'Continuar'}</button>
</form>
<p class="pie">Si es tu primera vez, el PIN que escribas quedará como tu PIN de firma.</p></div>"""
    return _marco(titulo, cuerpo)


def identificacion_pid(rid: str, detalles: list[dict] | None, qr: str, enlace: str, error: str | None = None,
                       mismo_dispositivo: bool = False) -> str:
    """Pantalla de identificacion con el PID: QR para otra pantalla y boton para abrir la wallet en este telefono."""
    if detalles:
        d = detalles[0]
        huellas = d.get("documentDigests") or []
        etiquetas = ", ".join(_e(h.get("label") or "documento") for h in huellas) or "documento"
        titulo = "Autoriza la firma con tu PID"
        resumen = (f"<div class='resumen'>Vas a firmar <b>{len(huellas) or d.get('numSignatures', 1)}</b> "
                   f"documento(s): {etiquetas}.<br>Para autorizarlo, presenta tu PID desde la wallet: así solo tú puedes "
                   "firmar con tu certificado.</div>")
    else:
        titulo = "Identifícate con tu PID"
        resumen = ("<div class='resumen'>Para ver tu certificado de firma y firmar documentos, presenta tu PID (cédula digital) "
                   "desde la wallet. Solo se piden tu <b>documento y tu nombre</b>.</div>")
    aviso = f"<div class='error'>{_e(error)}</div>" if error else ""
    if mismo_dispositivo:
        # En el telefono un QR en su propia pantalla no se puede escanear: se abre la wallet con el enlace, y al terminar
        # la wallet abre la pagina de retorno (/oauth2/retorno), que continua sola.
        medio = f"""<a class="abrir" href="{_e(enlace)}">Abrir mi wallet</a>
<ol class="pasos"><li>Toca <b>Abrir mi wallet</b> (si Android pregunta con qué app, elige tu wallet).</li>
<li>Desbloquéala, revisa lo que se pide y acepta.</li>
<li>Al terminar, toca el botón de la pantalla de éxito: volverás aquí y la firma continúa sola.</li></ol>"""
    else:
        medio = f"""<p class="sub" style="text-align:center;margin:14px 0 6px">Escanea este código con la wallet de tu teléfono</p>
<img class="qr" src="{_e(qr)}" alt="Código QR para presentar el PID">"""
    cuerpo = f"""<div class="caja"><h1>{titulo}</h1>
<p class="sub">Servicio de firma remota (simulador)</p>{aviso}{resumen}
{medio}
<div id="estado" class="estado" role="status" aria-live="polite">Esperando tu PID…</div>
<form id="f" method="post"><input type="hidden" name="rid" value="{_e(rid)}"></form>
<p class="pie">La wallet te mostrará qué se pide y para qué antes de que aceptes.</p></div>
<script>
(() => {{
  const rid = {json.dumps(rid)};
  const SOLO_AVISAR = {json.dumps(mismo_dispositivo)};
  const estado = document.getElementById('estado');
  let parar = false;
  async function sondear() {{
    if (parar) return;
    try {{
      const r = await fetch('identidad?rid=' + encodeURIComponent(rid), {{ cache: 'no-store' }});
      const j = await r.json();
      if (j.estado === 'listo') {{
        parar = true; estado.className = 'estado ok';
        estado.textContent = 'Identidad verificada: ' + j.nombre + '. Continuando…';
        // En el mismo dispositivo continua la pagina de retorno (la que abre la wallet); aqui solo se avisa.
        if (SOLO_AVISAR) {{ estado.textContent = 'Identidad verificada: ' + j.nombre + '. Puedes continuar en la pestaña abierta por la wallet.'; return; }}
        setTimeout(() => document.getElementById('f').submit(), 600);
        return;
      }}
      if (j.estado === 'error') {{
        parar = true; estado.className = 'estado mal';
        estado.innerHTML = ''; estado.append(j.motivo + ' ');
        const a = document.createElement('a'); a.href = location.href; a.textContent = 'Intentar de nuevo'; estado.append(a);
        return;
      }}
    }} catch (e) {{ /* sin red un momento: se vuelve a intentar */ }}
    setTimeout(sondear, 2000);
  }}
  sondear();
}})();
</script>"""
    return _marco(titulo, cuerpo)


def confirmar_sesion(rid: str, nombre: str, detalles: list[dict] | None, volver: str) -> str:
    """Ya se identifico con su PID hace pocos minutos: solo confirma. Sin datos que escribir."""
    if detalles:
        d = detalles[0]
        huellas = d.get("documentDigests") or []
        etiquetas = ", ".join(_e(h.get("label") or "documento") for h in huellas) or "documento"
        titulo = "Confirma la firma"
        resumen = (f"<div class='resumen'>Vas a firmar <b>{len(huellas) or d.get('numSignatures', 1)}</b> documento(s): "
                   f"{etiquetas}.<br>Firmarás como <b>{_e(nombre)}</b>, con tu certificado de firma electrónica de prueba.</div>")
        boton = "Firmar"
        auto = ""
    else:
        titulo = "Continuar"
        resumen = f"<div class='resumen'>Continuarás como <b>{_e(nombre)}</b>, identificado con tu PID hace unos minutos.</div>"
        boton = "Continuar"
        auto = "<script>setTimeout(() => document.getElementById('f').submit(), 500);</script>"
    cuerpo = f"""<div class="caja"><h1>{titulo}</h1>
<p class="sub">Servicio de firma remota (simulador)</p>{resumen}
<form id="f" method="post"><input type="hidden" name="rid" value="{_e(rid)}"><button type="submit">{boton}</button></form>
<p class="pie"><a href="{_e(volver)}">No soy yo: identificarme con otro PID</a></p></div>{auto}"""
    return _marco(titulo, cuerpo)


def retorno_continuar(rid: str, nombre: str) -> str:
    """Pagina a la que la wallet devuelve al navegador: confirma la identidad y envia sola el formulario."""
    cuerpo = f"""<div class="caja"><h1>Identidad verificada</h1>
<p class="sub">Servicio de firma remota (simulador)</p>
<div class="estado ok" role="status">{_e(nombre)}. Continuando con la firma…</div>
<form id="f" method="post" action="authorize"><input type="hidden" name="rid" value="{_e(rid)}">
<noscript><button type="submit">Continuar</button></noscript></form>
<script>setTimeout(() => document.getElementById('f').submit(), 400);</script></div>"""
    return _marco("Identidad verificada", cuerpo)


def error_simple(titulo: str, mensaje: str) -> str:
    return _marco(titulo, f"<div class='caja'><h1>{_e(titulo)}</h1><p class='sub'>{_e(mensaje)}</p></div>")
