# Fase 5: verificación académica para un empleador (diseño, 2026-10-22)

Estado: **construido y publicado** en `https://civica-desarrollo.avance.org.co/contratacion/` (ver «Construido» al final).
La idea del usuario: el empleador (p. ej. Avance) ofrece **un QR por nivel académico**.

## Lo comprobado
- Verifier oficial: la consulta DCQL admite filtrar por valor (`ClaimsQuery.values`).
- Wallet (`eudi-lib-android-wallet-core`, `DcqlRequestProcessor`): evalúa `values` («el dato se cumple si algún
  valor coincide con alguno de `values`»). Se comprobó en el código de referencia, no en el binario 0.30.2 de la app:
  hay que probarlo en el teléfono.
- Credencial de la UIS: `vct` `urn:eudi:academic:credential:1`, emitida en SD-JWT. Niveles que emite
  (`AcademicLevel`): `TECNICO`, `TECNOLOGICO`, `PREGRADO`, `ESPECIALIZACION`, `MAESTRIA`, `DOCTORADO`
  (el valor viaja en mayúsculas y sin tildes).
- La credencial lleva `email` y `phone_number` **firmados por la UIS** (el empleador puede pedirlos con valor probatorio).

## Diseño propuesto
- Portal del empleador (`verifiers/contratacion`, mismo patrón que el banco) con una tarjeta por nivel. Cada tarjeta
  crea una solicitud para `urn:eudi:academic:credential:1` con `academic_level` + `values: ["<NIVEL>"]` y los datos
  que el empleador necesita (nombre, programa, título otorgado, estado académico, fecha de graduación).
- La wallet solo ofrece las credenciales de ese nivel (si la persona tiene un pregrado y una especialización, para el
  QR de especialización solo aparece esa).
- Opcional: añadir `academic_status` con `values: ["GRADUADO"]` para verificar que terminó.
- Resultado para el empleador: ficha verificada (persona, programa, título, institución emisora y sello de confianza).

## Problemas a resolver antes
1. **Divulgación de más datos.** El emisor de la UIS emite como claim **plano** (siempre visible) casi todo:
   `student_id`, `document_number`, `identification_type`, `program_code`, `program_name`, `academic_level`,
   `faculty`, `academic_unit`, modalidad, créditos, fechas y estado. Solo son divulgables por separado `family_name`,
   `given_name`, `email`, `phone_number`, `awarded_title` y `accumulated_average`. Aunque el empleador solo pida el nivel,
   recibe también la cédula y el usuario estudiantil. El `vct` (metadatos) dice `sd: always` para casi todos, y el
   código no lo cumple. Arreglo: emitir esos datos con `sdClaim` y reemitir las credenciales.
2. **«Bachiller»** no lo emite una universidad: no existe en la credencial de la UIS. Haría falta otro emisor.
3. **La status list de la UIS** vive en el stack de la UIS: para verificar una credencial de la UIS el stack de la UIS
   tiene que estar arriba (hoy comparte máquina con la Registraduría).

## Decisiones del usuario (2026-10-22)
- Arreglar el emisor de la UIS para que los datos se compartan **solo si el verificador los pide**: **hecho**.
- Niveles: Doctorado, Maestría, Especialización, Pregrado, Tecnología, Técnico y **Bachiller** (este último queda
  reservado: la UIS no lo emite; será de un futuro emisor de colegio).
- Exigir **solo graduados** (`academic_status` con `values: ["GRADUADO"]`), no estudiantes.

## Cambio hecho en el emisor de la UIS
- `IssueAcademicCredential.kt`: solo siguen siempre visibles `issuing_authority`, `issuing_country`,
  `issuing_institution_name`, `date_of_issuance` y `date_of_expiry`. Todo lo demás (incluidos `document_number`,
  `student_id`, programa, nivel, estado, fechas académicas y contacto) pasa a ser divulgable por separado.
- `vct/academic_credential.json`: `student_id`, `document_number` y `program_code` pasan de `sd: never` a `always`.
- Pruebas: `AcademicCredentialDisclosureTest` (3 casos). La suite del emisor queda en 102/103: la que falla es
  `IssuerUiTest` (la UI devuelve 303), anterior y sin relación (archivos sin tocar desde el 20-oct).
- Imagen `eudi-generic-credential-issuer:local` reconstruida (respaldo: `:antes-divulgacion`) y contenedor
  `uis-issuer` recreado.
- **Las credenciales ya emitidas conservan el formato anterior** (datos planos): hay que borrarlas y reemitirlas.

