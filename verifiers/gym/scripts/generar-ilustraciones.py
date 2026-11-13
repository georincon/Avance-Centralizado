"""Ilustraciones propias del gimnasio (SVG planos): collage del portal, teléfono, y los insumos del logo y del fondo de la
tarjeta de socio (que se pasan a PNG/JPEG con Chrome: ver README). Uso: python3 scripts/generar-ilustraciones.py"""
import os
from pathlib import Path
PIEL = ['#F1C9A5', '#D9A272', '#B87A4B', '#8A5632', '#E8B98F']
PELO = ['#2B1B12', '#4A2C1A', '#1F1F24', '#C9CDD3', '#7A4A24']


def persona(x, y, w, h, bg, bg2, piel, pelo, ropa, estilo, extra='', i=0):
    cx = x + w / 2; r = min(w, h) * 0.17; cy = y + h * 0.46; base = y + h
    g = f'<defs><linearGradient id="b{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{bg}"/><stop offset="1" stop-color="{bg2}"/></linearGradient><clipPath id="c{i}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="22"/></clipPath></defs>'
    s = f'{g}<g clip-path="url(#c{i})"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#b{i})"/>'
    s += f'<circle cx="{x+w*0.85}" cy="{y+h*0.2}" r="{w*0.22}" fill="#fff" opacity=".13"/>'
    s += f'<path d="M{cx-w*0.46} {base} C{cx-w*0.46} {cy+r*1.9} {cx-w*0.25} {cy+r*1.45} {cx} {cy+r*1.45} C{cx+w*0.25} {cy+r*1.45} {cx+w*0.46} {cy+r*1.9} {cx+w*0.46} {base}Z" fill="{ropa}"/>'
    s += f'<rect x="{cx-r*0.36}" y="{cy+r*0.7}" width="{r*0.72}" height="{r*0.9}" rx="{r*0.3}" fill="{piel}"/>'
    if estilo in ('larga', 'trenzas'):
        s += f'<path d="M{cx-r*1.12} {cy-r*0.2} C{cx-r*1.3} {cy+r*2.2} {cx-r*0.8} {cy+r*2.4} {cx-r*0.6} {cy+r*2.1} L{cx+r*0.6} {cy+r*2.1} C{cx+r*0.8} {cy+r*2.4} {cx+r*1.3} {cy+r*2.2} {cx+r*1.12} {cy-r*0.2}Z" fill="{pelo}"/>'
    s += f'<ellipse cx="{cx}" cy="{cy}" rx="{r*0.92}" ry="{r}" fill="{piel}"/>'
    if estilo == 'corta': s += f'<path d="M{cx-r*0.95} {cy-r*0.1} C{cx-r*1.0} {cy-r*1.25} {cx+r*1.0} {cy-r*1.25} {cx+r*0.95} {cy-r*0.1} C{cx+r*0.5} {cy-r*0.6} {cx-r*0.5} {cy-r*0.6} {cx-r*0.95} {cy-r*0.1}Z" fill="{pelo}"/>'
    if estilo in ('larga', 'trenzas'): s += f'<path d="M{cx-r*1.0} {cy+r*0.1} C{cx-r*1.1} {cy-r*1.3} {cx+r*1.1} {cy-r*1.3} {cx+r*1.0} {cy+r*0.1} C{cx+r*0.7} {cy-r*0.55} {cx-r*0.1} {cy-r*0.75} {cx-r*1.0} {cy+r*0.1}Z" fill="{pelo}"/>'
    if estilo == 'calvo': s += f'<path d="M{cx-r*0.9} {cy-r*0.2} C{cx-r*0.9} {cy-r*0.5} {cx-r*0.7} {cy-r*0.4} {cx-r*0.8} {cy+r*0.2}" fill="none" stroke="{pelo}" stroke-width="{r*0.2}" stroke-linecap="round"/>'
    if estilo == 'gorra': s += f'<path d="M{cx-r*1.0} {cy-r*0.25} C{cx-r*1.0} {cy-r*1.35} {cx+r*1.0} {cy-r*1.35} {cx+r*1.0} {cy-r*0.25}Z" fill="#111318"/><rect x="{cx-r*1.15}" y="{cy-r*0.32}" width="{r*2.3}" height="{r*0.22}" rx="{r*0.1}" fill="#2A2F3A"/>'
    s += f'<circle cx="{cx-r*0.33}" cy="{cy+r*0.08}" r="{r*0.065}" fill="#2A1A12"/><circle cx="{cx+r*0.33}" cy="{cy+r*0.08}" r="{r*0.065}" fill="#2A1A12"/>'
    s += f'<path d="M{cx-r*0.3} {cy+r*0.42} Q{cx} {cy+r*0.68} {cx+r*0.3} {cy+r*0.42}" fill="none" stroke="#8A3B2A" stroke-width="{r*0.07}" stroke-linecap="round"/>'
    if extra == 'gafas': s += f'<circle cx="{cx-r*0.36}" cy="{cy+r*0.08}" r="{r*0.28}" fill="none" stroke="#111318" stroke-width="{r*0.06}"/><circle cx="{cx+r*0.36}" cy="{cy+r*0.08}" r="{r*0.28}" fill="none" stroke="#111318" stroke-width="{r*0.06}"/><path d="M{cx-r*0.08} {cy+r*0.08} h{r*0.16}" stroke="#111318" stroke-width="{r*0.06}"/>'
    return s + '</g>'


