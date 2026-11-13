#!/usr/bin/env python3
"""Genera el modelo físico (entidad-relación) del esquema `auditoria` leyendo la base REAL.
   Uso: python3 generar-modelo.py   -> modelo-entidad-relacion.svg / .png / .pdf  (requiere docker y google-chrome)"""
import json, subprocess, html, datetime, pathlib

SQL = r"""
SELECT json_build_object(
 'tablas', (SELECT json_agg(t ORDER BY t.nombre) FROM (
   SELECT c.relname nombre,
     (SELECT json_agg(json_build_object('n',a.attname,'t',format_type(a.atttypid,a.atttypmod),
        'pk', COALESCE(a.attnum = ANY (SELECT unnest(conkey) FROM pg_constraint WHERE conrelid=c.oid AND contype='p'),false),
        'fks', (SELECT COALESCE(json_agg(k.conname ORDER BY k.conname),'[]'::json) FROM pg_constraint k WHERE k.conrelid=c.oid AND k.contype='f' AND a.attnum = ANY (k.conkey))
      ) ORDER BY a.attnum) FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped) cols
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='auditoria' AND c.relkind='r') t),
 'relaciones', (SELECT json_agg(json_build_object('hijo',cl.relname,'padre',cp.relname,'nombre',k.conname,
        'cols',(SELECT json_agg(a.attname) FROM pg_attribute a WHERE a.attrelid=k.conrelid AND a.attnum = ANY (k.conkey))))
   FROM pg_constraint k JOIN pg_class cl ON cl.oid=k.conrelid JOIN pg_class cp ON cp.oid=k.confrelid
   JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE k.contype='f' AND n.nspname='auditoria')
)::text;"""
out = subprocess.run(['docker','exec','auditoria-db','psql','-U','auditoria','-d','auditoria','-At','-c',SQL],capture_output=True,text=True,check=True).stdout
db = json.loads(out)
T = {t['nombre']: t for t in db['tablas']}

# ── disposición (x, y) de cada tabla ──
W, FILA, TIT = 690, 27, 40
NC, TC = 300, 250   # ancho de la columna de nombres y de tipos
XA = 70; XB = XA+W+290; XC = XB+W+250; XD = XC+W+290
POS = {'usuario':(XA,110),'medio_contacto':(XA,790),'wallet':(XB,110),'dispositivo':(XB,450),'sesion':(XB,1200),
       'actividad':(XC,110),'actividad_elemento':(XC,800),'contraparte':(XD,110),'tipo_actividad':(XD,350),
       'credencial':(XD,640),'consulta_dashboard':(XD,1010)}
alto = lambda n: TIT + len(T[n]['cols'])*FILA + 8
caja = lambda n: (POS[n][0], POS[n][1], W, alto(n))

# numeración fk1, fk2... por tabla
for t in T.values():
    nombres = sorted({f for c in t['cols'] for f in c['fks']})
    t['fkn'] = {f:i+1 for i,f in enumerate(nombres)}

# ── relaciones: ruta ortogonal (puntos) + etiqueta manual, con flecha hacia el PADRE (como en el modelo de referencia) ──
def L(n,lado,dy=0.5,dx=0.5):
    x,y,w,h = caja(n)
    return {'izq':(x,y+h*dy),'der':(x+w,y+h*dy),'sup':(x+w*dx,y),'inf':(x+w*dx,y+h)}[lado]
RUTAS = {
 ('medio_contacto','usuario'):   ([L('medio_contacto','sup',dx=.2),L('usuario','inf',dx=.2)],'contacto_usuario'),
 ('wallet','usuario'):           ([(XB,215),(XA+W,215)],'wallet_usuario'),
 ('sesion','usuario'):           ([(XB,1330),(XA+W+145,1330),(XA+W+145,520),(XA+W,520)],'sesion_usuario',0.2),
 ('actividad','usuario'):        ([(XC,150),(XB+W+100,150),(XB+W+100,60),(XA+380,60),(XA+380,110)],'actividad_usuario'),
 ('dispositivo','wallet'):       ([L('dispositivo','sup',dx=.5),L('wallet','inf',dx=.5)],'dispositivo_wallet'),
 ('sesion','wallet'):            ([(XB+W,1250),(XB+W+50,1250),(XB+W+50,300),(XB+W,300)],'sesion_wallet'),
 ('actividad','wallet'):         ([(XC,235),(XB+W,235)],'actividad_wallet'),
 ('credencial','wallet'):        ([(XD,700),(XD-150,700),(XD-150,1130),(XB+W+100,1130),(XB+W+100,360),(XB+W,360)],'credencial_wallet'),
 ('actividad','sesion'):         ([(XC,430),(XB+W+150,430),(XB+W+150,1380),(XB+W,1380)],'actividad_sesion',0.85),
 ('actividad','contraparte'):    ([(XC+W,150),(XD,150)],'actividad_contraparte'),
 ('actividad','tipo_actividad'): ([(XC+W,430),(XD,430)],'actividad_tipo'),
 ('credencial','contraparte'):   ([(XD+W,700),(XD+W+80,700),(XD+W+80,180),(XD+W,180)],'credencial_emisor'),
 ('actividad_elemento','actividad'): ([L('actividad_elemento','sup',dx=.5),L('actividad','inf',dx=.5)],'elemento_actividad'),
}

