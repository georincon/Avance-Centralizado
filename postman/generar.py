#!/usr/bin/env python3
"""Genera la colección de Postman, los entornos y las guías, y puede ejecutar todas las pruebas contra el sistema.

    python3 postman/generar.py              # genera los archivos
    python3 postman/generar.py --verificar  # además ejecuta cada prueba contra el sistema real (https://localhost)

Lo compartible (colección, entorno de ejemplo, guías) NO lleva claves ni datos personales. Las claves y los datos de prueba
van solo a archivos locales que no se suben a git (*.local.*, DATOS-LOCALES.md).
"""
from __future__ import annotations

import datetime
import http.cookiejar
import json
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

from marco import R, a_coleccion, a_curl_completo, a_entorno, a_shell, mayus, _descripcion
from definiciones import CARPETAS

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent
DOMINIO = 'https://civica-desarrollo.avance.org.co'
SECRETOS = {'panel_token', 'tl_operator_key', 'tl_api_key', 'andes_client_secret', 'kc_admin_password', 'eps_clave', 'gym_clave', 'kc_token', 'andes_token'}


# ------------------------------------------------------------------------------------- lectura de claves

def leer_env(ruta: Path) -> dict:
    datos = {}
    if ruta.exists():
        for linea in ruta.read_text(encoding='utf-8').splitlines():
            if '=' in linea and not linea.lstrip().startswith('#'):
                k, v = linea.split('=', 1)
                datos[k.strip()] = v.strip().strip('"')
    return datos


def docker(*args: str) -> str:
    try:
        return subprocess.run(['docker', *args], capture_output=True, text=True, timeout=30).stdout.strip()
    except Exception:
        return ''


def env_contenedor(nombre: str) -> dict:
    salida = docker('inspect', nombre, '--format', '{{range .Config.Env}}{{println .}}{{end}}')
    return dict(l.split('=', 1) for l in salida.splitlines() if '=' in l)


def claves_locales() -> dict:
    """Todo lo que NO se comparte: claves de los .env y las credenciales de desarrollo documentadas en ENLACES.md."""
    k: dict = {}
    k['panel_token'] = leer_env(RAIZ / 'panel-control/docker-compose/.env').get('CONTROL_PANEL_TOKEN', '')
    tl = leer_env(RAIZ / 'srv-tl-publisher/docker-compose/.env')
    k['tl_operator_key'], k['tl_api_key'] = tl.get('TL_OPERATOR_KEY', ''), tl.get('TL_PUBLISHER_API_KEY', '')
    k['pki_api_key'] = tl.get('PKI_ISSUE_API_KEY', '')
    k['eps_clave'] = leer_env(RAIZ / 'verifiers/eps/.env').get('ADMIN_CLAVE', '')
    k['gym_clave'] = leer_env(RAIZ / 'verifiers/gym/.env').get('ADMIN_CLAVE', '')
    andes = leer_env(RAIZ / 'srv-andes-scd/.env')
    k['andes_client_id'], k['andes_client_secret'] = andes.get('CLIENT_ID', 'wallet-client'), andes.get('CLIENT_SECRET', '')
    enlaces = (RAIZ / 'ENLACES.md').read_text(encoding='utf-8') if (RAIZ / 'ENLACES.md').exists() else ''
    for etiqueta, clave in (('Panel admin de la Registraduría', 'reg_admin'), ('Keycloak de la Registraduría', 'kc_admin'), ('Keycloak de la UIS', 'kc_uis')):
        m = re.search(r'\|\s*' + re.escape(etiqueta) + r'[^|]*\|\s*`([^`]+)`\s*\|\s*`([^`]+)`', enlaces)
        k[clave + '_user'], k[clave + '_password'] = (m.group(1), m.group(2)) if m else ('', '')
    k['kc_admin_user'], k['kc_admin_password'] = k['kc_admin_user'] or 'admin', k['kc_admin_password']
    return k