OUT = str(Path(__file__).resolve().parent.parent / 'src/publico/img') + '/'
os.makedirs(OUT, exist_ok=True)


def accesorio(x, y, w, h, bg, bg2, piel, pelo, ropa, estilo, extra, i):
    """Persona con diadema o gorra deportiva (se pinta encima)."""
    s = persona(x, y, w, h, bg, bg2, piel, pelo, ropa, estilo, extra, i)
    cx = x + w / 2; r = min(w, h) * 0.17; cy = y + h * 0.46
    if extra == 'diadema':
        s = s.replace('</g>', f'<path d="M{cx-r*0.95} {cy-r*0.42} Q{cx} {cy-r*0.72} {cx+r*0.95} {cy-r*0.42}" fill="none" stroke="#C6F135" stroke-width="{r*0.2}" stroke-linecap="round"/></g>', 1) if False else s[:-4] + f'<path d="M{cx-r*0.95} {cy-r*0.4} Q{cx} {cy-r*0.72} {cx+r*0.95} {cy-r*0.4}" fill="none" stroke="#C6F135" stroke-width="{r*0.2}" stroke-linecap="round"/></g>'
    if extra == 'auriculares':
        s = s[:-4] + f'<path d="M{cx-r*1.0} {cy+r*0.1} C{cx-r*1.0} {cy-r*1.5} {cx+r*1.0} {cy-r*1.5} {cx+r*1.0} {cy+r*0.1}" fill="none" stroke="#111318" stroke-width="{r*0.16}"/><rect x="{cx-r*1.15}" y="{cy-r*0.15}" width="{r*0.32}" height="{r*0.55}" rx="{r*0.12}" fill="#111318"/><rect x="{cx+r*0.83}" y="{cy-r*0.15}" width="{r*0.32}" height="{r*0.55}" rx="{r*0.12}" fill="#111318"/></g>'
    return s


