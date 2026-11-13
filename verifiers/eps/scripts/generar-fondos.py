"""Genera los fondos de tarjeta (1000×600) de los documentos de la notaría y la EPS
(el mismo script está en ../../notaria/scripts y ../../eps/scripts).

    python3 scripts/generar-fondos.py <carpeta>   (requiere ImageMagick con librsvg)

Luego se copian a src/publico/fondos/ los de este servicio.

La wallet pone encima un velo oscuro arriba y abajo, el logo y el nombre arriba
a la izquierda y el estado abajo a la derecha: los motivos van a la derecha y
centro, sutiles, y la esquina superior izquierda queda limpia.
"""
import math
import subprocess
import sys
from pathlib import Path

SALIDA = Path(sys.argv[1])
SALIDA.mkdir(parents=True, exist_ok=True)
W, H = 1000, 600


def guilloche(cx, cy, r, color, opacidad, lobulos=18, capas=7):
    """Rosetón de seguridad (como en billetes y papel notarial)."""
    caminos = []
    for k in range(capas):
        rr = r * (0.55 + 0.07 * k)
        puntos = []
        for i in range(721):
            t = i / 720 * 2 * math.pi
            rad = rr + r * 0.12 * math.sin(lobulos * t + k * 0.6)
            puntos.append(f"{cx + rad * math.cos(t):.1f},{cy + rad * math.sin(t):.1f}")
        caminos.append(f'<polyline points="{" ".join(puntos)}" fill="none" stroke="{color}" stroke-opacity="{opacidad}" stroke-width="1.4"/>')
    return "\n".join(caminos)


def ondas(color, opacidad, y0, amplitud, n=9, paso=14):
    caminos = []
    for k in range(n):
        y = y0 + k * paso
        d = " ".join(f"{'M' if x == 0 else 'L'}{x},{y + amplitud * math.sin(x / 70 + k * .5):.1f}" for x in range(0, W + 10, 10))
        caminos.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{opacidad}" stroke-width="1.2"/>')
    return "\n".join(caminos)


def microtexto(texto, color, opacidad, y):
    linea = (texto + " · ") * 12
    return f'<text x="-20" y="{y}" font-family="DejaVu Sans, sans-serif" font-size="11" letter-spacing="1.5" fill="{color}" fill-opacity="{opacidad}">{linea}</text>'


def base(c1, c2, c3):
    return f'''<defs>
<linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="{c1}"/><stop offset=".55" stop-color="{c2}"/><stop offset="1" stop-color="{c3}"/>
</linearGradient>
<radialGradient id="luz" cx=".78" cy=".42" r=".55">
  <stop offset="0" stop-color="#ffffff" stop-opacity=".16"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/>
</radialGradient>
</defs>
<rect width="{W}" height="{H}" fill="url(#g)"/>
<rect width="{W}" height="{H}" fill="url(#luz)"/>'''


def sello(cx, cy, r, color):
    """Sello notarial: dos aros con dentado y estrella."""
    dientes = " ".join(
        f"{cx + (r + (6 if i % 2 else 0)) * math.cos(i * math.pi / 36):.1f},{cy + (r + (6 if i % 2 else 0)) * math.sin(i * math.pi / 36):.1f}"
        for i in range(72))
    estrella = " ".join(
        f"{cx + (r * .42 if i % 2 == 0 else r * .18) * math.cos(-math.pi / 2 + i * math.pi / 5):.1f},{cy + (r * .42 if i % 2 == 0 else r * .18) * math.sin(-math.pi / 2 + i * math.pi / 5):.1f}"
        for i in range(10))
    return f'''<g opacity=".55">
<polygon points="{dientes}" fill="none" stroke="{color}" stroke-width="2"/>
<circle cx="{cx}" cy="{cy}" r="{r * .82}" fill="none" stroke="{color}" stroke-width="2"/>
<circle cx="{cx}" cy="{cy}" r="{r * .66}" fill="none" stroke="{color}" stroke-width="1" stroke-dasharray="3 4"/>
<polygon points="{estrella}" fill="{color}" fill-opacity=".8"/>
</g>'''


def notaria(nombre, c1, c2, c3, motivo):
    claro = "#F4E7C8"  # oro pálido, el de la notaría
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
{base(c1, c2, c3)}
{guilloche(700, 300, 260, claro, .13)}
{ondas(claro, .07, 470, 9, n=8)}
{microtexto("NOTARÍA PRIMERA DE CÍVICA", claro, .16, 588)}
{motivo}
{sello(880, 470, 70, claro)}
<rect x="14" y="14" width="{W - 28}" height="{H - 28}" rx="26" fill="none" stroke="{claro}" stroke-opacity=".22" stroke-width="2"/>
</svg>'''


DECLARACION = '''<g fill="none" stroke="#F4E7C8" stroke-linecap="round" opacity=".5">
  <path d="M520 220h300M520 262h300M520 304h260M520 346h280" stroke-width="3" stroke-opacity=".6"/>
  <path d="M870 120c-60 40-150 150-190 250l-12 40 30-28c90-70 170-170 190-250z" stroke-width="4"/>
  <path d="M680 370c30-60 80-130 150-210" stroke-width="2"/>
  <path d="M668 410c-20 18-60 20-90 6" stroke-width="3"/>