def lista_estado_wallet_provider() -> str:
    """Ruta (sin dominio) de una lista de estado real del Wallet Provider, tomada de las credenciales ya emitidas."""
    url = docker('exec', 'postgres-reg', 'psql', '-U', 'pid_issuer', '-d', 'pid_issuer', '-At', '-c', 'select client_status_list_uri from issued_credential limit 1')
    return url.replace(DOMINIO, '') if url.startswith(DOMINIO) else ''


def variables_base() -> dict:
    return {
        'base_url': DOMINIO, 'instancia': 'banco', 'fecha_vuelo': (datetime.date.today() + datetime.timedelta(days=30)).isoformat(),
        'status_list_ruta': lista_estado_wallet_provider(),
        'solicitud_id': '', 'transaction_id': '', 'intended_use_id': '', 'session_id': '', 'dev_code': '', 'x5c_chain': '[]',
        'panel_token': '', 'tl_api_key': '', 'tl_operator_key': '', 'eps_clave': '', 'gym_clave': '',
        'andes_client_id': 'wallet-client', 'andes_client_secret': '', 'andes_token': '', 'kc_admin_user': 'admin', 'kc_admin_password': '', 'kc_token': '',
    }


# ------------------------------------------------------------------------------------------ verificación

class Ejecutor:
    def __init__(self, base: str, variables: dict):
        self.base, self.v = base.rstrip('/'), dict(variables)
        self.ctx = ssl.create_default_context()
        self.ctx.check_hostname, self.ctx.verify_mode = False, ssl.CERT_NONE
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), urllib.request.HTTPSHandler(context=self.ctx), _SinRedireccion())

    def sub(self, t):
        return re.sub(r'\{\{(\w+)\}\}', lambda m: str(self.v.get(m.group(1), '')), t) if isinstance(t, str) else t

    def reinicia_sesion(self):
        self.jar.clear()

    def ejecutar(self, r: R):
        ruta = self.sub(r.ruta)
        cab = {k: self.sub(v) for k, v in r.cabeceras.items()}
        datos = None
        if r.form is not None:
            datos = '&'.join(f'{k}={self.sub(v)}' for k, v in r.form.items()).encode()
            cab.setdefault('Content-Type', 'application/x-www-form-urlencoded')
        elif r.cuerpo is not None:
            crudo = r.cuerpo if isinstance(r.cuerpo, str) else json.dumps(r.cuerpo)
            datos = self.sub(crudo).encode()
            cab.setdefault('Content-Type', 'application/json')
        req = urllib.request.Request(self.base + ruta, data=datos, method=r.metodo, headers=cab)
        try:
            with self.op.open(req, timeout=30) as resp:
                codigo, cuerpo = resp.status, resp.read()
        except urllib.error.HTTPError as e:
            codigo, cuerpo = e.code, e.read()
        texto = cuerpo.decode('utf-8', 'replace')
        try:
            js = json.loads(texto)
        except Exception:
            js = None
        fallos = []
        esp = r.espera if isinstance(r.espera, list) else [r.espera]
        if codigo not in esp:
            fallos.append(f'HTTP {codigo} (se esperaba {esp})')
        dig = lambda o, p: __import__('functools').reduce(lambda a, k: (a[int(k)] if isinstance(a, list) and k.isdigit() else a.get(k) if isinstance(a, dict) else None), p.split('.'), o)
        for p in r.tiene:
            if js is None or dig(js, p) is None:
                fallos.append(f'falta {p}')
        for p, val in r.igual.items():
            if js is None or dig(js, p) != val:
                fallos.append(f'{p} = {dig(js, p) if js else None!r} (se esperaba {val!r})')
        if r.texto and r.texto not in texto:
            fallos.append(f'no contiene {r.texto!r}')
        if not fallos:
            for var, p in r.guarda.items():
                val = dig(js, p) if js else None
                if val is not None:
                    self.v[var] = val if isinstance(val, str) else json.dumps(val)
        return codigo, texto, fallos


