"""Todas las pruebas del ecosistema, una carpeta por proyecto. Es la única fuente: de aquí salen la colección de
Postman, la guía de Postman, la guía de terminal (curl) y la ejecución real (--verificar)."""
from marco import R, Carpeta

# --------------------------------------------------------------------------------------- piezas comunes

def metadatos_emisor(prefijo: str, ruta_meta: str, cred: str) -> list[R]:
    return [
        R('Metadatos del emisor (OpenID4VCI)', 'GET', ruta_meta,
          f'Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`{cred}`), su formato y sus datos. '
          'Si esto falla, la wallet no puede ni empezar a recibir credenciales.',
          tiene=['credential_issuer', 'credential_configurations_supported']),
        R('Nonce del emisor', 'POST', f'/{prefijo}/nonce',
          'La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.',
          tiene=['c_nonce']),
        R('Lista de estado de las credenciales (revocación)', 'GET', f'/{prefijo}/estado/1',
          'Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. '
          'La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).',
          texto='eyJ'),
    ]


def pedir_verificacion(prefijo: str, cuerpo: dict, desc: str, nombre: str = 'Crear solicitud de verificación') -> R:
    return R(nombre, 'POST', f'/{prefijo}/api/solicitud', desc, cuerpo=cuerpo, tiene=['id', 'enlace'],
             guarda={'solicitud_id': 'id'})


def consultar(prefijo: str) -> R:
    return R('Consultar el resultado de la solicitud', 'GET', f'/{prefijo}/api/solicitud/{{{{solicitud_id}}}}',
             'El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta '
             'dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.',
             manual='abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. '
                    'Sin ese paso, la respuesta es "esperando".',
             tiene=['estado'])


def salud(prefijo: str) -> R:
    return R('Salud del servicio', 'GET', f'/{prefijo}/salud', 'Comprobación rápida de que el portal está en pie.', igual={'estado': 'ok'})


def pagina(prefijo: str, nombre: str = 'Portal (página principal)') -> R:
    return R(nombre, 'GET', f'/{prefijo}/', 'La página que ve la persona en el navegador.', texto='<')


# -------------------------------------------------------------------------------------------- carpetas

HUMO = Carpeta('00 · Humo: ¿está todo en pie?',
    'Una pasada rápida por todo el ecosistema. Si esta carpeta sale en verde, los servicios están arriba y se pueden hacer las demás pruebas. '
    'Úsala con el *Runner* de Postman: se ejecuta sola y no necesita claves.',
    [R(f'{n} · salud', 'GET', f'/{n}/salud', f'Responde `ok` si el portal de {n} está en pie.', igual={'estado': 'ok'})
     for n in ['banco', 'contratacion', 'discoteca', 'beca', 'acceso', 'pensiones', 'migracion', 'eps', 'notaria', 'gym', 'aerolinea', 'cancilleria']] +
    [R('Wallet Provider · metadatos', 'GET', '/wallet-provider/.well-known/oauth-protected-resource', 'El servicio que certifica cada instalación de la wallet.', tiene=['resource']),
     R('Registraduría (PID) · metadatos', 'GET', '/pid-registraduria/.well-known/openid-credential-issuer', 'El emisor del documento de identidad.', cabeceras={'Accept': 'application/json'}, tiene=['credential_issuer']),
     R('UIS · metadatos', 'GET', '/uis-issuer/.well-known/openid-credential-issuer', 'El emisor de credenciales académicas.', cabeceras={'Accept': 'application/json'}, tiene=['credential_issuer']),
     R('Lista de confianza de emisores PID', 'GET', '/tl-publisher/TL/PID/current.json', 'Lista firmada (ETSI TS 119 612) de quiénes pueden emitir identidad.', texto='eyJ'),
     R('Validador de confianza · salud', 'GET', '/trust-validator/actuator/health', 'El servicio que decide si una cadena de certificados es de confianza.', igual={'status': 'UP'}),
     R('Andes SCD (firma remota) · salud', 'GET', '/andes-scd/health', 'El prestador de firma simulado.', igual={'ok': True}),
     R('Onboarding · salud', 'GET', '/onboarding/actuator/health', 'Verificación de identidad previa a la emisión.', igual={'status': 'UP'}),
     R('Panel de control · ficha del APK', 'GET', '/descargas/apk.json', 'Última versión de la wallet disponible para descargar.', tiene=['archivo', 'sha256'])])

