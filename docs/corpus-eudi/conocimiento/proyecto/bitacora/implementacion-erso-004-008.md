IMPLEMENTACION DE LAS ERSo 2026-004 A 2026-008 (VDR de extension, did:web)
Proyecto: Identidad Digital Soberana SSI
Fecha de la sesion: 28-sep-2026
Dominio: civica-desarrollo.avance.org.co
Ubicacion del proyecto: /home/geovani/Descargas/generic/bitacora/new_proyect

NOTA: este documento es una cronica resumida del hilo de trabajo con Claude Code
(peticiones, analisis, decisiones, pasos y resultados). No es una transcripcion literal.

======================================================================
1. PUNTO DE PARTIDA Y MALENTENDIDO INICIAL
======================================================================
- Se pidio aplicar la ERSo 2026-004 al proyecto eudi-app-android-wallet-ui.
- Se analizo el PDF de la ERSo 004 y el repositorio del wallet Android. El repo no tiene
  codigo de DID ni de VDR.
- Conclusion: un VDR con endpoints REST, respaldo y restauracion es un servicio de backend,
  no codigo de la app movil. La ERSo no exigia trabajar sobre ese repo.
- El usuario confirmo la confusion ("pense que se basaba en este proyecto") y pidio analizar
  donde convergen los temas en el ecosistema EUDI (eIDAS 2, ARF, etc.).

======================================================================
2. ANALISIS DE CONVERGENCIA (eIDAS 2 / ARF / DID / VDR)
======================================================================
- eIDAS 2 (Reglamento UE 2024/1183): capa legal. No menciona DIDs ni blockchain.
- ARF (Architecture and Reference Framework): capa tecnica. Su modelo de confianza base se
  apoya en certificados y Trusted Lists, con formatos mdoc y SD-JWT VC.
- W3C DID / VC: origen del concepto "Verifiable Data Registry" (rol abstracto: blockchain o
  servicio web).
- EBSI: la via europea que si uso DIDs (did:ebsi) y un registro de emisores de confianza.
- Lectura para las ERSo: el VDR/DID es una EXTENSION OPCIONAL sobre el camino base, por eso
  las ERSo la marcan con "O", la aislan y la dejan apagada por defecto.
- Los cinco temas: 004 VDR de extension, 005 did:web, 006 DID institucional,
  007 consumidor conforme, 008 ciclo de vida y trazabilidad.
- Decision: crear un proyecto NUEVO y separado del wallet, en Kotlin/Ktor.
- ADVERTENCIA registrada: el PDF interno "Identidad Digital EUDI Wallet" presenta DID como
  estandar base del ARF. La lectura de Claude es que es opcional; debe verificarse contra
  la version vigente del ARF antes de afirmarlo ante terceros.

======================================================================
3. DECISIONES DEL USUARIO
======================================================================
- Proyecto nuevo con Kotlin/Ktor.
- Usar todos los PDFs de la carpeta plan_tareas (ERSo 004 a 008).
- Carpeta de trabajo: bitacora/new_proyect.
- Dominio: civica-desarrollo.avance.org.co (el usuario lo recordo de nuevo a mitad de trabajo;
  ya estaba en uso en configuracion, DIDs, certificado TLS y alias de red de nginx).
- Usar Docker, PostgreSQL y lo necesario.
- Producir un informe por ERSo, con paso a paso, para aprender y exponer al grupo.
- Nota: no es posible cambiar la carpeta raiz de la sesion de Control Code; se trabajo con
  rutas absolutas.

======================================================================
4. ARQUITECTURA CONSTRUIDA
======================================================================
Modulos Gradle:
  did-core      Base58, Multikey (P-256), did:web, JSON canonico + hash, JWS ES256,
                validador de DID Documents, constructor de documentos.        (ERSo 005)
  did-resolver  Consumidor de SOLO LECTURA: resolver, dereferenciar, verificar pruebas.
                No depende del servicio de registro.                          (ERSo 007)
  vdr-service   Servicio Ktor + PostgreSQL: canal publico de lectura y canal de escritura
                (OAuth2 client-credentials + mTLS), desafios, versiones, auditoria,
                respaldo/restauracion.                                        (004, 006, 008)
  did-tools     CLI que actua como la "entidad" (keygen, build-doc, write, admin, resolve,
                sign, verify).