class _SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def verificar(base: str, claves: dict) -> dict:
    v = variables_base()
    v.update({k: claves.get(k, v.get(k, '')) for k in ('panel_token', 'tl_api_key', 'tl_operator_key', 'eps_clave', 'gym_clave', 'andes_client_id', 'andes_client_secret', 'kc_admin_user', 'kc_admin_password')})
    ex = Ejecutor(base, v)
    ejemplos, bien, mal, saltadas = {}, 0, 0, 0
    for c in CARPETAS:
        print(f'\n== {c.titulo}')
        ex.reinicia_sesion()
        for r in c.pruebas:
            if r.salta_verificacion:
                saltadas += 1
                print(f'  ·  {r.nombre}  (no se ejecuta: {r.salta_verificacion})')
                continue
            if r.privada and not all(ex.v.get(m, True) != '' for m in re.findall(r'\{\{(\w+)\}\}', json.dumps([r.cabeceras, r.cuerpo, r.form]))):
                saltadas += 1
                print(f'  ·  {r.nombre}  (falta la clave en este equipo)')
                continue
            codigo, texto, fallos = ex.ejecutar(r)
            marca = '✔' if not fallos else '✘'
            print(f'  {marca}  {r.nombre}  [{r.metodo} {ex.sub(r.ruta)[:70]}] -> {codigo}' + ('' if not fallos else '   <-- ' + '; '.join(fallos)))
            bien, mal = (bien + 1, mal) if not fallos else (bien, mal + 1)
            ejemplos[(c.titulo, r.nombre)] = (codigo, texto)
    print(f'\nResultado: {bien} bien, {mal} con problemas, {saltadas} no ejecutadas.')
    return {'ejemplos': ejemplos, 'mal': mal}


# ----------------------------------------------------------------------------------------------- guías

def recorta(texto: str, n: int = 260) -> str:
    texto = re.sub(r'\s+', ' ', texto).strip()
    return texto if len(texto) <= n else texto[:n] + ' …'


def comprobaciones(r: R) -> list[str]:
    esp = r.espera if isinstance(r.espera, list) else [r.espera]
    out = ['HTTP ' + ' o '.join(map(str, esp))]
    out += [f'trae `{p}`' for p in r.tiene]
    out += [f'`{p}` = `{json.dumps(v, ensure_ascii=False)}`' for p, v in r.igual.items()]
    if r.texto:
        out.append({'<': 'devuelve una página', 'eyJ': 'devuelve una lista firmada (JWT)', '<svg': 'devuelve una imagen SVG'}.get(r.texto, f'el cuerpo contiene `{r.texto}`'))
    return out


def guia_postman(ejemplos: dict) -> str:
    L = ['# Pruebas del ecosistema con Postman', '',
         f'Colección lista para importar: **`Avance-EUDI.postman_collection.json`**. Dominio de las pruebas: `{DOMINIO}` (variable `base_url`).', '',
         '## Cómo empezar (3 pasos)', '',
         '1. **Importa** en Postman: `Import` → arrastra `Avance-EUDI.postman_collection.json` y `Avance.postman_environment.json`.',
         '2. **Elige el entorno** «Avance · civica-desarrollo» (arriba a la derecha). Para las pruebas que usan claves, copia los valores de `DATOS-LOCALES.md` a las variables del entorno (las claves no se comparten y por eso vienen vacías).',
         '3. **Ejecuta la carpeta `00 · Humo`** con el *Runner* (clic derecho en la carpeta → *Run folder*). Si sale en verde, todo el ecosistema está en pie.', '',
         '## Cómo leer una prueba', '',
         'Cada petición trae su explicación (pestaña *Docs*) y **comprobaciones automáticas** (pestaña *Test Results* tras enviarla): el código HTTP, los campos que debe traer y, a veces, valores concretos. '
         'Las pruebas que dependen de la anterior (por ejemplo crear una solicitud y luego consultarla) pasan los datos solas mediante variables (`solicitud_id`, `transaction_id`, `session_id`…): '
         '**ejecútalas en orden**, de arriba abajo, o usa *Run folder*.', '',
         '> Las pruebas marcadas **«Paso manual»** necesitan una persona con la wallet (abrir el enlace `haip-vp://…` o escanear el QR). Sin ese paso devuelven «esperando», que también es el resultado correcto antes de que la wallet responda.', '',
         '## Variables del entorno', '',
         '| Variable | Para qué sirve | Se comparte |', '|---|---|---|',
         '| `base_url` | Dominio del ecosistema | sí |', '| `instancia` | Entidad cuyo verifier se prueba (`banco`, `eps`, `gym`…) | sí |',
         '| `fecha_vuelo` | Fecha para buscar vuelos (hoy + 30 días) | sí |', '| `status_list_ruta` | Ruta de una lista de estado real del Wallet Provider | sí |',
         '| `solicitud_id`, `transaction_id`, `session_id`, `dev_code`, `intended_use_id`, `x5c_chain`, `andes_token`, `kc_token` | Las rellenan las pruebas solas | — |',
         '| `panel_token` | Token de la API del panel (`CONTROL_PANEL_TOKEN`) | **no** |', '| `tl_api_key`, `tl_operator_key` | Claves del publicador de listas | **no** |',
         '| `eps_clave`, `gym_clave` | Clave de administración de la EPS y del gimnasio | **no** |', '| `andes_client_id`, `andes_client_secret` | Cliente de la wallet ante Andes SCD | **no** |',
         '| `kc_admin_user`, `kc_admin_password` | Administrador de Keycloak | **no** |', '',
         '## Las pruebas, proyecto por proyecto', '']
    for c in CARPETAS:
        L += [f'### {c.titulo}', '', c.desc, '']
        for i, r in enumerate(c.pruebas, 1):
            L += [f'**{i}. {r.nombre}** — `{r.metodo} {r.ruta}`', '', _descripcion(r), '',
                  '*Comprobaciones automáticas:* ' + '; '.join(comprobaciones(r)) + '.']
            ej = ejemplos.get((c.titulo, r.nombre))
            if ej and not r.privada and r.texto != '<':
                L += ['', '*Respuesta real de ejemplo:*', '```', f'HTTP {ej[0]}  ' + recorta(ej[1]), '```']
            L.append('')
    return '\n'.join(L)