PANEL = Carpeta('Panel de control',
    'Panel de operación (`/`): sube, baja y reinicia los stacks. Su API exige el token `CONTROL_PANEL_TOKEN` (cabecera `Authorization: Bearer …`). '
    'Las rutas `/descargas/*` son públicas (las abre el teléfono).',
    [R('Estado de todos los proyectos', 'GET', '/api/projects', 'Lista cada stack con su estado real (activo, parcial, detenido), contenedores y enlace. Es lo que pinta el panel.',
       cabeceras={'Authorization': 'Bearer {{panel_token}}'}, privada=True),
     R('Sin token: debe rechazar', 'GET', '/api/projects', 'Prueba de seguridad: sin el token, la API responde 401.', espera=401),
     R('Recursos del ecosistema', 'GET', '/api/recursos', 'Contenedores, redes y volúmenes con puertos y enlaces (la vista `/recursos`).',
       cabeceras={'Authorization': 'Bearer {{panel_token}}'}, tiene=['contenedores'], privada=True),
     R('Reiniciar un proyecto', 'POST', '/api/projects/verifier-discoteca/action', 'Sube (`up`), baja (`down`) o reinicia (`restart`) un stack. '
       'Cambia el estado real del sistema: úsala a propósito.', cuerpo={'action': 'restart'}, cabeceras={'Authorization': 'Bearer {{panel_token}}'}, privada=True,
       salta_verificacion='reinicia un servicio real'),
     R('Ficha del APK de la wallet', 'GET', '/descargas/apk.json', 'Archivo, tamaño, fecha, SHA-256 y enlace de la última versión compilada de la wallet.', tiene=['archivo', 'tamano', 'sha256', 'url']),
     R('QR de descarga de la wallet', 'GET', '/descargas/qr.svg', 'El código QR (SVG) que apunta al APK.', texto='<svg'),
     R('Descarga del APK (primeros 4 bytes)', 'GET', '/descargas/wallet.apk', 'Pide solo los primeros bytes (`Range`) para probar la descarga sin bajar 400 MB. Debe responder 206.',
       cabeceras={'Range': 'bytes=0-3'}, espera=206)])

TL_PUBLISHER = Carpeta('Confianza · tl-publisher (listas de confianza)',
    'Registro de actores y publicación de las listas de confianza firmadas (ETSI TS 119 612): WP (wallet providers), PID, QEAA, PUBEAA y RPREG, más la LOTL (lista de listas). '
    'Cada lista sale en XML y en `tl+jwt`.',
    [R('Listas publicadas', 'GET', '/tl-publisher/tl-api/v1/lists', 'Todas las listas con su secuencia, fecha y próxima actualización.'),
     R('Lista de emisores PID (JWT)', 'GET', '/TL/PID/current.json', 'La lista que consume la wallet, firmada (`tl+jwt`).', texto='eyJ'),
     R('Lista de emisores PubEAA (XML)', 'GET', '/TL/PUBEAA/current.xml', 'La misma lista en el formato oficial XML.', texto='<'),
     R('Lista de listas (LOTL)', 'GET', '/LOTL/current.json', 'Apunta a todas las demás listas.', texto='eyJ'),
     R('Anclas de confianza de una lista', 'GET', '/tl-publisher/tl-api/v1/lists/PID/trust-anchors', 'Los certificados raíz en los que confía esa lista.'),
     R('Publicar sin clave: debe rechazar', 'POST', '/tl-publisher/tl-api/v1/publish/PID', 'Publicar es una operación privilegiada: sin `X-API-Key` responde 401.', espera=401),
     R('Publicar una lista (con clave)', 'POST', '/tl-publisher/tl-api/v1/publish/PID', 'Vuelve a firmar y publicar la lista PID con una nueva secuencia.',
       cabeceras={'X-API-Key': '{{tl_api_key}}'}, privada=True, salta_verificacion='cambia las listas reales'),
     R('Política (CPS) de la PKI', 'GET', '/pki/cps', 'Declaración de prácticas de la PKI del ecosistema.')])

VALIDADOR = Carpeta('Confianza · trust-validator',
    'Servicio oficial de EUDI que responde «¿esta cadena de certificados es de confianza para este rol?» leyendo las listas. '
    'Estas pruebas usan un certificado real: el del verifier del banco.',
    [R('Salud', 'GET', '/trust-validator/actuator/health', 'Estado del servicio.', igual={'status': 'UP'}),
     R('1 · Obtener la cadena de un verifier', 'GET', '/verifier-banco/wallet/public-keys.json', 'Descarga la clave pública del verifier del banco, que trae su cadena de certificados (`x5c`). Se guarda para la prueba siguiente.',
       tiene=['keys'], guarda={'x5c_chain': 'keys.0.x5c'}),
     R('2 · Validar la cadena con el rol correcto', 'POST', '/trust-validator/trust', 'La cadena del verifier, validada como *certificado de acceso de parte usuaria*: es el rol que tiene. Responde `trusted: true` y el ancla.',
       cuerpo='{"chain": {{x5c_chain}}, "verificationContext": "WalletRelyingPartyAccessCertificate"}', igual={'trusted': True}),
     R('3 · Una cadena que no es un certificado: debe rechazar', 'POST', '/trust-validator/trust', 'Si lo que se envía no es un certificado X.509 válido, el validador no lo puede leer y responde 400.',
       cuerpo={'chain': ['AAAA'], 'verificationContext': 'PID'}, espera=400),
     R('Cuerpo vacío: debe rechazar', 'POST', '/trust-validator/trust', 'Sin `chain` ni `verificationContext` la petición no se puede leer.', cuerpo={}, espera=400)])