def figura(x, y, w, h, bg, bg2, fig, i):
    cx = x + w / 2; cy = y + h / 2; u = min(w, h)
    g = f'<defs><linearGradient id="f{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{bg}"/><stop offset="1" stop-color="{bg2}"/></linearGradient><clipPath id="q{i}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="22"/></clipPath></defs>'
    s = f'{g}<g clip-path="url(#q{i})"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#f{i})"/>'
    s += f'<circle cx="{x+w*0.12}" cy="{y+h*0.88}" r="{u*0.5}" fill="#fff" opacity=".08"/><circle cx="{x+w*0.92}" cy="{y+h*0.1}" r="{u*0.3}" fill="#fff" opacity=".1"/>'
    if fig == 'pesa':
        a = u * 0.34
        s += f'<g fill="#fff"><rect x="{cx-a*1.5}" y="{cy-a*0.62}" width="{a*0.34}" height="{a*1.24}" rx="{a*0.1}"/><rect x="{cx+a*1.16}" y="{cy-a*0.62}" width="{a*0.34}" height="{a*1.24}" rx="{a*0.1}"/>'
        s += f'<rect x="{cx-a*1.12}" y="{cy-a*0.9}" width="{a*0.42}" height="{a*1.8}" rx="{a*0.12}"/><rect x="{cx+a*0.7}" y="{cy-a*0.9}" width="{a*0.42}" height="{a*1.8}" rx="{a*0.12}"/><rect x="{cx-a*0.7}" y="{cy-a*0.13}" width="{a*1.4}" height="{a*0.26}" rx="{a*0.06}"/></g>'
    if fig == 'cronometro':
        s += f'<circle cx="{cx}" cy="{cy+u*0.04}" r="{u*0.3}" fill="none" stroke="#fff" stroke-width="{u*0.055}"/><rect x="{cx-u*0.07}" y="{cy-u*0.4}" width="{u*0.14}" height="{u*0.07}" rx="{u*0.02}" fill="#fff"/>'
        s += f'<path d="M{cx} {cy+u*0.04} L{cx+u*0.14} {cy-u*0.1}" stroke="#C6F135" stroke-width="{u*0.05}" stroke-linecap="round"/><circle cx="{cx}" cy="{cy+u*0.04}" r="{u*0.03}" fill="#fff"/>'
    if fig == 'pulso':
        s += f'<path d="M{x+w*0.08} {cy} H{cx-u*0.35} L{cx-u*0.2} {cy-u*0.28} L{cx} {cy+u*0.3} L{cx+u*0.18} {cy-u*0.1} L{cx+u*0.28} {cy} H{x+w*0.92}" fill="none" stroke="#C6F135" stroke-width="{u*0.045}" stroke-linecap="round" stroke-linejoin="round"/>'
    if fig == 'kettlebell':
        s += f'<path d="M{cx-u*0.15} {cy-u*0.05} C{cx-u*0.2} {cy-u*0.36} {cx+u*0.2} {cy-u*0.36} {cx+u*0.15} {cy-u*0.05}" fill="none" stroke="#fff" stroke-width="{u*0.07}" stroke-linecap="round"/>'
        s += f'<circle cx="{cx}" cy="{cy+u*0.12}" r="{u*0.27}" fill="#fff"/><circle cx="{cx}" cy="{cy+u*0.12}" r="{u*0.11}" fill="{bg2}" opacity=".35"/>'
    if fig == 'tarjeta':
        s += f'<rect x="{cx-u*0.34}" y="{cy-u*0.22}" width="{u*0.68}" height="{u*0.44}" rx="{u*0.06}" fill="#fff"/><rect x="{cx-u*0.34}" y="{cy-u*0.1}" width="{u*0.68}" height="{u*0.08}" fill="{bg2}" opacity=".5"/><circle cx="{cx-u*0.2}" cy="{cy+u*0.09}" r="{u*0.06}" fill="{bg2}"/><rect x="{cx-u*0.1}" y="{cy+u*0.06}" width="{u*0.3}" height="{u*0.05}" rx="{u*0.02}" fill="{bg2}" opacity=".6"/>'
    return s + '</g>'


P = PIEL; H = PELO
T = []
T.append(accesorio(0, 0, 200, 170, '#F26A1B', '#C2410C', P[1], H[0], '#111318', 'corta', 'diadema', 0))
T.append(accesorio(210, 0, 200, 260, '#1F2430', '#111318', P[2], H[1], '#F26A1B', 'trenzas', 'auriculares', 1))
T.append(figura(420, 0, 220, 150, '#111318', '#2A2F3A', 'pulso', 2))
T.append(accesorio(0, 180, 200, 200, '#C6F135', '#8DB41A', P[0], H[3], '#111318', 'gorra', '', 3))
T.append(accesorio(420, 160, 220, 220, '#FF8A3D', '#E5570F', P[3], H[0], '#FFFFFF', 'larga', 'diadema', 4))
T.append(figura(210, 270, 200, 110, '#F26A1B', '#9A3412', 'pesa', 5))
T.append(accesorio(0, 390, 200, 170, '#2A2F3A', '#111318', P[4], H[2], '#C6F135', 'corta', 'auriculares', 6))
T.append(figura(210, 390, 200, 170, '#C6F135', '#7FA312', 'cronometro', 7))
T.append(accesorio(420, 390, 220, 170, '#F26A1B', '#C2410C', P[1], H[4], '#111318', 'calvo', 'gafas', 8))
open(OUT + 'collage.svg', 'w').write('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 560" role="img" aria-label="Personas entrenando">' + ''.join(T) + '</svg>')