def guia_curl(ejemplos: dict) -> str:
    L = ['# Pruebas del ecosistema desde la terminal (curl)', '',
         'Las mismas pruebas de la colección de Postman, escritas como comandos `curl`, agrupadas por proyecto y con cada paso explicado. '
         f'Dominio: `{DOMINIO}`.', '',
         '## Preparación', '',
         '```bash', f'export BASE_URL={DOMINIO}', 'export JAR=$(mktemp)        # guarda las cookies de sesión entre comandos',
         '# Claves (no se comparten): copia los valores de DATOS-LOCALES.md', 'export PANEL_TOKEN=...      # pruebas del panel', 'export TL_API_KEY=...       # publicar listas',
         'export EPS_CLAVE=...  GYM_CLAVE=...', 'export ANDES_CLIENT_ID=wallet-client ANDES_CLIENT_SECRET=...', 'export KC_ADMIN_USER=admin KC_ADMIN_PASSWORD=...',
         'export INSTANCIA=banco      # entidad cuyo verifier se prueba', f'export FECHA_VUELO={(datetime.date.today() + datetime.timedelta(days=30)).isoformat()}', '```', '',
         '- `-s` silencia el progreso y `-k` acepta el certificado de desarrollo.',
         '- `jq` da formato al JSON (`sudo apt install jq`). Sin `jq`, quita `| jq .`.',
         '- Los comandos de cada proyecto están pensados para ejecutarse **en orden**: unos guardan datos en variables de la terminal (`SOLICITUD_ID`, `TRANSACTION_ID`…) que usan los siguientes.',
         '- Las pruebas marcadas **Paso manual** necesitan una persona con la wallet; sin ella, «esperando» es el resultado correcto.', '',
         '## Ver todo en pie de un vistazo', '', '```bash',
         'for p in banco contratacion discoteca beca acceso pensiones migracion eps notaria gym aerolinea cancilleria; do',
         '  printf "%-14s " $p; curl -sk "$BASE_URL/$p/salud"; echo', 'done', '```', '']
    for c in CARPETAS:
        L += [f'## {c.titulo}', '', c.desc, '']
        usa_cookies = any(x in c.titulo for x in ('Banco', 'Contratación', 'Discoteca', 'Instituto', 'Edificio', 'Fondo', 'Migración', 'EPS', 'Notaría', 'Gym', 'Páramo', 'Cancillería'))
        for i, r in enumerate(c.pruebas, 1):
            L += [f'### {i}. {r.nombre}', '', r.desc.strip()]
            if r.manual:
                L += ['', f'> **Paso manual:** {r.manual}']
            L += ['', '```bash', a_curl_completo(r, usa_cookies), '```', '',
                  '**Qué debes ver:** ' + '; '.join(comprobaciones(r)) + '.']
            ej = ejemplos.get((c.titulo, r.nombre))
            if ej and not r.privada and r.texto != '<':
                L += ['', 'Ejemplo de respuesta real:', '```', recorta(ej[1]), '```']
            L.append('')
    return '\n'.join(L)