WALLET_PROVIDER = Carpeta('Wallet · wallet-provider',
    'Servicio (Spartans) que certifica que cada instalación de la wallet y su clave son legítimas: emite la Wallet Instance Attestation y la Key Attestation, '
    'con una lista de estado para poder revocarlas. Valida el paquete Android de la app.',
    [R('Metadatos (RFC 9728)', 'GET', '/wallet-provider/.well-known/oauth-protected-resource', 'Quién es el servicio y dónde están sus claves.', tiene=['resource', 'jwks_uri']),
     R('Claves públicas (JWKS)', 'GET', '/wallet-provider/jwks', 'Con estas claves se comprueban las atestaciones que firma.', tiene=['keys']),
     R('Pedir un desafío (challenge)', 'POST', '/wallet-provider/challenge', 'Primer paso de la atestación: el servicio entrega un valor aleatorio que la wallet debe incluir en su prueba de integridad.', tiene=['challenge']),
     R('Atestación sin datos válidos: debe rechazar', 'POST', '/wallet-provider/wallet-instance-attestation/jwk', 'Sin una prueba de integridad real del dispositivo, no emite nada. '
       'Solo una app Android genuina puede completarla.', cuerpo={}, espera=[400, 415, 422])])

LISTAS_ESTADO = Carpeta('Revocación · listas de estado (status lists)',
    'Servicios que publican, firmadas, las listas de estado de las credenciales (draft-ietf-oauth-status-list): un bit por credencial, 1 = revocada. '
    'Hay una por servicio (`reg`, `uis`, `spartans`) y una propia por cada entidad simulada (`/<entidad>/estado/1`).',
    [R('Lista de estado de las atestaciones de wallet', 'GET', '{{status_list_ruta}}', 'Una lista real del Wallet Provider (la atestación de cada wallet apunta a una posición de aquí). '
       'La URL exacta la trae la credencial; en la guía está cómo sacarla de la base de datos.', cabeceras={'Accept': 'application/statuslist+jwt'}, texto='eyJ'),
     R('Lista de estado de la EPS', 'GET', '/eps/estado/1', 'Lista propia de la EPS (carnés y resúmenes de historia).', texto='eyJ'),
     R('Lista de estado de la notaría', 'GET', '/notaria/estado/1', 'Lista propia de la notaría.', texto='eyJ'),
     R('Lista de estado del gimnasio', 'GET', '/gym/estado/1', 'Lista propia del gimnasio (tarjetas de socio).', texto='eyJ'),
     R('Lista de estado de la aerolínea', 'GET', '/aerolinea/estado/1', 'Lista propia de la aerolínea (tiquetes).', texto='eyJ'),
     R('Lista de estado de la cancillería', 'GET', '/cancilleria/estado/1', 'Lista propia de la cancillería (pasaportes).', texto='eyJ')])

REGISTRADURIA = Carpeta('Emisor · Registraduría (PID)',
    'Emisor oficial del documento de identidad digital (PID, `urn:eudi:pid:1` en SD-JWT y `eu.europa.ec.eudi.pid.1` en mdoc). Los ciudadanos de prueba están en su base y se autentica con Keycloak. '
    'Puerto de su base de datos: 5441.',
    [R('Metadatos OpenID4VCI', 'GET', '/pid-registraduria/.well-known/openid-credential-issuer', 'Credenciales que ofrece, formatos y datos de cada una. Pide `Accept: application/json` (sin él puede responder firmado).',
       cabeceras={'Accept': 'application/json'}, tiene=['credential_issuer', 'credential_configurations_supported']),
     R('Metadatos JWT-VC', 'GET', '/pid-registraduria/.well-known/jwt-vc-issuer', 'Cómo verificar las credenciales SD-JWT que firma.', tiene=['issuer']),
     R('Claves públicas del emisor', 'GET', '/pid-registraduria/public_keys.jwks', 'JWKS con la clave de firma.', tiene=['keys']),
     R('Metadatos del tipo de la PID', 'GET', '/pid-registraduria/type-metadata/urn:eudi:pid:1', 'Descripción y datos del tipo `urn:eudi:pid:1`.'),
     R('Pantalla ciudadana: solicitar la PID', 'GET', '/pid-registraduria/registraduria/solicitar', 'La página donde el ciudadano pide su PID digital (sin login; se identifica con el onboarding).', texto='<'),
     R('Login del panel de administración', 'GET', '/pid-registraduria/registraduria/admin/login', 'Formulario de acceso del administrador de la Registraduría (la clave está en `DATOS-LOCALES.md`).', texto='<'),
     R('Crear una oferta sin indicar qué credencial: debe rechazar', 'POST', '/pid-registraduria/issuer/credentialsOffer/create', 'La API para crear ofertas exige saber qué credencial se ofrece.',
       cuerpo={}, espera=400, igual={'error': 'MissingCredentialConfigurationIds'}),
     R('Token de administrador de Keycloak', 'POST', '/idp-reg/realms/master/protocol/openid-connect/token', 'Pide un token para operar el Keycloak de la Registraduría (sus claves están en `DATOS-LOCALES.md`).',
       form={'grant_type': 'password', 'client_id': 'admin-cli', 'username': '{{kc_admin_user}}', 'password': '{{kc_admin_password}}'}, tiene=['access_token'],
       guarda={'kc_token': 'access_token'}, privada=True, salta_verificacion='usa credenciales de administrador'),
     R('Configuración OIDC del realm', 'GET', '/idp-reg/realms/pid-registraduria-realm/.well-known/openid-configuration', 'Endpoints de autorización y token del realm donde viven los ciudadanos.', tiene=['issuer', 'token_endpoint'])])

