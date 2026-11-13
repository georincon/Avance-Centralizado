#!/usr/bin/env python3
"""Colección de Postman de la integración con la plataforma de notarías (SIGNO QA): API REST de órdenes de emisión.

Usa el marco del generador general (../marco.py). Escribe en esta carpeta:
  Integracion-Notarias.postman_collection.json · Integracion.postman_environment.json (sin clave)
  Integracion-local.postman_environment.json (con la clave; NO se comparte) · PRUEBAS-CURL.md

  python3 postman/pruebasintegracion/generar.py              # solo genera
  python3 postman/pruebasintegracion/generar.py --verificar  # además ejecuta cada prueba contra https://localhost
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent.parent
sys.path.insert(0, str(AQUI.parent))

from marco import R, Carpeta, a_coleccion, a_entorno, a_curl_completo   # noqa: E402
from generar import Ejecutor                                            # noqa: E402

DOMINIO = 'https://civica-desarrollo.avance.org.co'
API = '/notaria-issuer/api/v1'
CLAVE = {'X-API-Key': '{{api_key}}'}
FE_DE_VIDA = {'titular_nombre': 'Ana María Pérez Gómez', 'titular_documento': '1020304050', 'medio_identificacion': 'Cédula de ciudadanía',
              'entidad_destino': 'Colpensiones', 'vigente_hasta': '2027-12-31'}
PODER = {'poderdante_nombre': 'Carlos Andrés Rojas', 'poderdante_documento': '79123456', 'apoderado_nombre': 'Laura Torres Díaz',
         'apoderado_documento': '52987654', 'facultades': 'Representarlo en la venta del vehículo de placas ABC123.',
         'vigente_hasta': '2027-12-31', 'asunto': 'Venta de vehículo'}

# ------------------------------------------------------------------------------------------------ 01
CONEXION = Carpeta('01 · Conexión y catálogo',
    'Antes de crear órdenes: comprobar que la clave (`X-API-Key`) funciona y qué documentos se pueden emitir, con los campos que la plataforma debe enviar de cada uno.',
    [R('Sin clave: debe rechazar', 'GET', f'{API}/documentos', 'Toda la API exige la cabecera `X-API-Key`. Sin ella responde **401** y no revela nada.',
       espera=401, igual={'error.codigo': 'no_autenticado'}),
     R('Catálogo de documentos', 'GET', f'{API}/documentos',
       'Los nueve documentos notariales que emite la notaría. De cada uno: `documento` (el identificador que se usa al crear la orden), `vct`, `nombre`, '
       '`campos_requeridos` y `campos_opcionales` (solo los que escribe la plataforma; los demás —número de protocolo, fecha, notario, etc.— los pone la notaría).',
       cabeceras=CLAVE, tiene=['documentos.0.documento', 'documentos.0.campos_requeridos'])])

# ------------------------------------------------------------------------------------------------ 02
CREAR = Carpeta('02 · (a) Crear la orden de emisión',
    'La plataforma de notarías envía el documento que quiere emitir y sus datos. La orden queda guardada (vive 24 h) y todavía no hay QR: ese paso es el siguiente. '
    '`referencia` es el identificador del trámite **en la plataforma**: sirve para no duplicar (repetir la misma petición devuelve la misma orden).',
    [R('Crear una orden (fe de vida)', 'POST', f'{API}/ordenes',
       'Crea la orden. **201** la primera vez; **200** si ya existía una orden con esa `referencia` y los mismos datos. Guarda el `id` en la variable `orden_id`. '
       'Cambia `referencia` en el entorno si quieres crear una orden nueva.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}', 'datos': FE_DE_VIDA},
       espera=[200, 201], igual={'documento': 'co.notaria.fe_de_vida'}, tiene=['id', 'estado', 'expira_en'], guarda={'orden_id': 'id'}),
     R('Repetir la misma petición (idempotencia)', 'POST', f'{API}/ordenes',
       'Misma `referencia` y mismos datos: **200** y la misma orden, sin duplicarla. Así la plataforma puede reintentar sin riesgo si perdió la respuesta.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}', 'datos': FE_DE_VIDA}, espera=200, tiene=['id']),   # que el id sea el mismo lo comprueba verificar()
     R('Misma referencia con otros datos: debe rechazar', 'POST', f'{API}/ordenes',
       'Una `referencia` ya usada con datos distintos es un conflicto (**409**): evita pisar un trámite por error.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}', 'datos': {**FE_DE_VIDA, 'entidad_destino': 'Otra entidad'}},
       espera=409, igual={'error.codigo': 'referencia_en_uso'}),
     R('Crear una orden (poder especial)', 'POST', f'{API}/ordenes',
       'Otro documento, con sus propios campos (ver el catálogo). Guarda el `id` en `orden_poder_id`; se usa más abajo para probar la cancelación.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.poder_especial', 'referencia': '{{referencia}}-poder', 'datos': PODER},
       espera=[200, 201], tiene=['id'], guarda={'orden_poder_id': 'id'}),
     R('Faltan campos obligatorios: debe rechazar', 'POST', f'{API}/ordenes',
       '**400** con la lista de campos que faltan en `error.campos`.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}-x1', 'datos': {'titular_nombre': 'Ana'}},
       espera=400, igual={'error.codigo': 'campos_faltantes'}, tiene=['error.campos']),
     R('Campo que pone la notaría: debe rechazar', 'POST', f'{API}/ordenes',
       'El número de protocolo, la fecha, el notario… los pone la notaría al emitir. Enviarlos es un error (**400**), no se ignoran en silencio.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}-x2', 'datos': {**FE_DE_VIDA, 'numero': '2026-99999'}},
       espera=400, igual={'error.codigo': 'campos_desconocidos'}),
     R('Documento inexistente: debe rechazar', 'POST', f'{API}/ordenes',
       '**400** y la lista de documentos válidos en `error.validos`.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.no_existe', 'referencia': '{{referencia}}-x3', 'datos': FE_DE_VIDA},
       espera=400, igual={'error.codigo': 'documento_desconocido'}),
     R('Fecha ya vencida: debe rechazar', 'POST', f'{API}/ordenes',
       'La vigencia de una fe de vida no puede estar en el pasado: **422**. Las fechas van como `AAAA-MM-DD`.',
       cabeceras=CLAVE, cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}-x4', 'datos': {**FE_DE_VIDA, 'vigente_hasta': '2020-01-01'}},
       espera=422, igual={'error.codigo': 'datos_no_validos'}),
     R('Sin clave: debe rechazar', 'POST', f'{API}/ordenes', 'Sin `X-API-Key` no se crea nada (**401**).',
       cuerpo={'documento': 'co.notaria.fe_de_vida', 'referencia': '{{referencia}}-x5', 'datos': FE_DE_VIDA}, espera=401)])

# ------------------------------------------------------------------------------------------------ 03
OFERTA = Carpeta('03 · (b) Obtener el QR / enlace de oferta',
    'Con la orden creada, la plataforma pide la oferta de emisión. Devuelve el `enlace` (para abrirlo con la wallet en el mismo teléfono), el `qr_png_base64` '
    '(para mostrarlo y que otro dispositivo lo escanee) y el `tx_code`: un PIN de 5 dígitos que **se entrega a la persona por otro medio** (la wallet lo pide al recibir el documento). '
    'La oferta vive 60 minutos; si vence, esta misma llamada genera una nueva mientras la orden siga vigente.',
    [R('Obtener la oferta', 'GET', f'{API}/ordenes/{{{{orden_id}}}}/oferta',
       'Pasa la orden a `OFERTA_GENERADA`. **Con la wallet:** abre el `enlace` o escanea el QR (guárdalo como PNG: el texto después de `base64,`), y escribe el `tx_code` cuando lo pida.',
       cabeceras=CLAVE, tiene=['enlace', 'qr_png_base64', 'tx_code', 'expira_en'], igual={'estado': 'OFERTA_GENERADA'}),
     R('Pedirla otra vez (misma oferta)', 'GET', f'{API}/ordenes/{{{{orden_id}}}}/oferta',
       'Mientras la oferta no venza, devuelve la misma (`regenerada: false`): se puede consultar las veces que haga falta.',
       cabeceras=CLAVE, igual={'regenerada': False}),
     R('Orden inexistente', 'GET', f'{API}/ordenes/00000000-0000-4000-8000-000000000000/oferta', '**404**: el id no existe (o es de otro cliente).',
       cabeceras=CLAVE, espera=404, igual={'error.codigo': 'orden_no_encontrada'}),
     R('Id mal formado', 'GET', f'{API}/ordenes/no-es-un-uuid/oferta', 'También **404**, sin error interno.',
       cabeceras=CLAVE, espera=404),
     R('Sin clave: debe rechazar', 'GET', f'{API}/ordenes/{{{{orden_id}}}}/oferta', '**401**.', espera=401)])

# ------------------------------------------------------------------------------------------------ 04
ESTADO = Carpeta('04 · (c) Consultar el estado del proceso',
    'La plataforma pregunta cuándo quiera en qué va la orden. `estado` es uno de: `CREADA` (sin oferta aún) · `OFERTA_GENERADA` (esperando a la wallet) · '
    '`OFERTA_EXPIRADA` (el QR venció; pedir la oferta otra vez) · `EMITIDA` (la wallet ya recibió el documento) · `CANCELADA` · `EXPIRADA` (la orden venció a las 24 h). '
    '`eventos` es la bitácora de la orden.',
    [R('Estado de la orden', 'GET', f'{API}/ordenes/{{{{orden_id}}}}',
       'Estado actual, fechas y la bitácora (`eventos`). Tras pedir la oferta debe verse `OFERTA_GENERADA`. **Con la wallet:** después de recibir el documento pasa a `EMITIDA` y aparece `emitida_en`.',
       cabeceras=CLAVE, tiene=['id', 'estado', 'eventos.0.evento', 'oferta.expira_en'], igual={'estado': 'OFERTA_GENERADA'}),
     R('Estado de una orden sin oferta', 'GET', f'{API}/ordenes/{{{{orden_poder_id}}}}', 'Una orden recién creada está en `CREADA` y `oferta` es `null`.',
       cabeceras=CLAVE, igual={'estado': 'CREADA'}),
     R('Orden inexistente', 'GET', f'{API}/ordenes/00000000-0000-4000-8000-000000000000', '**404**.', cabeceras=CLAVE, espera=404),
     R('Sin clave: debe rechazar', 'GET', f'{API}/ordenes/{{{{orden_id}}}}', '**401**.', espera=401)])

# ------------------------------------------------------------------------------------------------ 05
CANCELAR = Carpeta('05 · Cancelar una orden',
    'Si el trámite se anula en la plataforma antes de que la persona reciba el documento, la orden se cancela: la oferta deja de servir.',
    [R('Cancelar la orden', 'POST', f'{API}/ordenes/{{{{orden_poder_id}}}}/cancelar', 'Pasa a `CANCELADA`. El `motivo` es opcional.',
       cabeceras=CLAVE, cuerpo={'motivo': 'Trámite anulado en SIGNO'}, igual={'estado': 'CANCELADA'}),
     R('Pedir la oferta de una orden cancelada: debe rechazar', 'GET', f'{API}/ordenes/{{{{orden_poder_id}}}}/oferta', '**409**: no se entrega oferta de una orden cancelada.',
       cabeceras=CLAVE, espera=409, igual={'error.codigo': 'orden_cancelada'}),
     R('Cancelar otra vez: debe rechazar', 'POST', f'{API}/ordenes/{{{{orden_poder_id}}}}/cancelar', '**409**: ya está en un estado final.',
       cabeceras=CLAVE, espera=409, igual={'error.codigo': 'estado_final'}),
     R('Estado final', 'GET', f'{API}/ordenes/{{{{orden_poder_id}}}}', 'La bitácora termina en `CANCELADA`.', cabeceras=CLAVE, igual={'estado': 'CANCELADA'})])

CARPETAS = [CONEXION, CREAR, OFERTA, ESTADO, CANCELAR]
VARIABLES = {'base_url': DOMINIO, 'api_key': '', 'referencia': 'SIGNO-QA-001'}   # orden_id y orden_poder_id los llenan las pruebas (variables de la colección)
INTERNAS = {'orden_id': '', 'orden_poder_id': ''}


def clave_local() -> str:
    try:
        return next(iter(json.loads((RAIZ / 'eudi-srv-issuer-pid-notaria/secretos/api-ordenes.json').read_text()).values()))
    except OSError:
        return ''


def verificar() -> int:
    ex = Ejecutor('https://localhost', {**VARIABLES, **INTERNAS, 'api_key': clave_local(), 'referencia': f'SIGNO-QA-{int(time.time())}'})
    mal = 0
    for c in CARPETAS:
        print(f'\n== {c.titulo}')
        for r in c.pruebas:
            cod, texto, fallos = ex.ejecutar(r)
            if r.nombre.startswith('Repetir la misma') and not fallos and json.loads(texto)['id'] != ex.v['orden_id']:
                fallos.append('el id no es el de la primera orden')
            print(f'  {"✔" if not fallos else "✘"}  {r.nombre}  [{r.metodo} {ex.sub(r.ruta)[-48:]}] -> {cod}' + ('' if not fallos else '   <-- ' + '; '.join(fallos) + ' :: ' + texto[:150]))
            mal += bool(fallos)
    print(f'\nCon problemas: {mal}')
    return mal


def curl_md() -> str:
    s = ['# Integración con notarías · pruebas con curl\n',
         'Las mismas pruebas de la colección, para la terminal. Hace falta `jq` para leer las respuestas.\n',
         '```bash', f'export BASE_URL={DOMINIO}', 'export API_KEY=<la clave que le entregaron>', 'export REFERENCIA=SIGNO-QA-001   # cámbiala para crear una orden nueva', '```\n']
    for c in CARPETAS:
        s += [f'## {c.titulo}\n', c.desc + '\n']
        for r in c.pruebas:
            s += [f'### {r.nombre}\n', (r.desc or '') + '\n', '```bash', a_curl_completo(r, False), '```\n']
    return '\n'.join(s)


def main() -> int:
    mal = verificar() if '--verificar' in sys.argv else 0
    colec = a_coleccion('Integración con notarías · API de órdenes de emisión',
                        'API REST que consume la plataforma de notarías (SIGNO): (a) crear la orden de emisión, (b) obtener el QR/enlace de oferta y (c) consultar el estado. '
                        'Importa también el entorno, escribe tu `api_key` y ejecuta las carpetas en orden. Documento de consumo: `API-ORDENES-EMISION.md`.',
                        CARPETAS, {**VARIABLES, **INTERNAS})
    colec['info']['_postman_id'] = 'c4e1a9d2-3b7f-4e08-8a55-integracion-notarias'
    dump = lambda o: json.dumps(o, indent=2, ensure_ascii=False) + '\n'
    (AQUI / 'Integracion-Notarias.postman_collection.json').write_text(dump(colec), encoding='utf-8')
    (AQUI / 'Integracion.postman_environment.json').write_text(dump(a_entorno('Integración notarías · civica-desarrollo', VARIABLES, {'api_key'})), encoding='utf-8')
    (AQUI / 'Integracion-local.postman_environment.json').write_text(
        dump(a_entorno('Integración notarías · civica-desarrollo (con clave)', {**VARIABLES, 'api_key': clave_local()}, {'api_key'})), encoding='utf-8')
    (AQUI / 'PRUEBAS-CURL.md').write_text(curl_md(), encoding='utf-8')
    print('\nGenerado en', AQUI, f'({sum(len(c.pruebas) for c in CARPETAS)} pruebas)')
    return 1 if mal else 0


if __name__ == '__main__':
    sys.exit(main())