def datos_locales(claves: dict) -> str:
    """Claves, bases de datos y datos de prueba. SOLO LOCAL: no se sube a git."""
    def pg(c, u, d, sql):
        return docker('exec', c, 'psql', '-U', u, '-d', d, '-At', '-F', ' | ', '-c', sql)
    puertos = [('uis_academic (Postgres)', 'postgres-uis-academic', 5433), ('uis_issuer (Postgres)', 'postgres-uis-issuer', 5434), ('spartans / Wallet Provider (Postgres)', 'postgres-spartans', 5435),
               ('onboarding (Postgres)', 'onboarding-postgres', 5436), ('pid_issuer / Registraduría (Postgres)', 'postgres-reg', 5441)]
    L = ['# Datos locales del ecosistema (NO COMPARTIR)', '',
         f'Generado el {datetime.datetime.now():%Y-%m-%d %H:%M}. Este archivo y `Avance-local.postman_environment.json` contienen claves y datos personales de prueba: están en `.gitignore` y no deben subirse ni enviarse.', '',
         '## Claves de acceso', '', '| Qué | Usuario | Clave / token |', '|---|---|---|',
         f'| API del panel de control | — | `{claves["panel_token"]}` |', f'| Panel admin de la Registraduría | `{claves["reg_admin_user"]}` | `{claves["reg_admin_password"]}` |',
         f'| Keycloak de la Registraduría (`/idp-reg/admin`) | `{claves["kc_admin_user"]}` | `{claves["kc_admin_password"]}` |', f'| Keycloak de la UIS (`/idp-uis/admin`) | `{claves["kc_uis_user"]}` | `{claves["kc_uis_password"]}` |',
         f'| Publicador de listas · clave del operador (UI) | — | `{claves["tl_operator_key"]}` |', f'| Publicador de listas · API de publicación (`X-API-Key`) | — | `{claves["tl_api_key"]}` |',
         f'| PKI · API de emisión | — | `{claves["pki_api_key"]}` |', f'| Andes SCD · cliente `{claves["andes_client_id"]}` | `{claves["andes_client_id"]}` | `{claves["andes_client_secret"]}` |',
         f'| EPS · administración | — | `{claves["eps_clave"]}` |', f'| Gimnasio · administración y puerta | — | `{claves["gym_clave"]}` |', '']
    ids = {'status-list-reg': 'reg', 'status-list-uis': 'uis', 'status-list-spartans': 'spartans'}
    L += ['### Claves de los servicios de listas de estado (uso interno)', '', '| Servicio | `API_key` |', '|---|---|']
    for c in ids:
        L.append(f'| `{c}` | `{env_contenedor(c).get("API_key", "")}` |')
    L += ['', 'Se usan en `POST /take` y `POST /set` (cabecera `X-API-Key`) dentro de la red Docker; no están publicadas hacia afuera.', '',
          '## Bases de datos (para DBeaver o `psql`)', '', 'Todas escuchan solo en `127.0.0.1` (desde esta máquina). Los puertos son fijos.', '',
          '| Base | Host | Puerto | Base de datos | Usuario | Clave |', '|---|---|---|---|---|---|']
    for nombre, cont, pto in puertos:
        e = env_contenedor(cont)
        L.append(f'| {nombre} | `127.0.0.1` | `{pto}` | `{e.get("POSTGRES_DB", "")}` | `{e.get("POSTGRES_USER", "")}` | `{e.get("POSTGRES_PASSWORD", "")}` |')
    m = env_contenedor('tlp-db')
    L += [f'| trusted_lists / tl-publisher (**MySQL**) | `127.0.0.1` | `3307` | `{m.get("MYSQL_DATABASE", "")}` | `{m.get("MYSQL_USER", "")}` | `{m.get("MYSQL_PASSWORD", "")}` (root: `{m.get("MYSQL_ROOT_PASSWORD", "")}`) |', '',
          '**Cadenas JDBC:** `jdbc:postgresql://127.0.0.1:5433/uis_academic` · `…:5434/uis_issuer` · `…:5435/spartans` · `…:5436/onboarding` · `…:5441/pid_issuer` · `jdbc:mysql://127.0.0.1:3307/trusted_lists`.', '',
          'Desde la terminal: `docker exec -it postgres-uis-academic psql -U uis_academic -d uis_academic`.', '']
    L += ['## Qué hay en cada base', '']
    estructura = [('pid_issuer (Registraduría)', 'postgres-reg', 'pid_issuer', 'pid_issuer'), ('uis_academic', 'postgres-uis-academic', 'uis_academic', 'uis_academic'),
                  ('uis_issuer', 'postgres-uis-issuer', 'uis_issuer', 'uis_issuer'), ('spartans', 'postgres-spartans', 'spartans', 'spartans'), ('onboarding', 'onboarding-postgres', 'onboarding', 'onboarding')]
    for titulo, cont, usr, bd in estructura:
        tablas = pg(cont, usr, bd, "select table_name || ' (' || (xpath('/row/c/text()', query_to_xml('select count(*) as c from public.' || quote_ident(table_name), false, true, '')))[1]::text || ')' from information_schema.tables where table_schema='public' order by 1")
        L.append(f'- **{titulo}:** ' + (', '.join(tablas.splitlines()) if tablas else '(sin acceso)'))
    mt = docker('exec', 'tlp-db', 'sh', '-c', 'mysql -utlp_user -p"$MYSQL_PASSWORD" trusted_lists -N -e "select count(*) from trusted_lists; select count(*) from trust_services; select count(*) from trust_service_providers" 2>/dev/null').split()
    if len(mt) == 3:
        L.append(f'- **trusted_lists (MySQL):** {mt[0]} listas, {mt[1]} servicios de confianza, {mt[2]} proveedores.')
    L += ['', '## Datos de prueba para repetir las pruebas', '',
          '### Ciudadanos de la Registraduría (tabla `cedulas`)', '', '```', pg('postgres-reg', 'pid_issuer', 'pid_issuer', "select documento_numero, nombres || ' ' || apellidos, fecha_nacimiento, lugar_nacimiento_ciudad from cedulas order by id"), '```', '',
          'Con estos datos el ciudadano pide su PID en `/pid-registraduria/registraduria/solicitar` (después del onboarding).', '',
          '### Estudiantes de la UIS (tabla `datos_estudiantes`)', '', 'Usuario y clave para entrar a `/uis-issuer/issuer/login` y generar el QR del carnet:', '',
          '```', 'usuario | clave | nombre | documento', pg('postgres-uis-academic', 'uis_academic', 'uis_academic', "select usuario_autenticacion, clave_autenticacion, nombres || ' ' || apellidos, numero_identificacion from datos_estudiantes order by id_estudiante"), '```', '']
    L += ['### Credenciales emitidas durante las pruebas', '',
          '| Emisor | Qué ha emitido | Cantidad |', '|---|---|---|']
    for fila in pg('postgres-reg', 'pid_issuer', 'pid_issuer', "select credential_type || ' (' || credential_format || ')', count(*) from issued_credential group by 1 order by 2 desc").splitlines():
        t, n = fila.rsplit(' | ', 1)
        L.append(f'| Registraduría (PID) | {t} | {n} |')
    L.append(f'| UIS | credenciales académicas y carnets | {pg("postgres-uis-issuer", "uis_issuer", "uis_issuer", "select count(*) from academic_credential")} |')
    L += ['', 'Cada copia de una credencial en la wallet cuenta como una emisión (el PID se entrega por lotes de copias de un solo uso).', '']
    return '\n'.join(L)