Despliegue (deploy/): docker compose con postgres, vdr, nginx y tools (perfil).
  - nginx: puerto 443 = lectura publica (solo GET/HEAD de */did.json y camino base);
           puerto 8443 = escritura con mTLS obligatorio.
  - Ni la aplicacion ni la base de datos publican puertos.
  - nginx sobrescribe siempre los encabezados de identidad del certificado cliente.
  - La extension esta APAGADA por defecto (VDR_ENABLED=false). Apagada, la aplicacion ni se
    conecta a la base de datos ni abre el canal de escritura.

Correspondencia DID -> URL:
  did:web:civica-desarrollo.avance.org.co:entidades:avance
  -> https://civica-desarrollo.avance.org.co/entidades/avance/did.json

Namespaces de laboratorio: entidades/avance, entidades/avance-ciclo, lab/laboratorio.

Mecanismos de seguridad y ciclo de vida:
  - Desafio de un solo uso, con tipo (CREATE/UPDATE/DEACTIVATE) y audiencia especificos.
  - Prueba de posesion: JWS ES256 sobre {aud, challenge, did, docHash, purpose}.
  - En UPDATE/DEACTIVATE firma la clave VIGENTE; en CREATE, la clave del propio documento.
  - Escritura con If-Match (version esperada) y Idempotency-Key.
  - Confirmacion leyendo la URL publica y comparando hash; si no hay respuesta en plazo la
    operacion queda PENDING y se reconcilia (CONFIRMED o SUPERSEDED).
  - Historial append-only (triggers en PostgreSQL) y auditoria; estado reconstruible por instante.
  - Estado terminal: un historial desactivado no vuelve a activarse sin instancia nueva.
  - Respaldo con checksum SHA-256 del JSON canonico; restauracion solo sobre registro vacio,
    con verificacion de integridad.

======================================================================
5. PROCESO DE IMPLEMENTACION (resumen cronologico)
======================================================================
1) Verificacion de entorno: Docker, Java 21, Gradle 9.4.1, acceso a Maven. Ktor 3.6.0, Kotlin 2.3.0.
2) Esqueleto Gradle + did-core. Prueba fallida real: al pasar el host a minusculas, %3A del
   puerto se volvia %3a. Corregido.
3) did-resolver (ERSo 007) con pruebas usando MockEngine.
4) vdr-service: configuracion, migracion SQL, almacen JDBC, servicios, rutas HTTP.
5) Pruebas de integracion contra PostgreSQL real, organizadas por ERSo y por criterio.
   Correcciones durante las pruebas:
   - JDBC reporta "bigserial" para la columna id de auditoria; se mapeo a int8 en la restauracion.
   - La auditoria BACKUP se escribia despues de la foto; ahora se escribe antes para que el
     respaldo la contenga.
   - Timestamps normalizados a ISO-8601 para la consulta "estado en el instante T".
6) HALLAZGO IMPORTANTE: dos pruebas de did-resolver (criterios 1 y 3 de la ERSo 007) no se
   ejecutaban. Un metodo de prueba cuya ultima expresion devuelve un valor no es void y JUnit lo
   ignora en silencio. Corregido, y se anadio una prueba guarda para evitar reincidencia.
7) did-tools (CLI) y despliegue Docker: certificados de laboratorio (CA propia, servidor,
   clientes por entidad), nginx con dos canales, Dockerfile y compose.
8) HALLAZGO: la CLI no presentaba su certificado cliente a nginx. El motor CIO de Ktor no lo
   envia con claves EC. Se cambio al motor Java (TLS nativo del JDK).
9) Recorrido extremo a extremo (scripts/e2e.sh) por TLS y mTLS reales:
   - Primera corrida con la CLI defectuosa: 37 FAIL (cascada del problema del certificado).
   - Tras el arreglo: 53 PASS / 2 FAIL. Los 2 eran errores de la aserciones (sin certificado
     cliente nginx responde 400, no corta el handshake). Corregidas.
   - Corrida final desde entorno limpio: 56 PASS / 0 FAIL.
10) Documentacion: README, guia de exposicion, versiones normativas, cinco informes, evidencias.

======================================================================
6. RESULTADOS
======================================================================
Pruebas automaticas: 53 pruebas, 0 fallos
  did-core 14 | did-resolver 12 | vdr-service 27
