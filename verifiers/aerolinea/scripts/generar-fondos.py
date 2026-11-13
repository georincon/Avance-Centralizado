"""Genera el fondo de tarjeta (1000×600) del tiquete y el logo de la aerolínea.

    python3 scripts/generar-fondos.py <carpeta>   (requiere Chrome y ImageMagick `convert`)

Luego se copian a src/publico/fondos/ y src/publico/logo.png.

La wallet pone encima un velo oscuro arriba y abajo, el logo y el nombre arriba a la izquierda y el estado abajo a la
derecha: los motivos van a la derecha y al centro, sutiles, y la esquina superior izquierda queda limpia.
"""
import shutil
import subprocess
import sys
from pathlib import Path

SALIDA = Path(sys.argv[1])
SALIDA.mkdir(parents=True, exist_ok=True)
W, H = 1000, 600
CLARO = "#FDE9E4"

TIQUETE = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5a0508"/><stop offset=".55" stop-color="#b30912"/><stop offset="1" stop-color="#7d060c"/></linearGradient>
<radialGradient id="luz" cx=".78" cy=".4" r=".55"><stop offset="0" stop-color="#fff" stop-opacity=".18"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#g)"/><rect width="{W}" height="{H}" fill="url(#luz)"/>
<g fill="none" stroke="{CLARO}" stroke-opacity=".16" stroke-width="1.4">{"".join(f'<path d="M{x-200},{560-i*34} Q{x+250},{300-i*34} {x+700},{560-i*34}"/>' for i, x in enumerate([260]*9))}</g>
<g fill="none" stroke="{CLARO}" stroke-linecap="round" stroke-linejoin="round" opacity=".6">
  <path d="M560 400 C680 300 820 270 930 160" stroke-width="3" stroke-dasharray="3 12"/>
  <circle cx="560" cy="400" r="10" stroke-width="3"/><circle cx="930" cy="160" r="10" stroke-width="3"/>
  <path d="M640 330 L760 244 L736 336 L706 308 L684 330 L690 290z" fill="{CLARO}" fill-opacity=".25" stroke-width="3.5"/>
</g>
<g fill="{CLARO}" fill-opacity=".5"><circle cx="880" cy="450" r="3"/><circle cx="760" cy="110" r="2.5"/><circle cx="990" cy="300" r="2.5"/></g>
<text x="-20" y="588" font-family="DejaVu Sans, sans-serif" font-size="11" letter-spacing="1.5" fill="{CLARO}" fill-opacity=".16">{("PÁRAMO AIR · TIQUETE ELECTRÓNICO · " * 8)}</text>
<rect x="14" y="14" width="{W-28}" height="{H-28}" rx="26" fill="none" stroke="{CLARO}" stroke-opacity=".24" stroke-width="2"/>
</svg>'''

LOGO = '''<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
<rect width="256" height="256" rx="52" fill="#dc0b17"/>
<path d="M44 150 214 62 168 196 128 148 84 176 92 134z" fill="#fff"/><path d="M92 134 214 62 128 148z" fill="#f3c9cc"/>
<path d="M44 214h110" stroke="#fde9e4" stroke-width="10" stroke-linecap="round"/></svg>'''


def render(nombre, svg, ancho, alto, salida_png=False):
    ruta = SALIDA / f"{nombre}.svg"
    ruta.write_text(svg)
    chrome = shutil.which("google-chrome") or shutil.which("chromium")
    png = SALIDA / f"{nombre}.png"
    args = [chrome, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars", f"--window-size={ancho},{alto}", f"--screenshot={png}"]
    if salida_png:
        args.insert(-1, "--default-background-color=00000000")
    subprocess.run([*args, f"file://{ruta.resolve()}"], check=True, capture_output=True)
    if not salida_png:
        subprocess.run(["convert", str(png), "-quality", "84", "-strip", str(SALIDA / f"{nombre}.jpg")], check=True)
        png.unlink()


render("aerolinea-tiquete", TIQUETE, W, H)
render("logo", LOGO, 256, 256, salida_png=True)
print("listo")