def catalogo_credenciales() -> str:
    """Catálogo de credenciales que ofrece cada emisor, leído en vivo de sus metadatos."""
    filas = ['| Emisor | Credencial | Formato | Tipo (`vct` / `doctype`) |', '|---|---|---|---|']
    for nombre, url in (('Registraduría', '/pid-registraduria/.well-known/openid-credential-issuer'), ('UIS', '/uis-issuer/.well-known/openid-credential-issuer'),
                        ('EPS', '/.well-known/openid-credential-issuer/eps'), ('Notaría', '/.well-known/openid-credential-issuer/notaria'), ('Gimnasio', '/.well-known/openid-credential-issuer/gym'),
                        ('Aerolínea', '/.well-known/openid-credential-issuer/aerolinea'), ('Cancillería', '/.well-known/openid-credential-issuer/cancilleria')):
        try:
            ctx = ssl.create_default_context(); ctx.check_hostname, ctx.verify_mode = False, ssl.CERT_NONE
            d = json.load(urllib.request.urlopen(urllib.request.Request('https://localhost' + url, headers={'Accept': 'application/json'}), context=ctx, timeout=20))
        except Exception:
            continue
        for k, v in d.get('credential_configurations_supported', {}).items():
            if k.endswith('_deferred'):
                continue
            filas.append(f'| {nombre} | `{k}` | {v.get("format")} | `{v.get("vct") or v.get("doctype")}` |')
    return '\n'.join(filas)