## Cómo lo plantea la UE (lo que dice el corpus del repositorio)
- ARF §5: un diploma es una atestación; según quién la emita es QEAA, PuB-EAA o EAA no cualificada (la diferencia es solo legal).
- Cada tipo de atestación debería tener un *Attestation Rulebook* publicado; el catálogo del corpus solo trae `pid` y
  `mdl`: **no hay un rulebook oficial de credenciales académicas** con una lista de datos obligatoria.
- Principios aplicables: el verifier pide solo los atributos registrados para su uso previsto; la wallet muestra
  quién pide, qué atributos y para qué; ejemplo de admisiones (tema K): se pre-evalúa con las credenciales **sin
  identificar a la persona** y la identidad se pide después en otra presentación, lo que evita discriminar por el apellido.

## Reemisión de la credencial de la UIS con la wallet nueva (2026-10-22)
- Síntoma: «¡Vaya! Algo salió mal» al emitir. Log de la wallet: `POST /idp-uis/.../par/request` → 401
  «Authentication failed»; Keycloak-UIS: `PUSHED_AUTHORIZATION_REQUEST_ERROR error="client_not_found"`.
- Causa: la wallet nueva se identifica como `client_id=newwallet-app`, que existía solo en el realm de la Registraduría.
  El realm de la UIS solo tenía `eudiw-abca` (el cliente de la wallet anterior), por eso ayer funcionó y hoy no.
- Arreglo: cliente `newwallet-app` añadido al realm de la UIS, copia de su `eudiw-abca` (mismos alcances, incluido
  `urn:eudi:academic:credential:1:dc+sd-jwt`, atestación `abca-draft07`, PAR y DPoP obligatorios):
  - en `eudi-srv-issuer-pid-uis/docker-compose/keycloak/realms/uis-issuer-realm-realm.json` (para arranques nuevos);
  - en el Keycloak en marcha, con `kcadm.sh` dentro del contenedor.
- El borrado del caché de la wallet no tuvo que ver: borra imágenes (`coil3_disk_cache`) y listas (`lote-cache`),
  no documentos ni preferencias.

## Construido (2026-10-22)
- `verifiers/contratacion/`: portal con una tarjeta por nivel (Doctorado, Maestría, Especialización, Pregrado,
  Tecnología, Técnico; **Bachiller** bloqueado: «Próximamente») y su verifier `verifier-contratacion` (actor #24).
- Cada QR pide `urn:eudi:academic:credential:1` con **filtros por valor** `academic_level = <NIVEL>` y
  `academic_status = GRADUADO` y los datos `program_name`, `awarded_title`, `graduation_date`. No pide nombre ni documento.
  Comprobado en el `request.jwt` real.
- El servidor vuelve a comprobar lo recibido (tipo de credencial, nivel, estado graduado): no se fía solo del filtro.
- **Identidad: paso aparte y opcional** («Confirmar identidad»): `given_name`, `family_name`, `identification_type`,
  `document_number`, con los mismos filtros, y solo después de haber verificado la formación de ese nivel.
- Nada se guarda: los resultados viven en memoria 15 min; el log solo dice «paso, nivel, verificada/rechazada». El verifier
  conserva cada presentación 30 min en memoria.
- Plantilla común: `WRPRC_VCTS` (nuevo) hace que el certificado de registro de un verifier declare otra credencial; el de
  contratación declara la académica, no el PID. Por defecto sigue declarando el PID.
- Ruta `/contratacion` en `srv-nw-nginx`.

## Pendiente
- Prueba con la wallet v2: que el filtro por valor ofrezca solo la credencial del nivel (se verificó en el código de
  referencia, no en el binario de la app), y qué muestra el historial.
- Para verificar, el stack de la UIS tiene que estar arriba (su status list). La Registraduría y la UIS comparten máquina.

## Prueba con la wallet y ajuste del portal (2026-10-22)
- Prueba real: 3 verificaciones (Especialización ×2, Pregrado) sin rechazos. En las tres viajaron solo 4 datos siempre
  visibles (institución, país, autoridad, fecha de emisión) y 5 divulgados (nivel, estado, título, fecha de grado,
  programa): no viajó cédula, usuario estudiantil, nombre, correo ni celular. El arreglo del emisor funciona.
- Ajuste pedido: al verificar un nivel, **su botón se pone verde y se desactiva** («✔ Verificado») y **los datos compartidos
  quedan abajo** del portal, uno por nivel; «Confirmar identidad» añade ahí la identidad; «Quitar de la pantalla» borra
  el bloque y vuelve a habilitar el botón. Todo en memoria del navegador, nada en disco ni en el servidor.
- Comprobado en un navegador con el servidor simulado: un nivel verde y desactivado, los demás libres, Bachiller bloqueado,
  dos niveles a la vez, identidad en el bloque correspondiente.