# Teléfono con la tarjeta de socio
open(OUT + 'telefono.svg', 'w').write('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 360" role="img" aria-label="Tarjeta de socio en el teléfono">
<defs><linearGradient id="t" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF8A3D"/><stop offset="1" stop-color="#C2410C"/></linearGradient></defs>
<circle cx="170" cy="190" r="150" fill="#fff" opacity=".08"/><circle cx="262" cy="70" r="44" fill="#C6F135" opacity=".95"/><circle cx="52" cy="300" r="30" fill="#F26A1B"/>
<g transform="rotate(8 170 190)"><rect x="95" y="25" width="150" height="300" rx="26" fill="#0B0C0F"/><rect x="103" y="37" width="134" height="276" rx="19" fill="#F7F5F2"/><rect x="146" y="30" width="48" height="8" rx="4" fill="#0B0C0F"/>
<rect x="113" y="60" width="114" height="74" rx="12" fill="url(#t)"/><rect x="123" y="72" width="50" height="7" rx="3.5" fill="#fff"/><rect x="123" y="86" width="74" height="6" rx="3" fill="#fff" opacity=".75"/><rect x="123" y="98" width="58" height="6" rx="3" fill="#fff" opacity=".75"/><rect x="123" y="116" width="40" height="8" rx="4" fill="#C6F135"/>
<g fill="#fff" opacity=".9"><rect x="195" y="112" width="4" height="14" rx="1"/><rect x="214" y="112" width="4" height="14" rx="1"/><rect x="199" y="116" width="15" height="6" rx="1"/><rect x="190" y="116" width="4" height="6" rx="1"/><rect x="219" y="116" width="4" height="6" rx="1"/></g>
<rect x="113" y="146" width="114" height="44" rx="10" fill="#fff" stroke="#E7E2DB"/><circle cx="133" cy="168" r="11" fill="#E3F6E8"/><path d="M127 168l4 4 8-9" fill="none" stroke="#16A34A" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><rect x="151" y="158" width="58" height="6" rx="3" fill="#111318"/><rect x="151" y="170" width="40" height="5" rx="2.5" fill="#A8A29E"/>
<rect x="113" y="200" width="114" height="44" rx="10" fill="#fff" stroke="#E7E2DB"/><circle cx="133" cy="222" r="11" fill="#FFF0E3"/><path d="M127 222h12M133 216v12" stroke="#F26A1B" stroke-width="3" stroke-linecap="round"/><rect x="151" y="212" width="52" height="6" rx="3" fill="#111318"/><rect x="151" y="224" width="34" height="5" rx="2.5" fill="#A8A29E"/>
<rect x="113" y="262" width="114" height="34" rx="17" fill="#C6F135"/><rect x="141" y="276" width="58" height="6" rx="3" fill="#111318"/></g></svg>''')

# Logo (se usa en la wallet, en el favicon y en la metadata del emisor)
open(OUT + 'logo.svg', 'w').write('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF8A3D"/><stop offset="1" stop-color="#C2410C"/></linearGradient></defs><rect x="2" y="2" width="60" height="60" rx="15" fill="url(#lg)"/><g fill="#fff"><rect x="10" y="23" width="6" height="18" rx="2"/><rect x="48" y="23" width="6" height="18" rx="2"/><rect x="16" y="19" width="6" height="26" rx="2"/><rect x="42" y="19" width="6" height="26" rx="2"/><rect x="22" y="29.5" width="20" height="5" rx="1.5"/></g><path d="M10 52h44" stroke="#C6F135" stroke-width="3.5" stroke-linecap="round"/></svg>''')

# Fondo de la tarjeta en la wallet (1000×600): la wallet pone un velo arriba y abajo, el logo arriba a la izquierda y
# el estado abajo a la derecha, así que los motivos van a la derecha y al centro.
rayas = ''.join(f'<rect x="{-200 + k*70}" y="-50" width="26" height="800" fill="#C6F135" opacity="{0.05 if k % 2 else 0.09}" transform="rotate(24 500 300)"/>' for k in range(0, 22))
disco = ''.join(f'<circle cx="760" cy="300" r="{r}" fill="none" stroke="#fff" stroke-opacity="{o}" stroke-width="{w}"/>' for r, o, w in [(230, .10, 22), (170, .14, 14), (110, .2, 10), (40, .3, 24)])
open(OUT + 'tarjeta-fondo.svg', 'w').write(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 600" width="1000" height="600"><defs>
<linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F26A1B"/><stop offset=".55" stop-color="#C2410C"/><stop offset="1" stop-color="#1A0F08"/></linearGradient>
<radialGradient id="luz" cx=".78" cy=".42" r=".55"><stop offset="0" stop-color="#fff" stop-opacity=".18"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>
<rect width="1000" height="600" fill="url(#g)"/>{rayas}<rect width="1000" height="600" fill="url(#luz)"/>{disco}
<text x="-20" y="585" font-family="DejaVu Sans, sans-serif" font-size="12" letter-spacing="2" fill="#fff" fill-opacity=".14">{("CUMBRE GYM · SOCIO · " * 14)}</text></svg>''')
print('ilustraciones listas')