UIS = Carpeta('Emisor · UIS (credenciales académicas)',
    'Universidad Industrial de Santander: emite credencial académica (`urn:eudi:academic:credential:1`), de aprendizaje (`urn:eu.europa.ec.eudi:learning:credential:1`) y **carnet estudiantil** '
    '(`urn:co:uis:carnet-estudiantil:1`). Sus datos académicos viven en Postgres (puerto 5433) y su registro de credenciales en el 5434.',
    [R('Metadatos OpenID4VCI', 'GET', '/uis-issuer/.well-known/openid-credential-issuer', 'Credenciales que ofrece la UIS: académica, aprendizaje, carnet, PID y mDL. Pide `Accept: application/json`.',
       cabeceras={'Accept': 'application/json'}, tiene=['credential_issuer', 'credential_configurations_supported']),
     R('Metadatos JWT-VC', 'GET', '/uis-issuer/.well-known/jwt-vc-issuer', 'Cómo verificar las credenciales que firma.', tiene=['issuer']),
     R('Claves públicas del emisor', 'GET', '/uis-issuer/public_keys.jwks', 'JWKS con la clave de firma.', tiene=['keys']),
     R('Metadatos del tipo «aprendizaje»', 'GET', '/uis-issuer/type-metadata/urn:eu.europa.ec.eudi:learning:credential:1', 'Descripción del tipo de credencial de aprendizaje.'),
     R('Metadatos del tipo académico (no existe)', 'GET', '/uis-issuer/type-metadata/urn:eudi:academic:credential:1', 'Este tipo se anuncia en los metadatos pero no publica su descripción: 404. Se deja como caso conocido.', espera=404),
     R('Portal del emisor (login del estudiante)', 'GET', '/uis-issuer/issuer/login', 'Donde el estudiante entra con su usuario y clave para generar el QR de su carnet. Los estudiantes de prueba están en `DATOS-LOCALES.md`.', texto='<'),
     R('Configuración OIDC del realm de la UIS', 'GET', '/idp-uis/realms/uis-issuer-realm/.well-known/openid-configuration', 'Endpoints de autorización y token del Keycloak de la UIS. Si ese Keycloak está apagado (el panel apaga uno de los dos con «Activar consola», porque comparten sesión), responde 503: es un estado válido, no un fallo.', espera=[200, 503])])

ONBOARDING = Carpeta('Wallet · onboarding (verificación de identidad)',
    'Identity proofing previo a pedir la PID: crea una sesión, verifica el contacto con un código (OTP), pide consentimiento, captura el documento y la selfie, y decide. '
    'Base de datos Postgres en el puerto 5436. En desarrollo el código OTP viene en la respuesta (`devCode`).',
    [R('Salud del backend', 'GET', '/onboarding/actuator/health', 'Estado del servicio.', igual={'status': 'UP'}),
     R('Salud de biometría y OCR', 'GET', '/biometrics/health', 'Servicio que compara el rostro y lee el documento.'),
     R('1 · Crear una sesión', 'POST', '/onboarding/api/v1/onboarding/sessions', 'Abre una sesión de onboarding (dura 2 horas). Devuelve su `id`.', espera=201, tiene=['id', 'status'], guarda={'session_id': 'id'}),
     R('2 · Ver el estado de la sesión', 'GET', '/onboarding/api/v1/onboarding/sessions/{{session_id}}', 'Estado, decisión y datos capturados hasta ahora.', tiene=['status', 'contactVerified']),
     R('3 · Enviar el código al contacto', 'POST', '/onboarding/api/v1/onboarding/sessions/{{session_id}}/contact', 'Envía un código al correo o teléfono. En desarrollo no sale a ningún lado: llega en `devCode`.',
       cuerpo={'channel': 'EMAIL', 'email': 'prueba@example.com'}, tiene=['status', 'devCode'], guarda={'dev_code': 'devCode'}),
     R('4 · Verificar el código', 'POST', '/onboarding/api/v1/onboarding/sessions/{{session_id}}/contact/verify', 'Confirma que la persona controla ese contacto.', cuerpo={'code': '{{dev_code}}'}, igual={'contactVerified': True}),
     R('5 · Aceptar el consentimiento', 'POST', '/onboarding/api/v1/onboarding/sessions/{{session_id}}/consent', 'La persona acepta el tratamiento de sus datos.', cuerpo={'accepted': True, 'policyVersion': 'v1'}, igual={'consentAccepted': True}),
     R('6 · Auditoría de la sesión', 'GET', '/onboarding/api/v1/onboarding/sessions/{{session_id}}/audit', 'Cada paso queda registrado con hora.')])

