IMPLEMENTACION DE LAS ERSo 2026-001, 2026-002 Y 2026-003 (cartera, credenciales y DID del titular)
Proyecto: Identidad Digital Soberana SSI
Fecha de la sesion: 29-sep-2026
Dominio: civica-desarrollo.avance.org.co
Ubicacion del proyecto: /home/geovani/Descargas/generic/bitacora/new_proyect

NOTA: este documento es una cronica resumida del hilo de trabajo con Claude Code
(peticiones, analisis, decisiones, pasos y resultados). No es una transcripcion literal.
Continua a implementacion.txt (ERSo 004 a 008, 28-sep-2026).

======================================================================
1. PETICION Y PUNTO DE PARTIDA
======================================================================
- Se pidio analizar las ERSo 2026-001, 002 y 003 (nuevas en plan_tareas), aplicar el mismo procedimiento que con
  las anteriores, implementarlas en el proyecto practico y documentarlas en informes .md.
- Antes de escribir codigo se leyeron: los tres PDF, el informe 004 (como plantilla), el codigo base de did-core,
  y los documentos nuevos del equipo en bitacora/: la imagen de arquitectura (Rocio), el requerimiento de arquitectura
  y el informe de almacenamiento en Android Keystore (del propio desarrollador de 001-003).
- Hallazgo que cambio el diseno: el diagrama de Rocio dice que el Wallet Backend recibe el DID Document construido
  por la app y lo PUBLICA en el VDR (pasos 4 a 6). Es decir, las ERSo 001-003 se conectan con el VDR ya construido.
- Tambien estan en /home/geovani/Descargas/generic/: el wallet Android EUDI, dos emisores EUDI (eudi-srv-pid-issuer y
  eudi-generic-issuer) y "lissi" (que resulto ser solo capturas de pantalla). Se exploraron en modo solo lectura.

======================================================================
2. ANALISIS DE LO EXISTENTE (docs/ANALISIS-BASES-EUDI.md)
======================================================================
- Wallet Android: delega las claves a eudi-lib-android-wallet-core 0.30.2; solo PIDE StrongBox (useStrongBoxForKeys=true)
  con caida silenciosa; NO detecta ni declara el nivel real; NO verifica la attestation; NO tiene instancia de cartera,
  recuperacion ni respaldo; NO tiene nada de DID.
- Emisores EUDI: dc+sd-jwt y mso_mdoc, ES256, OpenID4VCI 1.0, descubren la clave del emisor por x5c (no por DID),
  usan DPoP y (el generico) cifrado JWE. No usan la libreria openid4vci-kt: implementan a mano.
- Conclusion: 001 y 003 son trabajo nuevo; en 002 la brecha de perfil mas importante es x5c frente a DID.

======================================================================
3. DECISIONES DE DISENO
======================================================================
- Nuevos modulos: credentials-core (002), wallet-core (001/003), wallet-sim (dispositivo simulado + CLI de escenarios),
  wallet-service (Wallet Backend), credential-service (emisor OpenID4VCI + verificador OpenID4VP).
- No hay hardware seguro en una JVM. Decision: custodio SIMULADO que declara SOFTWARE; el contrato KeyCustodian expresa la
  no exportabilidad (ningun metodo devuelve material privado); el diseno Android queda en docs/ANDROID-REFERENCIA.md SIN compilar.
- Nivel de proteccion: el backend lo LEE de la key attestation (esquema publico de Android, OID 1.3.6.1.4.1.11129.2.1.17),
  con un lector DER propio y validacion de cadena con el JDK. Se prueba con cadenas de LABORATORIO (BouncyCastle, solo en wallet-sim).
- Igualdad estricta declarado = verificado; sin evidencia se asume SOFTWARE; arranque no verificado reduce a SOFTWARE.
- DID del titular: did:web:<dominio>:titulares:<huella RFC 7638 de la clave publica> (derivacion decidida por el proyecto).
- El VDR gana un namespace comodin de UN nivel (titulares/*) para que el Wallet Backend publique sin reservar cada DID.
- Credenciales: clave del emisor descubierta por DID (desviacion declarada respecto a EUDI/ISO, que usan x5c/x5chain).
- Recuperacion: NO recupera la clave; crea cartera nueva, revoca la anterior; las credenciales deben reemitirse.

======================================================================
4. PROCESO DE IMPLEMENTACION (resumen cronologico)
======================================================================
1) Andamiaje Gradle (5 modulos), catalogo de versiones (com.upokecenter:cbor 4.5.6; BouncyCastle solo para pruebas/simulador).
2) did-core: Jws.signWith (firma delegada al custodio, typ opcional) e informe de conformidad DidConformance (10 comprobaciones).
3) credentials-core: SD-JWT VC (emisor, titular, verificador con KB-JWT), mdoc (IssuerSigned/MSO/COSE_Sign1), perfiles,
   registro de ejecucion, inspector de artefactos, matriz de claves. 12 pruebas.
4) wallet-core: KeyCustodian, SoftwareKeyCustodian, HolderDidService, SealedDocumentStore. 15 pruebas (incluye no exportabilidad
   por reflexion y busqueda de la clave privada real en todas las salidas).
5) vdr-service: namespace comodin. 2 pruebas nuevas; las 27 anteriores siguen en verde.
   ERROR CURIOSO: un KDoc con la secuencia "titulares/*" abrio un comentario anidado (Kotlin anida /* */) y se trago el archivo.