Recorrido E2E: 56 comprobaciones PASS, 0 FAIL (log: evidencias/e2e-20260928-173547.log)

Criterios de aceptacion por ERSo:
  ERSo 004  C1 registro/actualizacion/trazabilidad      OK
            C2 respaldo y restauracion verificados      OK
               (respaldo -> perdida total -> restauracion: mismos hash publicos; historial y
                estado desactivado sobreviven)
            C3 camino base con la opcion apagada        OK
  ERSo 005  C1 servido por HTTPS en la URL calculada    OK
            C2 clave publica correcta y hash igual      OK
            C3 sin claves privadas ni datos civiles     OK
            C4 id desajustado rechazado + regresion     OK
  ERSo 006  C1 servido por HTTPS y resoluble            OK
            C2 escritura autenticada, trazada y limitada al namespace  OK
            C3 sin claves privadas ni datos civiles     OK
            C4 evidencia (version, hash, URL) registrada OK
  ERSo 007  C1 prueba con la clave resuelta valida      OK
            C2 prueba con clave ajena rechazada         OK
            C3 documento no conforme/id desajustado/inexistente rechazado  OK
            C4 solo lectura (solo GET; interfaz sin escritura)             OK
  ERSo 008  C1 precondiciones: si falla, no hay desafio OK
            C2 desafio de un solo uso, tipo y audiencia OK
            C3 documento sin claves privadas, id/controladores correctos   OK
            C4 escritura con version esperada; devuelve version, hash, URL OK
            C5 confirmacion por lectura de URL y hash   OK
            C6 sin respuesta en plazo: PENDING          OK
            C7 la traza reconstruye el estado           OK

======================================================================
7. ENTREGABLES (en new_proyect)
======================================================================
  README.md                       Vision general, arquitectura, puesta en marcha, limites
  docs/GUIA-EXPOSICION.md         Glosario, recorrido de publicacion, amenazas, guion de demo, preguntas
  docs/VERSIONES-NORMATIVAS.md    Especificaciones de referencia (confirmar version y estado)
  informes/ERSo-2026-004.md ... ERSo-2026-008.md   Un informe por ERSo, con tabla de actividades
  evidencias/                     Log E2E final y pruebas-automaticas.md
  scripts/                        gen-dev-certs.sh, gen-env.sh, e2e.sh, summarize-tests.py
  deploy/                         docker-compose.yml, nginx, Dockerfile

Comandos utiles:
  ./gradlew :vdr-service:installDist :did-tools:installDist
  bash scripts/gen-dev-certs.sh && bash scripts/gen-env.sh
  cd deploy && docker compose up -d
  bash scripts/e2e.sh

======================================================================
8. LIMITES, DISCREPANCIAS Y PENDIENTES (declarados)
======================================================================
- No se desplego en el dominio real ni se probo desde internet. El TLS es de laboratorio
  (CA propia). Falta DNS y certificado publico para el canal de lectura, y una CA privada
  para las entidades.
- Discrepancia: el PDF general del equipo usa JsonWebKey2020/publicKeyJwk; la ERSo 005 exige
  Multikey. Se siguio la ERSo; el validador y el consumidor rechazan otros tipos de clave.
- ERSo 006: el PDF asigna la implementacion a Luis Gonzalez y las pruebas a Geovani Rincon.
  Lo implementado es una implementacion de referencia que el responsable debe revisar.
- Base58 y Multikey son implementacion propia, verificados por ida y vuelta y por el prefijo
  "zDn"; no se contrastaron con vectores oficiales.
- El consumidor valida contra el documento ACTUAL; no hay validacion historica de firmas ni
  endpoint publico de historial.
- Sin recuperacion por perdida de clave (la ERSo 008 lo advierte): la desactivacion es terminal.
- Sin limitacion de tasa (rate limiting), sin rotacion automatica de secretos, sin revocacion
  anticipada de tokens.
- La confianza en los encabezados de mTLS depende de que el puerto de la aplicacion no se publique.
- Las fechas de desarrollo de las ERSo 004 (21-sep), 008 (23-sep) y 007 (25-sep) ya habian
  pasado el 28-sep.
- Las pruebas funcionales formales, la plantilla de pruebas y las firmas de la tabla de
  actividades corresponden a las personas responsables; aqui se aportan evidencias tecnicas.
- El stack Docker quedo corriendo al terminar (cd deploy && docker compose down lo detiene).