ANDES = Carpeta('Confianza · Andes SCD (firma remota)',
    'Prestador de servicios de firma remota **simulado** (CSC v2.2) y autoridad de sellado de tiempo. La wallet firma contratos con él; el portal de contratación sella las firmas. '
    'No es un prestador cualificado: es de prueba.',
    [R('Salud', 'GET', '/andes-scd/health', 'Estado del servicio.', igual={'ok': True}),
     R('Información del prestador (CSC)', 'GET', '/andes-scd/csc/v2/info', 'Qué métodos de autenticación y operaciones soporta.', tiene=['name', 'authType']),
     R('Metadatos OAuth', 'GET', '/andes-scd/.well-known/oauth-authorization-server', 'Endpoints de autorización, token y PAR.', tiene=['issuer', 'token_endpoint']),
     R('Token de cliente (client credentials)', 'POST', '/andes-scd/oauth2/token', 'Token del cliente de la wallet (`wallet-client`). Sirve para operaciones del servicio, pero **no identifica a un firmante**.',
       form={'grant_type': 'client_credentials', 'client_id': '{{andes_client_id}}', 'client_secret': '{{andes_client_secret}}'}, tiene=['access_token'], guarda={'andes_token': 'access_token'}, privada=True),
     R('Listar credenciales de firma sin firmante: debe rechazar', 'POST', '/andes-scd/csc/v2/credentials/list', 'Con el token de cliente no hay firmante: responde 403. Para listar las credenciales de firma de una persona hay que hacer el flujo de autorización con su identidad (lo hace la wallet).',
       cabeceras={'Authorization': 'Bearer {{andes_token}}'}, cuerpo={}, espera=403, privada=True),
     R('Sello de tiempo (RFC 3161)', 'POST', '/andes-scd/tsa', 'Recibe una consulta de sello (`consulta.tsq`) y devuelve el sello firmado. Se crea con: `openssl ts -query -data archivo.txt -sha256 -cert -no_nonce -out consulta.tsq`.',
       binario='consulta.tsq', cabeceras={'Content-Type': 'application/timestamp-query'}, salta_verificacion='necesita un archivo de consulta')])

VERIFIER = Carpeta('Verificación · verifier OpenID4VP (una instancia por entidad)',
    'Cada entidad tiene su propia instancia del verifier oficial de EUDI (`/verifier-<entidad>`). Estas pruebas usan la del banco (`{{instancia}}` = `banco`; cámbiala por `eps`, `gym`, `notaria`…). '
    'Es la API que usan los portales para pedir datos a la wallet con OpenID4VP + DCQL.',
    [R('Usos previstos (intended uses)', 'GET', '/verifier-{{instancia}}/ui/intended-uses', 'Para qué pide datos esta instancia (lo que la wallet muestra al usuario) y su certificado de registro.', tiene=['intended_uses'], guarda={'intended_use_id': 'intended_uses.0.intended_use_id'}),
     R('Claves públicas del verifier', 'GET', '/verifier-{{instancia}}/wallet/public-keys.json', 'Su certificado de acceso (cadena x5c).', tiene=['keys']),
     R('Crear una presentación (DCQL)', 'POST', '/verifier-{{instancia}}/ui/presentations/v2', 'Pide el apellido y el nombre de la PID. Devuelve el enlace `haip-vp://…` que la wallet abre.',
       cuerpo={'dcql_query': {'credentials': [{'id': 'pid', 'format': 'dc+sd-jwt', 'meta': {'vct_values': ['urn:eudi:pid:1']}, 'claims': [{'path': ['family_name']}, {'path': ['given_name']}]}]},
               'nonce': 'prueba-1234567890', 'profile': 'haip', 'intended_use_id': '{{intended_use_id}}', 'request_uri_method': 'get'},
       tiene=['transaction_id', 'authorization_request_uri'], guarda={'transaction_id': 'transaction_id'}),
     R('Resultado antes de que la wallet responda', 'GET', '/verifier-{{instancia}}/ui/presentations/{{transaction_id}}', 'Mientras la wallet no presente nada, responde 400 `PresentationNotSubmitted`.',
       manual='abre el enlace de la prueba anterior con la wallet y acepta; entonces esta misma petición devuelve el `vp_token` con los datos.', espera=400, igual={'error': 'PresentationNotSubmitted'}),
     R('Eventos de la transacción', 'GET', '/verifier-{{instancia}}/ui/presentations/{{transaction_id}}/events', 'Cada paso de la transacción (inicializada, solicitud leída por la wallet, respuesta, fallo…). Sirve para depurar.', tiene=['events']),
     R('Crear una presentación sin uso previsto: debe rechazar', 'POST', '/verifier-{{instancia}}/ui/presentations/v2', 'Sin `intended_use_id` el verifier no puede adjuntar el certificado de registro.',
       cuerpo={'dcql_query': {'credentials': [{'id': 'pid', 'format': 'dc+sd-jwt', 'meta': {'vct_values': ['urn:eudi:pid:1']}, 'claims': [{'path': ['family_name']}]}]}, 'nonce': 'prueba-1234567890', 'profile': 'haip', 'request_uri_method': 'get'},
       espera=400, igual={'error': 'MissingRegistrationCertificate'})])