def esc(s): return html.escape(str(s))
svg=[]; add=svg.append
anchoT = XD+W+300; altoT = 1760
add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{anchoT}" height="{altoT}" viewBox="0 0 {anchoT} {altoT}" font-family="DejaVu Sans, Arial, sans-serif">')
add('<defs><marker id="f" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="13" markerHeight="13" orient="auto"><path d="M0 0 L12 6 L0 12 z" fill="#111"/></marker></defs>')
add(f'<rect width="{anchoT}" height="{altoT}" fill="#fff"/><rect x="14" y="14" width="{anchoT-28}" height="{altoT-28}" fill="none" stroke="#222" stroke-width="2"/>')

# relaciones primero (quedan detrás de las cajas); las etiquetas se dibujan al final, encima de todas las líneas
etiquetas_svg = []
for r in db['relaciones']:
    ruta, etiqueta, *resto = RUTAS[(r['hijo'], r['padre'])]; frac = resto[0] if resto else 0.5
    pts = ' '.join(f'{x:.0f},{y:.0f}' for x,y in ruta)
    add(f'<polyline points="{pts}" fill="none" stroke="#111" stroke-width="2" marker-end="url(#f)"/>')
    seg = max(range(len(ruta)-1), key=lambda i: abs(ruta[i+1][0]-ruta[i][0])+abs(ruta[i+1][1]-ruta[i][1]))
    (x1,y1),(x2,y2) = ruta[seg], ruta[seg+1]
    mx,my = x1+(x2-x1)*frac, y1+(y2-y1)*frac; ancho = len(etiqueta)*10.6+10
    if abs(y2-y1) < abs(x2-x1):   # tramo horizontal: encima de la línea
        tx,ty,anc = mx, my-9, 'middle'; rx = mx-ancho/2
    else:                          # tramo vertical: a la derecha de la línea
        tx,ty,anc = mx+10, my, 'start'; rx = mx+6
    etiquetas_svg.append(f'<rect x="{rx:.0f}" y="{ty-18:.0f}" width="{ancho:.0f}" height="25" fill="#fff"/>')
    etiquetas_svg.append(f'<text x="{tx:.0f}" y="{ty:.0f}" font-family="Comic Sans MS, DejaVu Sans, cursive" font-style="italic" font-size="19" fill="#1a3b8f" text-anchor="{anc}">{esc(etiqueta)}</text>')

svg.extend(etiquetas_svg)

# cabecera del modelo (como el recuadro "Physical Data Model")
hx,hy,hw,hh = XA,1380,W,300
add(f'<rect x="{hx}" y="{hy}" width="{hw}" height="{hh}" rx="26" fill="#fff" stroke="#111" stroke-width="2"/>')
add(f'<text x="{hx+hw/2}" y="{hy+42}" font-size="24" font-weight="700" text-anchor="middle">Physical Data Model</text>')
fecha = datetime.date.today().strftime('%d/%m/%Y')
for i,(k,v) in enumerate([('Model:','Auditoría de wallets Avance'),('Package:','auditoria (PostgreSQL)'),('Diagram:','Modelo de datos de auditoría'),('Author:','Identidad Digital · Avance'),('Version:','1.0')]):
    yy = hy+85+i*42
    add(f'<line x1="{hx}" x2="{hx+hw}" y1="{yy-28}" y2="{yy-28}" stroke="#111"/>')
    add(f'<text x="{hx+14}" y="{yy}" font-size="21">{k}</text><text x="{hx+120}" y="{yy}" font-size="21" font-weight="600">{esc(v)}</text>')
add(f'<text x="{hx+hw-14}" y="{hy+hh-14}" font-size="19" text-anchor="end">Date: {fecha}</text>')

# tablas
for nombre,t in T.items():
    x,y,w,h = caja(nombre)
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff" stroke="#111" stroke-width="2"/>')
    add(f'<text x="{x+w/2}" y="{y+27}" font-size="21" font-weight="700" text-anchor="middle">{esc(nombre.upper())}</text>')
    add(f'<line x1="{x}" x2="{x+w}" y1="{y+TIT-4}" y2="{y+TIT-4}" stroke="#111" stroke-width="2"/>')
    for i,c in enumerate(t['cols']):
        yy = y+TIT+(i+1)*FILA-8
        marcas = (['pk'] if c['pk'] else []) + [f'fk{t["fkn"][f]}' for f in c['fks']]
        deco = ' text-decoration="underline"' if c['pk'] else ''
        add(f'<text x="{x+16}" y="{yy}" font-size="16"{deco}>{esc(c["n"])}</text>')
        add(f'<text x="{x+16+NC}" y="{yy}" font-size="16">{esc(c["t"])}</text>')
        if marcas: add(f'<text x="{x+w-14}" y="{yy}" font-size="16" text-anchor="end"{deco}>&lt;{",".join(marcas)}&gt;</text>')
add('</svg>')
base = pathlib.Path(__file__).parent
(base/'modelo-entidad-relacion.svg').write_text('\n'.join(svg), encoding='utf-8')
print('SVG listo; tablas:', len(T), 'relaciones:', len(db['relaciones']))

# el PDF también se publica en el login del dashboard (frontend/modelo-entidad-relacion.pdf): copiarlo tras regenerar