</g>'''

PODER = '''<g fill="none" stroke="#F4E7C8" stroke-linecap="round" stroke-linejoin="round" opacity=".55">
  <circle cx="620" cy="250" r="62" stroke-width="5"/>
  <circle cx="620" cy="250" r="26" stroke-width="3"/>
  <path d="M682 250h230l-26 26-22-22-22 22-22-22" stroke-width="5"/>
  <path d="M540 400c40-30 70 10 100-10s40-40 70-20 50 30 90 0" stroke-width="3"/>
  <path d="M540 430h300" stroke-width="2" stroke-opacity=".5"/>
</g>'''

RECONOCIMIENTO = '''<g fill="none" stroke="#F4E7C8" stroke-linecap="round" opacity=".55">
  <path d="M560 100h190l60 60v260H560z" stroke-width="4"/>
  <path d="M750 100v60h60" stroke-width="3"/>
  <path d="M595 200h150M595 235h170M595 270h120" stroke-width="3" stroke-opacity=".7"/>
  ''' + "\n  ".join(
    f'<path d="M{660 - k * 9},{372 + k * 2} a{12 + k * 9},{14 + k * 10} 0 0 1 {2 * (12 + k * 9)},0" stroke-width="2.2"/>'
    for k in range(7)) + '''
  <text x="560" y="455" font-family="DejaVu Sans Mono, monospace" font-size="15" fill="#F4E7C8" stroke="none" fill-opacity=".55">sha256 · 3f9a…c27e</text>
</g>'''


def eps(c1, c2, c3, motivo):
    claro = "#E4FFF3"
    cruces = "\n".join(
        f'<path d="M{x - 9},{y}h18M{x},{y - 9}v18" stroke="{claro}" stroke-opacity=".10" stroke-width="3" stroke-linecap="round"/>'
        for x in range(470, 1000, 64) for y in range(40, 600, 64) if (x // 64 + y // 64) % 2 == 0)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
{base(c1, c2, c3)}
{cruces}
{ondas(claro, .08, 480, 12, n=7, paso=12)}
{microtexto("EPS SALUD CÍVICA", claro, .15, 588)}
{motivo}
<rect x="14" y="14" width="{W - 28}" height="{H - 28}" rx="26" fill="none" stroke="{claro}" stroke-opacity=".2" stroke-width="2"/>
</svg>'''


CARNE = '''<g opacity=".5">
  <path d="M770 120h80v90h90v80h-90v90h-80v-90h-90v-80h90z" fill="none" stroke="#E4FFF3" stroke-width="5" stroke-linejoin="round"/>
  <path d="M790 140h40v90h90v40h-90v90h-40v-90h-90v-40h90z" fill="#E4FFF3" fill-opacity=".25"/>
  <circle cx="620" cy="390" r="46" fill="none" stroke="#E4FFF3" stroke-width="4"/>
  <path d="M552 470c10-40 40-58 68-58s58 18 68 58" fill="none" stroke="#E4FFF3" stroke-width="4" stroke-linecap="round"/>
</g>'''

ECG_D = "M420 320 L560 320 L585 320 L600 290 L615 320 L640 320 L655 200 L675 420 L695 320 L730 320 L750 290 L775 320 L860 320 L880 300 L900 320 L1000 320"
HISTORIA = f'''<g fill="none" stroke-linecap="round" stroke-linejoin="round">
  <path d="{ECG_D}" stroke="#FFE4EC" stroke-opacity=".18" stroke-width="16"/>
  <path d="{ECG_D}" stroke="#FFE4EC" stroke-opacity=".75" stroke-width="5"/>
  <path d="M760 110c0-26 34-40 52-14 18-26 52-12 52 14 0 34-52 62-52 62s-52-28-52-62z" stroke="#FFE4EC" stroke-opacity=".55" stroke-width="4"/>
  <path d="M600 140v60a40 40 0 0 0 80 0v-60M640 240v40a50 50 0 0 0 100 0v-20" stroke="#FFE4EC" stroke-opacity=".45" stroke-width="4"/>
  <circle cx="740" cy="252" r="12" stroke="#FFE4EC" stroke-opacity=".45" stroke-width="4"/>
</g>'''

FONDOS = {
    "notaria-declaracion": notaria("declaracion", "#2E1247", "#5B2A86", "#3A1A5C", DECLARACION),
    "notaria-poder": notaria("poder", "#08333C", "#0E5E6F", "#0A4250", PODER),
    "notaria-reconocimiento": notaria("reconocimiento", "#4A2804", "#8A4B08", "#5E3305", RECONOCIMIENTO),
    "eps-carne": eps("#063D2C", "#0B6E4F", "#085540", CARNE),
    "eps-historia": eps("#45101F", "#7A1F3D", "#5A1630", HISTORIA).replace('stroke="#E4FFF3"', 'stroke="#FFE4EC"').replace('fill="#E4FFF3"', 'fill="#FFE4EC"'),
}

for nombre, svg in FONDOS.items():
    ruta_svg = SALIDA / f"{nombre}.svg"
    ruta_svg.write_text(svg)
    subprocess.run(["magick", "-density", "96", str(ruta_svg), "-quality", "84", "-strip", str(SALIDA / f"{nombre}.jpg")], check=True)
    print(nombre, (SALIDA / f"{nombre}.jpg").stat().st_size // 1024, "KB")