# ---------------------------------------------------------------------------------- entidades simuladas

BANCO = Carpeta('Verificación · Banco Cívica',
    'Banco sin contraseñas: se entra y se abre la cuenta presentando el PID desde la wallet (nombre y documento). Solo verifica; no emite.',
    [salud('banco'), pagina('banco'),
     pedir_verificacion('banco', {'tipo': 'ingreso'}, 'Pide a la wallet los datos para **entrar** a la banca. Devuelve `id`, el enlace `haip-vp://…` y el QR (data URL).'),
     consultar('banco'),
     pedir_verificacion('banco', {'tipo': 'registro'}, 'Pide los datos para **abrir una cuenta** (más datos que el ingreso).', 'Crear solicitud de apertura de cuenta'),
     R('Tipo no válido: debe rechazar', 'POST', '/banco/api/solicitud', 'Solo existen `ingreso` y `registro`.', cuerpo={'tipo': 'otro'}, espera=400),
     R('¿Quién soy? (sesión)', 'GET', '/banco/api/yo', 'Datos de la persona ya identificada en esta sesión del navegador; sin identificarse, responde 401.', espera=[200, 401]),
     R('Cerrar sesión', 'POST', '/banco/api/salir', 'Borra la sesión del navegador.', espera=[200, 204])])

CONTRATACION = Carpeta('Verificación · Contratación (empleador)',
    'Portal de empleo: verifica el **nivel de formación** con la credencial académica de la UIS, la identidad con el PID (opcional), genera el contrato en PDF y lo hace firmar desde la wallet '
    '(Firmar → Escanear), con sello de tiempo de Andes SCD.',
    [salud('contratacion'), pagina('contratacion'),
     R('Niveles de formación', 'GET', '/contratacion/api/niveles', 'Los niveles que se pueden verificar (doctorado, maestría, … bachiller).', tiene=['niveles']),
     pedir_verificacion('contratacion', {'nivel': 'PREGRADO', 'paso': 'formacion'}, 'Pide la credencial académica de nivel pregrado; la wallet solo ofrece las credenciales que cumplen el filtro.'),
     consultar('contratacion'),
     R('Nivel no disponible: debe rechazar', 'POST', '/contratacion/api/solicitud', 'Un nivel que no existe se rechaza.', cuerpo={'nivel': 'NIVEL-INVENTADO', 'paso': 'formacion'}, espera=400),
     R('Generar el contrato sin verificar antes: debe rechazar', 'POST', '/contratacion/api/contrato', 'No se puede generar el contrato si la formación y la identidad no están verificadas.',
       cuerpo={'previa': 'inexistente', 'cargo': 'Analista de datos'}, espera=400),
     R('Estado de la firma (id desconocido)', 'GET', '/contratacion/api/firma/inexistente', 'Consulta el estado de una firma de contrato; con un id que no existe, 404.', espera=404)])

DISCOTECA = Carpeta('Verificación · Discoteca Neón (mayoría de edad)',
    'Control de edad en la puerta: pide a la wallet solo «mayor de 18» (sin nombre ni documento) y **no guarda nada**, solo contadores.',
    [salud('discoteca'), pagina('discoteca'),
     pedir_verificacion('discoteca', {}, 'Pide únicamente la prueba de mayoría de edad del PID.'),
     consultar('discoteca'),
     R('Contadores de la noche', 'GET', '/discoteca/api/noche', 'Cuántas entradas y rechazos van, sin ningún dato personal.', tiene=['entradas', 'rechazos'])])

BECA = Carpeta('Verificación · Instituto Meridiano (inscripción y becas)',
    'Pide en una sola presentación el PID y la credencial académica, y evalúa si la persona cumple los requisitos de cada convocatoria.',
    [salud('beca'), pagina('beca'),
     R('Convocatorias', 'GET', '/beca/api/convocatorias', 'Inscripciones y becas disponibles con sus requisitos (nivel, estado, promedio mínimo…).', tiene=['convocatorias']),
     pedir_verificacion('beca', {'convocatoria': 'inscripcion-maestria', 'paso': 'preevaluacion'}, 'Pide el PID y la credencial académica para pre-evaluar la inscripción a maestría.'),
     consultar('beca')])