6) wallet-service: verificador de key attestation, servicio (activacion, recuperacion, DID, respaldo), rutas. 32 pruebas con el VDR real en proceso.
   Correccion de la prueba de inventario (TestApplication arranca perezosamente).
7) credential-service: emisor y verificador. 16 pruebas. HALLAZGO REAL de una prueba: POST /credential sin cuerpo daba 400 y debia dar 401
   (se leia el cuerpo antes de autenticar). Corregido.
8) wallet-sim: autoridad de attestation persistente, Holder App, cartera OpenID4VCI/VP y CLI de escenarios.
9) Despliegue: nginx con canal :8444 (DNS resuelto por peticion para que la caida de un servicio no tumbe el VDR), compose con wallet,
   credential y holder, .dockerignore (lista blanca: hubo que anadir los modulos nuevos), certificado wallet-backend incremental,
   .env actualizado sin tocar secretos.
10) Recorrido E2E extendido. Primera corrida: 100 PASS / 7 FAIL, todos por errores de MIS scripts, no de los servicios:
    - rutas duplicadas /wallet/v1/wallet/v1/... en los escenarios;
    - "docker compose run" recreo el VDR que se habia apagado a proposito (hay que usar --no-deps);
    - un escenario que se caia con excepcion NO contaba como fallo (hueco corregido: ahora cuenta y exige linea RESULTADO).
    Corrida final desde entorno limpio: 121 PASS / 0 FAIL.
11) Documentacion: 3 informes nuevos, nota de actualizacion en los de 004-008, PERFIL-CREDENCIALES, ANALISIS-BASES-EUDI, ANDROID-REFERENCIA,
    ampliacion de la guia de exposicion (capitulo 11) y de las versiones normativas, README.

======================================================================
5. RESULTADOS
======================================================================
Pruebas automaticas: 130, 0 fallos, 0 omitidas
  did-core 14 | did-resolver 12 | vdr-service 29 | credentials-core 12 | wallet-core 15 | wallet-service 32 | credential-service 16
Recorrido E2E: 121 comprobaciones PASS, 0 FAIL (evidencias/e2e-20260929-155349.log); 56 de 004-008 + 65 nuevas.

Criterios por ERSo (detalle y evidencia en informes/):
  ERSo 001  C1 instancia registrada y activa, asociada al ciudadano            OK
            C2 clave no exportable                                             OK en contrato y backend; NO verificado en hardware real
            C3 nivel declarado = disponible                                    OK con evidencia de LABORATORIO
            C4 recuperacion sin acceder a la clave privada                     OK
  ERSo 002  C1 emision/recepcion dc+sd-jwt y mso_mdoc                          OK entre componentes propios; NO contra terceros
            C2 declarado = usado                                               OK
            C3 sin protocolo propio paralelo                                   OK
            C4 firma del emisor separada de la prueba del titular              OK
  ERSo 003  C1 DID sin exponer la clave privada                                OK (custodio simulado)
            C2 conformidad W3C DIDs                                            OK estructural (no la suite oficial)
            C3 relaciones apuntan a la clave Multikey                          OK
            C4 almacenamiento no exportable asociado a la instancia            OK (almacen simulado)

======================================================================
6. LIMITES, DISCREPANCIAS Y PENDIENTES (declarados)
======================================================================
- Sin hardware seguro real ni attestation real; sin implementacion Android (solo diseno).
- Sin prueba cruzada con el emisor/wallet EUDI; perfil distinto (clave del emisor por DID en vez de x5c); sin DPoP, JWE, presentacion mdoc,
  revocacion, JAR ni direct_post.jwt; estado del emisor/verificador en memoria.
- Varios detalles de OpenID4VCI/VP, SD-JWT y COSE estan escritos de memoria del texto final: confirmar en docs/VERSIONES-NORMATIVAS.md.
- Se publican DID de ciudadanos via el Wallet Backend (decision del diagrama del equipo): riesgo de correlacion. Esto MATIZA lo dicho en la guia
  de 004-008 ("no se publica un DID por ciudadano"), que fue actualizada.
- La recuperacion no recupera la clave; el DID huerfano no se desactiva; no hay rotacion del DID del titular via backend.
- No se emite la Wallet Instance Attestation (JWT) que espera el wallet EUDI.
- Alta de ciudadano abierta en laboratorio; verificacion de identidad fuera de alcance.
- El criterio 1 de la ERSo 003 aparece duplicado en el PDF; se trato como uno solo.
- Las fechas de desarrollo y prueba de 001 y 002 ya habian pasado; la prueba de 003 es el 30-sep.
- Las pruebas funcionales formales, la plantilla de pruebas y las firmas de las tablas de actividades corresponden a las personas responsables.
- Se eliminaron tres logs E2E intermedios de desarrollo (corridas abortadas o con los fallos de script descritos arriba).