# -------------------------------------------------------------------------------------------------- main

def escribir(ruta: Path, contenido) -> None:
    ruta.write_text(contenido if isinstance(contenido, str) else json.dumps(contenido, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main() -> int:
    claves = claves_locales()
    ejemplos, fallos = {}, 0
    if '--verificar' in sys.argv:
        res = verificar('https://localhost', claves)
        ejemplos, fallos = res['ejemplos'], res['mal']
    variables = variables_base()
    colec = a_coleccion('Avance · Ecosistema EUDI', 'Pruebas reales de todo el ecosistema EUDI de Avance, una carpeta por proyecto. Empieza por **00 · Humo**. Guía: `GUIA-POSTMAN.md`.', CARPETAS, variables)
    escribir(AQUI / 'Avance-EUDI.postman_collection.json', colec)
    publico = {k: v for k, v in variables.items() if k not in SECRETOS and k not in {'panel_token', 'tl_api_key', 'tl_operator_key', 'eps_clave', 'gym_clave', 'andes_client_secret', 'kc_admin_password'}}
    sec = {'panel_token', 'tl_api_key', 'tl_operator_key', 'eps_clave', 'gym_clave', 'andes_client_secret', 'kc_admin_password', 'kc_token', 'andes_token'}
    escribir(AQUI / 'Avance.postman_environment.json', a_entorno('Avance · civica-desarrollo', {**variables}, sec))
    local = dict(variables)
    local.update({k: claves.get(k, '') for k in ('panel_token', 'tl_api_key', 'tl_operator_key', 'eps_clave', 'gym_clave', 'andes_client_id', 'andes_client_secret', 'kc_admin_user', 'kc_admin_password')})
    escribir(AQUI / 'Avance-local.postman_environment.json', a_entorno('Avance · civica-desarrollo (con claves)', local, sec))
    escribir(AQUI / 'GUIA-POSTMAN.md', guia_postman(ejemplos))
    escribir(AQUI / 'PRUEBAS-CURL.md', guia_curl(ejemplos))
    escribir(AQUI / 'DATOS-LOCALES.md', datos_locales(claves) + '\n## Catálogo de credenciales que ofrece cada emisor\n\n' + catalogo_credenciales() + '\n')
    escribir(AQUI / '.gitignore', 'Avance-local.postman_environment.json\nDATOS-LOCALES.md\n*.local.*\n__pycache__/\n')
    print('\nArchivos generados en', AQUI)
    return 1 if fallos else 0


if __name__ == '__main__':
    sys.exit(main())