ACCESO = Carpeta('Verificación · Edificio Cumbre (control de acceso)',
    'Pide la foto, el nombre y el documento del PID para que un guardia decida el ingreso, con estilo de centro de control.',
    [salud('acceso'), pagina('acceso'),
     pedir_verificacion('acceso', {}, 'Pide foto, nombre y documento del PID.'),
     consultar('acceso'),
     R('Decisión del guardia sobre una solicitud inexistente', 'POST', '/acceso/api/decision', 'El guardia permite o niega; con un id que no existe se rechaza.', cuerpo={'id': 'inexistente', 'decision': 'permitir'}, espera=[400, 404, 409]),
     R('Registro de decisiones', 'GET', '/acceso/api/registro', 'Quién entró y quién no (datos mínimos).', tiene=['registro'])])

PENSIONES = Carpeta('Verificación · Fondo de Pensiones Horizonte (prueba de supervivencia)',
    'La persona prueba que está viva presentando el PID y su **fe de vida notarial**; el fondo cruza las dos.',
    [salud('pensiones'), pagina('pensiones'),
     pedir_verificacion('pensiones', {}, 'Pide el PID y la fe de vida de la notaría.'),
     consultar('pensiones'),
     R('Personas acreditadas', 'GET', '/pensiones/api/acreditados', 'Quién ya probó supervivencia.', tiene=['acreditados'])])

MIGRACION = Carpeta('Verificación · Migración Cívica (control migratorio)',
    'Pide el PID y el pasaporte digital (Cancillería), cruza las comprobaciones y el funcionario decide la entrada o la salida.',
    [salud('migracion'), pagina('migracion'),
     R('Movimiento no indicado: debe rechazar', 'POST', '/migracion/api/solicitud', 'Hay que decir si es `entrada` o `salida`.', cuerpo={}, espera=400, igual={'error': 'movimiento: entrada o salida'}),
     pedir_verificacion('migracion', {'movimiento': 'entrada'}, 'Pide PID + pasaporte para una **entrada** al país.'),
     consultar('migracion'),
     R('Decisión sobre una solicitud inexistente', 'POST', '/migracion/api/decision', 'El funcionario autoriza o niega; con un id que no existe se rechaza.', cuerpo={'id': 'inexistente', 'decision': 'permitir'}, espera=[400, 404, 409]),
     R('Registro de movimientos', 'GET', '/migracion/api/registro', 'Entradas y salidas decididas.', tiene=['registro'])])

EPS = Carpeta('Emisor+Verifier · EPS Salud Cívica (carné e historia clínica)',
    'Emite el **carné de afiliado** y el **resumen de historia clínica** (QEAA, SD-JWT), cada dato divulgable por separado; verifica a farmacias y médicos solo lo necesario. '
    'Tiene portal de afiliados, de médicos y de administración (`ADMIN_CLAVE`).',
    [salud('eps'), pagina('eps'),
     pedir_verificacion('eps', {'proposito': 'identificacion'}, 'El afiliado se identifica con su PID para entrar a su portal.'),
     consultar('eps'),
     R('Propósito no válido: debe rechazar', 'POST', '/eps/api/solicitud', 'Los propósitos válidos son identificacion, medico, carne, historia o convivencia.', cuerpo={}, espera=400),
     R('Administración · entrar', 'POST', '/eps/api/admin/entrar', 'Entra a la administración con la clave del administrador.', cuerpo={'clave': '{{eps_clave}}'}, privada=True, espera=[200, 204]),
     R('Administración · médicos registrados', 'GET', '/eps/api/admin/medicos', 'Los médicos autorizados a consultar historias (requiere haber entrado).', privada=True, espera=[200, 401]),
     R('Administración · salir', 'POST', '/eps/api/admin/salir', 'Cierra la sesión de administración.', espera=[200, 204]),
     *metadatos_emisor('eps', '/.well-known/openid-credential-issuer/eps', 'co.eps.carne_afiliado')])

NOTARIA = Carpeta('Emisor+Verifier · Notaría Avance (9 documentos notariales)',
    'Verifica el PID y emite nueve documentos (PuB-EAA): declaración extraproceso, poder especial, reconocimiento de documento, **fe de vida**, autenticación de firma, copia de escritura, registro civil, convivencia y permiso de salida de menor.',
    [salud('notaria'), pagina('notaria'),
     pedir_verificacion('notaria', {'proposito': 'identificacion'}, 'La persona se identifica con su PID para hacer un trámite.'),
     consultar('notaria'),
     R('Propósito no válido: debe rechazar', 'POST', '/notaria/api/solicitud', 'Hay que decir `identificacion` o `verificacion` con un tipo de documento.', cuerpo={}, espera=400),
     R('Trámites (sin identificarse)', 'GET', '/notaria/api/tramites', 'Los trámites de la persona; antes de identificarse con la wallet responde 401.', espera=401, igual={'error': 'Primero identifícate con tu wallet.'}),
     *metadatos_emisor('notaria', '/.well-known/openid-credential-issuer/notaria', 'co.notaria.fe_de_vida')])

