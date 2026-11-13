#!/usr/bin/env python3
"""Colección de Postman de la notaría de ejemplo (verifiers/notaria): portal, trámites, verificación y emisor OpenID4VCI.

Usa el marco del generador general (../marco.py). Escribe en esta carpeta:
  Notaria.postman_collection.json  ·  Notaria.postman_environment.json  ·  PRUEBAS-CURL.md

  python3 postman/notaria/generar.py              # solo genera
  python3 postman/notaria/generar.py --verificar  # además ejecuta cada prueba contra https://localhost
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parent))

from marco import R, Carpeta, a_coleccion, a_entorno, a_curl_completo   # noqa: E402
from generar import Ejecutor                                            # noqa: E402

DOMINIO = 'https://civica-desarrollo.avance.org.co'
SHA = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08'
SUBPRUEBA = ('Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. '
             'Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.')

# ------------------------------------------------------------------------------------------- 01
ESTADO = Carpeta('01 · Estado del servicio',
    'Comprobaciones rápidas, sin sesión ni claves. Si estas dos salen en verde, la notaría está arriba y se puede seguir.',
    [R('Salud', 'GET', '/notaria/salud', 'Responde `ok` si el portal de la notaría está en pie.', igual={'estado': 'ok'}),
     R('Portal (página principal)', 'GET', '/notaria/',
       'La página que ve la persona: elegir el trámite, identificarse, llenarlo y recibirlo en la wallet. Crea la cookie de sesión.', texto='<')])

# ------------------------------------------------------------------------------------------- 02
IDENTIFICARSE = Carpeta('02 · Identificarse con el PID',
    'Antes de cualquier trámite la persona presenta su PID desde la wallet, a través del verifier de la notaría (`verifier-notaria`). '
    'La notaría crea la solicitud y devuelve el enlace `haip-vp://…` (y el QR); la wallet responde y la notaría consulta el resultado. '
    'Los datos de la persona (documento, nombres, nacimiento y foto) salen del PID verificado: nadie los escribe.',
    [R('Crear solicitud de identificación', 'POST', '/notaria/api/solicitud',
       'Pide el PID con documento, nombres, apellidos, nacimiento y foto. Devuelve el `id`, el `enlace` para la wallet y el `qr` (imagen). '
       'Con `"dispositivo": "mismo"` la wallet vuelve al navegador al terminar.',
       cuerpo={'proposito': 'identificacion'}, tiene=['id', 'enlace', 'qr'], guarda={'solicitud_id': 'id'}),
     R('Consultar el resultado', 'GET', '/notaria/api/solicitud/{{solicitud_id}}',
       'El navegador pregunta cada par de segundos si la wallet ya respondió: `esperando` hasta que la persona presenta el PID; después `listo` con sus datos.',
       manual='abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es `esperando`.',
       tiene=['estado']),
     R('Quién soy (sesión)', 'GET', '/notaria/api/yo',
       'Los datos de la persona identificada en esta sesión. Antes de identificarse con la wallet responde 401.', espera=[200, 401]),
     R('Cerrar la identificación', 'POST', '/notaria/api/salir', 'Olvida a la persona de esta sesión (hay que volver a identificarse).', igual={'ok': True}),
     R('Propósito vacío: debe rechazar', 'POST', '/notaria/api/solicitud',
       'Hay que decir `identificacion`, o `verificacion` con un tipo de documento.', cuerpo={}, espera=400),
     R('Verificar un tipo inexistente: debe rechazar', 'POST', '/notaria/api/solicitud',
       'Un `tipo` que la notaría no emite no abre ninguna solicitud.', cuerpo={'proposito': 'verificacion', 'tipo': 'no-existe'}, espera=400),
     R('Solicitud desconocida', 'GET', '/notaria/api/solicitud/id-que-no-existe', 'Un id que no es de esta sesión no revela nada.', espera=404, igual={'estado': 'rechazada'})])

# ------------------------------------------------------------------------------------------- 03
TRAMITES = Carpeta('03 · Trámites (requieren identificarse)',
    'Los nueve trámites notariales: declaración extraproceso, poder especial, reconocimiento de documento, fe de vida, autenticación de firma, '
    'copia de escritura, registro civil, convivencia y permiso de salida de menor. Cada trámite queda en el protocolo con un número y se entrega '
    'a la wallet con una oferta (QR) y un PIN de 4 dígitos. ' + SUBPRUEBA + ' Hay que ser mayor de edad (se comprueba con la fecha de nacimiento del PID).',
    [R('Mis trámites', 'GET', '/notaria/api/tramites', 'El protocolo de la persona identificada, del más reciente al más antiguo. ' + SUBPRUEBA, espera=[200, 401]),
     R('Otorgar un poder especial', 'POST', '/notaria/api/tramites',
       'Crea el trámite y devuelve la oferta: `enlace` (`openid-credential-offer://…`), `pin` de 4 dígitos y `vence`. La oferta dura 15 minutos y sirve una vez. ' + SUBPRUEBA,
       cuerpo={'tipo': 'poder', 'datos': {'apoderado_nombre': 'Carlos Andrés Rojas Pérez', 'apoderado_documento': '1020304050',
                                          'facultades': 'Representarme ante la entidad de salud para reclamar mis medicamentos.', 'vigente_hasta': '2027-12-31'}},
       espera=[201, 401], guarda={'tramite_numero': 'tramite.numero'}),
     R('Declaración extraproceso', 'POST', '/notaria/api/tramites', 'Declaración bajo juramento (Decreto 1557 de 1989). ' + SUBPRUEBA,
       cuerpo={'tipo': 'declaracion', 'datos': {'finalidad': 'Caja de compensación familiar', 'declaracion': 'Declaro bajo la gravedad del juramento que convivo con mi familia y dependo económicamente de mis ingresos.'}},
       espera=[201, 401]),
     R('Fe de vida', 'POST', '/notaria/api/tramites', 'Constancia de que la persona vive (válida 30 días). Pide confirmar que comparece personalmente. ' + SUBPRUEBA,
       cuerpo={'tipo': 'fe_vida', 'datos': {'confirmo': True, 'entidad_destino': 'Fondo de pensiones'}}, espera=[201, 401]),
     R('Reconocimiento de documento (solo la huella)', 'POST', '/notaria/api/tramites',
       'El archivo **no sale del navegador**: el portal calcula su SHA-256 y solo la huella, el nombre y el tamaño van al documento. ' + SUBPRUEBA,
       cuerpo={'tipo': 'reconocimiento', 'datos': {'documento_nombre': 'contrato-arrendamiento.pdf', 'documento_sha256': SHA, 'documento_bytes': 48213}}, espera=[201, 401]),
     R('Trámite desconocido: debe rechazar', 'POST', '/notaria/api/tramites', 'Un `tipo` que la notaría no emite. ' + SUBPRUEBA,
       cuerpo={'tipo': 'no-existe', 'datos': {}}, espera=[400, 401]),
     R('Datos inválidos: debe rechazar', 'POST', '/notaria/api/tramites',
       'La notaría valida lo que se declara: aquí el documento del apoderado no son dígitos y la facultad es muy corta. ' + SUBPRUEBA,
       cuerpo={'tipo': 'poder', 'datos': {'apoderado_nombre': 'Carlos Rojas', 'apoderado_documento': 'abc', 'facultades': 'corta'}}, espera=[400, 401]),
     R('Pedir de nuevo la oferta', 'POST', '/notaria/api/tramites/{{tramite_numero}}/oferta',
       'Vuelve a mostrar el QR y el PIN de un trámite que todavía no está en ninguna wallet. Si ya se entregó, responde 409: para recibirlo otra vez hay que pedir la **reexpedición** (la copia anterior queda revocada). ' + SUBPRUEBA,
       cuerpo={}, espera=[200, 401, 404, 409]),
     R('Reexpedir (la copia anterior queda revocada)', 'POST', '/notaria/api/tramites/{{tramite_numero}}/oferta',
       'Para quien perdió la wallet: emite una nueva copia y revoca la anterior en la lista de estado. ' + SUBPRUEBA,
       cuerpo={'reexpedir': True}, espera=[200, 401, 404, 409]),
     R('Anular el trámite (revocar)', 'POST', '/notaria/api/tramites/{{tramite_numero}}/anular',
       'Anula el trámite: su bit en la lista de estado pasa a 1 y quien lo verifique lo rechaza («el documento fue anulado»). En un poder es revocarlo. ' + SUBPRUEBA,
       cuerpo={'motivo': 'Prueba de Postman'}, espera=[200, 401, 404]),
     R('Anular un trámite ajeno: debe rechazar', 'POST', '/notaria/api/tramites/999999/anular', 'Solo se anula un trámite propio.', cuerpo={}, espera=[401, 404])])

# ------------------------------------------------------------------------------------------- 04
VERIFICAR = Carpeta('04 · Verificar un documento notarial',
    'Cualquiera puede comprobar un documento notarial: elige el tipo, la notaría se lo pide a la wallet de quien lo muestra y dice si es auténtico y sigue vigente. '
    'Un poder revocado no pasa. Cambia la variable `tipo_documento` por: declaracion, poder, reconocimiento, fe_vida, autenticacion, copia_escritura, registro_civil, convivencia o permiso_menor.',
    [R('Crear solicitud de verificación', 'POST', '/notaria/api/solicitud',
       'Pide a la wallet el documento del tipo `{{tipo_documento}}` con todos sus datos.',
       cuerpo={'proposito': 'verificacion', 'tipo': '{{tipo_documento}}'}, tiene=['id', 'enlace', 'qr'], guarda={'solicitud_id': 'id'}),
     R('Consultar el resultado', 'GET', '/notaria/api/solicitud/{{solicitud_id}}',
       'Mientras la wallet no responde dice `esperando`; después entrega los datos ya verificados (firma del emisor, lista PUBEAA, estado y prueba de posesión), o el motivo del rechazo.',
       manual='abre el enlace `haip-vp://…` con la wallet que tiene el documento y acepta compartirlo.', tiene=['estado'])])

# ------------------------------------------------------------------------------------------- 05
EMISOR = Carpeta('05 · Emisor OpenID4VCI 1.0 (lo que hace la wallet)',
    'El emisor entrega cada documento como SD-JWT VC por **código preautorizado con PIN**. No hay `authorization_code`: los documentos se piden en el portal, no desde la wallet. '
    'El token exige la **WIA** de una wallet certificada (lista WP) y DPoP, y el documento queda ligado a la clave del dispositivo. '
    'Estas pruebas cubren lo público y los rechazos; la entrega completa necesita la wallet (o `verifiers/notaria/pruebas/probar-notaria.sh`).',
    [R('Metadatos del emisor', 'GET', '/.well-known/openid-credential-issuer/notaria',
       'Lo primero que lee la wallet: los nueve documentos que ofrece (`co.notaria.*`), su formato (`dc+sd-jwt`), sus datos y los colores de cada tarjeta. Es pública y con CORS.',
       tiene=['credential_issuer', 'credential_configurations_supported']),
     R('Metadatos (ubicación alterna)', 'GET', '/notaria/.well-known/openid-credential-issuer', 'La misma metadata al final de la ruta, que algunas wallets también prueban.',
       tiene=['credential_issuer']),
     R('Servidor de autorización', 'GET', '/.well-known/oauth-authorization-server/notaria', 'Dónde pedir el token y qué exige (solo código preautorizado).',
       tiene=['token_endpoint']),
     R('Oferta desconocida', 'GET', '/notaria/oferta/no-existe', 'La oferta (la que va en el QR) dura 15 minutos y sirve una vez; una desconocida o vencida da 404.',
       espera=404),
     R('Token con otro tipo de grant: debe rechazar', 'POST', '/notaria/token', 'Solo se acepta `urn:ietf:params:oauth:grant-type:pre-authorized_code`.',
       form={'grant_type': 'authorization_code', 'code': 'x'}, espera=400, igual={'error': 'unsupported_grant_type'}),
     R('Token con código inexistente: debe rechazar', 'POST', '/notaria/token', 'Un código que venció o ya se usó: se pide otra oferta en la notaría.',
       form={'grant_type': 'urn:ietf:params:oauth:grant-type:pre-authorized_code', 'pre-authorized_code': 'no-existe', 'tx_code': '0000'}, espera=400, igual={'error': 'invalid_grant'}),
     R('Nonce', 'POST', '/notaria/nonce', 'Nonce de un solo uso para que la wallet firme su prueba de posesión al pedir la credencial.', tiene=['c_nonce']),
     R('Credencial sin token: debe rechazar', 'POST', '/notaria/credencial', 'Sin `Authorization` no se entrega nada.',
       cuerpo={'credential_configuration_id': 'co.notaria.poder_especial'}, espera=401, igual={'error': 'invalid_token'}),
     R('Lista de estado (revocación)', 'GET', '/notaria/estado/1',
       'Lista firmada (`statuslist+jwt`) con un bit por trámite: 1 = anulado o reexpedido. La consultan los verifiers para saber si el documento sigue vigente. No se guarda en caché.',
       texto='eyJ')])

CARPETAS = [ESTADO, IDENTIFICARSE, TRAMITES, VERIFICAR, EMISOR]
VARIABLES = {'base_url': DOMINIO, 'tipo_documento': 'poder', 'solicitud_id': '', 'tramite_numero': ''}


def verificar() -> int:
    ex = Ejecutor('https://localhost', VARIABLES)
    mal = 0
    for c in CARPETAS:
        print(f'\n== {c.titulo}')
        ex.reinicia_sesion()
        for r in c.pruebas:
            cod, _, fallos = ex.ejecutar(r)
            print(f'  {"✔" if not fallos else "✘"}  {r.nombre}  [{r.metodo} {ex.sub(r.ruta)[:60]}] -> {cod}' + ('' if not fallos else '   <-- ' + '; '.join(fallos)))
            mal += bool(fallos)
    print(f'\nCon problemas: {mal}')
    return mal


def curl_md() -> str:
    s = ['# Notaría · pruebas con curl\n',
         'Las mismas pruebas de la colección, para la terminal. `JAR` guarda la cookie de sesión (la identificación con la wallet vive en esa sesión).\n',
         '```bash', f'export BASE_URL={DOMINIO}', 'export JAR=$(mktemp)', 'export TIPO_DOCUMENTO=poder', '```\n']
    for c in CARPETAS:
        s += [f'## {c.titulo}\n', c.desc + '\n']
        for r in c.pruebas:
            s += [f'### {r.nombre}\n', (r.desc or '') + '\n', '```bash', a_curl_completo(r, True), '```\n']
            if r.manual:
                s.append(f'**Paso manual:** {r.manual}\n')
    return '\n'.join(s)


def main() -> int:
    mal = verificar() if '--verificar' in sys.argv else 0
    colec = a_coleccion('Notaría de ejemplo · Notaría Avance',
                        'Portal, trámites notariales, verificación y emisor OpenID4VCI de `verifiers/notaria`. Empieza por **01 · Estado del servicio**. '
                        'Guía: `README.md`. Cambia `base_url` en el entorno para apuntar a `https://localhost`.',
                        CARPETAS, VARIABLES)
    colec['info']['_postman_id'] = 'b3d8c0a1-7e2f-4c6a-9b11-notaria-avance'
    (AQUI / 'Notaria.postman_collection.json').write_text(json.dumps(colec, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (AQUI / 'Notaria.postman_environment.json').write_text(
        json.dumps(a_entorno('Notaría · civica-desarrollo', VARIABLES, set()), indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (AQUI / 'PRUEBAS-CURL.md').write_text(curl_md(), encoding='utf-8')
    print('\nGenerado en', AQUI, f'({sum(len(c.pruebas) for c in CARPETAS)} pruebas)')
    return 1 if mal else 0


if __name__ == '__main__':
    sys.exit(main())