GYM = Carpeta('Emisor+Verifier · Cumbre Gym (tarjeta de socio, ingreso y egreso)',
    'Emite la **tarjeta de socio** (QEAA, con foto) y controla el ingreso y egreso de sus sedes (anti-passback y aforo). Las pantallas de la puerta piden la clave de puerta (= `ADMIN_CLAVE`).',
    [salud('gym'), pagina('gym'),
     pedir_verificacion('gym', {'proposito': 'identificacion'}, 'La persona se identifica con su PID para afiliarse o ver su portal.'),
     consultar('gym'),
     R('Propósito no válido: debe rechazar', 'POST', '/gym/api/solicitud', 'Hay que decir `identificacion` o `acceso`.', cuerpo={}, espera=400),
     R('Puerta · clave incorrecta: debe rechazar', 'POST', '/gym/api/puerta/entrar', 'Prueba de seguridad: una clave equivocada no abre la pantalla de la puerta.', cuerpo={'clave': 'clave-incorrecta'}, espera=[401, 403, 429]),
     R('Puerta · entrar con la clave', 'POST', '/gym/api/puerta/entrar', 'Habilita esta sesión para las pantallas de entrada y salida.', cuerpo={'clave': '{{gym_clave}}'}, privada=True, espera=[200, 204]),
     R('Puerta · aforo en vivo', 'GET', '/gym/api/puerta/aforo', 'Cuánta gente hay dentro en cada sede.', privada=True, espera=[200, 401]),
     R('Administración · resumen', 'GET', '/gym/api/admin/resumen', 'Aforo, socios y últimos accesos (requiere entrar como administrador).', privada=True, espera=[200, 401]),
     *metadatos_emisor('gym', '/.well-known/openid-credential-issuer/gym', 'co.gym.tarjeta_socio')])

AEROLINEA = Carpeta('Emisor+Verifier · Páramo Air (tiquetes)',
    'Vende pasajes: verifica nombre, documento, nacimiento y foto del PID, **emite el tiquete** (QEAA) y lo verifica en el embarque.',
    [salud('aerolinea'), pagina('aerolinea'),
     R('Destinos', 'GET', '/aerolinea/api/destinos', 'Ciudades a las que vuela.', tiene=['ciudades']),
     R('Vuelos disponibles', 'GET', '/aerolinea/api/vuelos?origen=BOG&destino=MDE&fecha={{fecha_vuelo}}', 'Vuelos de Bogotá a Medellín en la fecha indicada.', espera=200),
     R('Mismo origen y destino: debe rechazar', 'GET', '/aerolinea/api/vuelos?origen=BOG&destino=BOG&fecha={{fecha_vuelo}}', 'Hay que elegir origen y destino distintos.', espera=400),
     pedir_verificacion('aerolinea', {'proposito': 'identificacion'}, 'La persona se identifica con su PID para comprar.'),
     consultar('aerolinea'),
     *metadatos_emisor('aerolinea', '/.well-known/openid-credential-issuer/aerolinea', 'co.aerolinea.tiquete')])

CANCILLERIA = Carpeta('Emisor+Verifier · Cancillería (pasaporte digital)',
    'Solicitud, entrega única, reexpedición y verificación del **pasaporte digital** (PuB-EAA). Un pasaporte ya entregado solo se vuelve a emitir con reexpedición.',
    [salud('cancilleria'), pagina('cancilleria'),
     pedir_verificacion('cancilleria', {'proposito': 'identificacion'}, 'La persona se identifica con su PID para solicitar el pasaporte.'),
     consultar('cancilleria'),
     R('Propósito no válido: debe rechazar', 'POST', '/cancilleria/api/solicitud', 'Hay que decir `identificacion` o `verificacion` con un tipo.', cuerpo={}, espera=400),
     R('Trámites (sin identificarse)', 'GET', '/cancilleria/api/tramites', 'Antes de identificarse con la wallet responde 401.', espera=401),
     *metadatos_emisor('cancilleria', '/.well-known/openid-credential-issuer/cancilleria', 'co.cancilleria.pasaporte')])

CARPETAS = [HUMO, PANEL, TL_PUBLISHER, VALIDADOR, ANDES, WALLET_PROVIDER, LISTAS_ESTADO, ONBOARDING, REGISTRADURIA, UIS, VERIFIER,
            BANCO, CONTRATACION, DISCOTECA, BECA, ACCESO, PENSIONES, MIGRACION, EPS, NOTARIA, GYM, AEROLINEA, CANCILLERIA]
