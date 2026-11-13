# Tratado del ecosistema EUDI Wallet de la Unión Europea
## Cómo se concibió, cómo se construyó en código y si logrará lo que promete

Estado de las fuentes: 6 de noviembre de 2026.

Este documento trata del ecosistema **original de la UE**, no del laboratorio Avance. Se basa en dos fuentes:

- **El código real** de los repositorios de `Wallet/` y `librerias/`, leído por seis análisis independientes (cartera, protocolos, emisores, verificador y confianza, firma remota). Cada hallazgo cita archivos.
- **La norma**: eIDAS 2.0, el ARF v3.0.0, los actos de ejecución y sus cronogramas, tomados del corpus local (`docs/corpus-eudi`).

Convención de certeza, usada en todo el texto:

| Marca | Significa |
|---|---|
| **[V]** | Verificado en el código o en el texto de la norma citado. |
| **[I]** | Inferencia mía a partir de lo verificado. Puede estar equivocada. |
| **[NV]** | No pude verificarlo con las fuentes disponibles. No lo afirmo. |

Este tratado complementa a `GUIA-EUDI-DE-CERO-A-EXPERTO.md`, que explica los conceptos básicos y los flujos. Aquí se asume que ya los conoces (si no, lee primero esa guía) y se profundiza en el porqué del diseño, el estado real del código y la evaluación crítica.

---

## Índice

1. La concepción: de eIDAS 1 al ARF
2. Los objetivos legales de eIDAS 2.0
3. La arquitectura del ARF
4. Las decisiones de diseño más disputadas
5. Cómo se materializó: el código, pieza por pieza
6. Tres flujos recorridos en el código real
7. La brecha entre diseño y realidad
8. Cronograma: hasta fin de 2026 y después
9. ¿Logrará los objetivos? Evaluación
10. Qué aprender de este caso
11. Lo que este tratado no sabe

---

## 1. La concepción: de eIDAS 1 al ARF

### 1.1 El problema que eIDAS 1 no resolvió
El Reglamento 910/2014 dejaba la identificación electrónica en manos de cada Estado:

- **[V]** Cons. 13 del texto original: los Estados no estaban obligados a notificar sus sistemas de identificación; decidían si notificar "todos, algunos o ninguno".
- **[V]** Cons. 17: el uso por el sector privado solo se "fomentaba", de forma voluntaria.
- **[V]** Cons. 5 del Reglamento 2024/1183 (eIDAS 2.0): el objetivo declarado es reducir "la fragmentación [...] por soluciones nacionales divergentes o, en algunos Estados miembros, la ausencia de soluciones".
- **[I]** Sin obligación de ofrecer ni de aceptar no hay masa crítica. Eso explica que eIDAS 2.0 sea más obligatorio que su antecesor.
- **[NV]** El corpus no contiene la evaluación de la Comisión sobre eIDAS 1 ni cifras de uso. No las uso.

### 1.2 La idea fundadora: el usuario controla
**[V]** Cons. 1 del 2024/1183: un derecho a una identidad digital bajo **control exclusivo del usuario**, válida para servicios públicos y privados, en línea y fuera de línea. Esa frase explica casi todo el diseño técnico posterior: por qué las credenciales viven en el teléfono, por qué hay divulgación selectiva y por qué el emisor no puede ver dónde se usa una credencial.

### 1.3 Soft law que se endurece por capas
La UE no empezó legislando la tecnología. Fue por etapas, cada una más vinculante que la anterior:

```
 Recomendación (UE) 2021/946  →  "Toolbox" + ARF  →  Reglamento 2024/1183  →  Actos de ejecución (CIR)  →  Estándares
 (junio 2021, no vinculante)     (informativo)         (la ley)                (obligaciones concretas)      (ETSI, OpenID, ISO...)
```

- **[V]** La Recomendación de 3 de junio de 2021 pidió a los Estados cooperar con la Comisión en una *Toolbox* con el ARF, estándares comunes y buenas prácticas, "sin interferir ni prejuzgar el proceso legislativo" (ARF §1.2).
- **[V]** El ARF lo coordina el Grupo de Cooperación y es **informativo, no vinculante** (ARF §1.8). Lo vinculante son los reglamentos de ejecución, que se actualizan por rondas.
- **[V]** El cons. 24 del 2024/1183 cita esa Recomendación y pide acordar una arquitectura técnica común y "elementos comunes de modelo de negocio y estructura de tarifas", para facilitar la adopción por las pymes.
- **[I]** Es una forma de legislar "con el código por delante": se prueba la arquitectura con pilotos y con implementación de referencia, y luego se fija en ley lo que funcionó.

### 1.4 Los pilotos a gran escala y la implementación de referencia
- **[V]** El ARF §1.4 describe la convocatoria de 22 de febrero de 2022 (Programa Europa Digital): los pilotos a gran escala prueban casos de uso con actores públicos y privados y retroalimentan el ARF.
- **[NV]** Los nombres de los consorcios (POTENTIAL, NOBID, EWC, DC4EU) no constan en el corpus y no se afirman aquí. Sí aparece, dentro del repositorio `eudi-wallet-rfcs`, que es del consorcio **EWC** y define perfiles de interoperabilidad para pilotos (ver §5.5).
- **[V]** El ARF (§1.3) dice que alimenta una **implementación de referencia** de la Wallet Solution. Los repos que se analizaron son esa implementación, con licencia Apache 2.0.
- **[NV]** El corpus no dice qué equipos o empresas los desarrollan. No lo afirmo.

### 1.5 La evolución del ARF
Según el CHANGELOG del repositorio del ARF **[V]**:

| Versión | Fecha | Hito |
|---|---|---|
| 1.0.0 | 10-feb-2023 | Publicación inicial |
| 1.4.0 | 20-may-2024 | Reestructura de anexos |
| 1.5.0 | 4-feb-2025 | Alineada con los CIR adoptados de los arts. 5a y 5c; más de 275 comentarios incorporados |
| 1.6 a 1.8 | mar-2025 | Resultados de los *topics* de discusión: privacidad, lotes, WUA, pseudónimos, DC API, ZKP, reglas del PID |
| 1.10.0 | 2-may-2025 | Alineada con el CIR 2025/848 (registro de partes usuarias) |
| 2.0.0 | 28-may-2025 | Avisos: WUA, pseudónimos y ZKP se revisarán |
| 2.1.0 | jun-2025 | Se retira la presencia posible del WUA en la interfaz hacia las partes usuarias |
| 2.5.0 | | Las reglas del PID y del mDL pasan al repositorio de rulebooks; se añade accesibilidad |
| 2.6.0 | | **La vinculación al dispositivo pasa de obligatoria a recomendada** (Topic Z) |
| 2.7.0 | 10-nov-2025 | El Anexo 2 original pasa a CSV (725 requisitos HLR) |
| 3.0.0 | 21-jul-2026 | Alineada con los CIR modificadores de julio de 2026; concepto de *Relying Party Services*; anclas desde listas ETSI TS 119 612 **y** TS 119 602; marco de conformidad FCAF |

Lectura **[I]**: el ARF no es un documento cerrado. Cambia varias veces al año, y las decisiones más importantes (vinculación, WUA, ZKP) se han revertido o reabierto. Quien construya sobre él debe asumir cambio continuo.

---

## 2. Los objetivos legales de eIDAS 2.0

Texto consolidado de eIDAS (corpus: `fuentes/regulaciones/02014R0910-20241018.txt`). Las siguientes obligaciones son los **objetivos medibles** contra los que se evalúa el éxito:

| Artículo | Obligación | Observación |
|---|---|---|
| **5a(1)** | Cada Estado ofrece **al menos una** wallet en 24 meses desde la entrada en vigor de los actos de ejecución | Plazo derivado: hacia el 24-dic-2026 **[I]** |
| 5a(2) | La wallet puede ser estatal, mandatada o de un tercero "independiente pero reconocido" | Abre la puerta a proveedores privados |
| 5a(3) | Código fuente de los componentes de aplicación con licencia open source | Excepción para lo que no está en el dispositivo |
| 5a(4) | Funciones: solicitar, guardar y presentar PID y atestaciones; **pseudónimos**; interacción entre wallets; **panel de transacciones** con solicitud de borrado y denuncia; firma cualificada; portabilidad | |
| 5a(5)(b) | La wallet **no informa** a los emisores del uso de las atestaciones | Es el "no llamar a casa" |
| 5a(5)(g) | Firma electrónica cualificada **por defecto y gratis** para personas físicas (puede limitarse a uso no profesional) | |
| 5a(11) | Nivel de garantía **alto** | |
| 5a(13) | Emisión, uso y revocación **gratuitos** para personas físicas | |
| 5a(15) | Uso **voluntario**; no se puede desfavorecer a quien no la use | |
| **5a(16)** | **No seguimiento ni correlación** tras la emisión, y técnicas que garanticen la **no vinculabilidad** | La más difícil técnicamente |
| 5b | Las partes usuarias se **registran** y no pueden pedir datos distintos de los registrados | |
| 5c | **Certificación** por organismos designados; ciberseguridad con esquemas europeos (Reglamento 2019/881) | |
| 5d, 5e | Lista pública de wallets certificadas; suspensión y retiro en caso de brecha | |
| **5f(1)** | Los servicios públicos que exijan identificación electrónica **deben aceptar** la wallet | |
| **5f(2)** | Privados con autenticación reforzada obligatoria (banca, transporte, energía, salud, telecom...) deben aceptarla en **36 meses** (dic-2027, derivado **[I]**), solo si el usuario lo pide | Se exceptúan micro y pequeñas empresas |
| 5f(3) | Las plataformas en línea de muy gran tamaño (DSA) deben aceptarla para autenticación | |
| 5f(5) | Evaluación de la Comisión 24 meses tras el despliegue | |
| 45b y ss. | Atestaciones electrónicas: efecto jurídico equivalente al papel para las cualificadas y las de organismos públicos con fuente auténtica | |
| 49 | Informe de la Comisión antes del **21-may-2030** y luego cada cuatro años | |

Dos advertencias sobre cifras:

- **[V]** El objetivo de **80 % de ciudadanos con wallet en 2030** **no aparece** en el 2024/1183 ni en el texto consolidado. El cons. 3 solo habla de "despliegue amplio" vía la Década Digital. Si ves esa cifra, viene de otra fuente.
- **[V]** La técnica de **conocimiento cero** (ZKP) aparece en el cons. 14 con lenguaje de "debería", no de obligación.

---

## 3. La arquitectura del ARF

### 3.1 Actores (ARF cap. 3) [V]
Usuario. **Wallet Provider** (responsable de la solución y de garantizar un WSCD adecuado; firma las atestaciones de instancia y de claves). **PID Provider** (identifica con garantía alta y emite el PID). Proveedores de atestaciones: QEAA, PuB-EAA y EAA no cualificadas. Proveedores de listas de confianza. Partes usuarias, con servicios e instancias. Organismos de evaluación de la conformidad y supervisores. Registradores, CA de acceso y proveedores de certificados de registro.

### 3.2 Componentes de la Wallet Unit (§4.3.2) [V]
Dispositivo, Wallet Instance, **WSCA/WSCD** (aplicación y dispositivo criptográfico seguros) y almacén de claves. La instancia puede ser una aplicación web; en ese caso el WSCD es un HSM remoto.

### 3.3 Cuatro arquitecturas de WSCD permitidas (§4.5) [V]
Remoto (HSM del proveedor), local externo (tarjeta o token), local interno (eSIM, elemento seguro) y local nativo (API del sistema operativo). La elección tiene un efecto enorme en seguridad y en dependencia de terceros (ver §9).

### 3.4 Confianza (§6.1) [V]
PID, QEAA y PuB-EAA se validan con PKI X.509 y listas de confianza ETSI (TS 119 612 y, desde la v3.0, listas de entidades TS 119 602). Las EAA no cualificadas pueden usar otros modelos. El registro de partes usuarias se rige por el CIR 2025/848.

### 3.5 Formatos [V]
Base: **mdoc** (ISO 18013-5) y **SD-JWT VC**. El modelo W3C VCDM es opcional. **No hay DID.** El mdoc obliga a la clave de dispositivo (vinculación); en SD-JWT VC es opcional (ver Topic Z).

---

## 4. Las decisiones de diseño más disputadas

El ARF documenta sus debates abiertos en *discussion topics*. Estos son los que más condicionan el éxito.

### 4.1 No vinculabilidad frente a credenciales por lotes (ARF §7.4.3.5.2; Topic 10, ISSU_36 a ISSU_57) [V]
- El problema: si la credencial lleva una firma fija del emisor, dos verificadores que la reciban pueden reconocer que es la misma, y el emisor, si colabora con ellos, puede saber dónde se usó.
- La solución adoptada: **emitir varias copias** de la misma credencial, cada una con firma y clave distintas, y usar una por presentación. Método A (uso único) y B (validez limitada) son obligatorios; C y D, opcionales.
- El propio ARF admite que, con hashes salados, la vinculabilidad hacia el proveedor "no puede eliminarse del todo". Solo una prueba de conocimiento cero la elimina.
- **[I]** Entonces, el mandato legal 5a(16)(b) queda cumplido en parte por medidas organizativas y auditorías, no solo por criptografía. Es la brecha más clara entre la ley y el diseño técnico.

### 4.2 ZKP (ARF §7.4.3.5.3; Topic 53; Topic G) [V]
"No specific ZKP has been selected". La integración es compleja y el hardware seguro actual no la soporta. Hay requisitos condicionales y estándares en preparación. El Topic G se reabre en la iteración 6 de 2026. En el código, la cartera de referencia incluye una integración con **Longfellow** (ver §5.1), pero no es el camino principal.

### 4.3 Atestación de la wallet: WIA, KA y WUA (Topic C) [V]
Hubo ida y vuelta: en 2.1.0 se retiró el WUA de la interfaz con las partes usuarias; la WIA y la atestación de claves se envían al emisor una sola vez; hubo revisión en 2.9.0. La razón: cada vez que viaja una atestación estable se crea un identificador de correlación.

### 4.4 Vinculación al dispositivo (Topic Z, v2.6.0) [V]
Pasó de obligatoria a **recomendada**. Un motivo es que dificulta la ZKP y la portabilidad entre dispositivos. **[I]** Esto relaja una de las defensas contra la copia de credenciales.

### 4.5 Digital Credentials API (ARF §4.4.3.3; Topic F) [V]
Es un borrador de trabajo del W3C, no implementado por todos los navegadores y sistemas. Las wallets deben soportarla; las partes usuarias pueden seguir usando esquemas URI propios. El ARF fija expectativas de neutralidad, pero solo la regulación puede hacerlas cumplir.

### 4.6 Dónde viven las claves y quién es el proveedor (§4.5; art. 5a(2)) [V/I]
- **[V]** El HSM remoto se admite si el proveedor garantiza que solo accede la instancia legítima.
- **[V]** Para WSCD internos o nativos se aceptan "supuestos" de resistencia a ataque alto (CIR 2024/2981 anexo IV).
- **[I]** Esos supuestos pueden diluir la garantía real. En la práctica, la mayoría de usuarios dependerá del hardware de Google o Apple, o de un HSM del proveedor.
- **[NV]** El corpus no documenta críticas de la sociedad civil ni de las autoridades de protección de datos. Habría que buscarlas fuera.

### 4.7 Pagos y firma (Topics 20 y 16; Topics AA y AB) [V]
La autenticación fuerte para pagos (TS12, PSD2) y la confianza para firmas y sellos cualificados siguen abiertas. Los Topics AA y AB están reabiertos en las iteraciones de fin de 2026. El ARF reconoce (§7.3) que aún no incluye las interacciones de confianza para firmas o sellos cualificados.

---

## 5. Cómo se materializó: el código, pieza por pieza

### 5.1 Principio de conjunto [V/I]
La UE **no entrega una wallet monolítica**. Entrega:

1. **Una librería pequeña por estándar** (OpenID4VCI, OpenID4VP, SD-JWT, Token Status List...), con interfaces que el integrador implementa.
2. **Los puntos de confianza se inyectan desde fuera.** Las librerías no deciden en quién se confía: `CertificateChainTrust`, `X509CertificateTrust`, `VerifyStatusListToken*Signature`, `ProvisionClientAttestation`, `RegistrationCertificatePolicy`. La política de confianza la ponen el Estado o el integrador.
3. **Aplicaciones y servicios de referencia** que ensamblan todo, marcados "initial development release, no usar en producción" **[V: en todos los README]**.

Lectura **[I]**: es coherente con la filosofía del ARF (arquitectura abierta, múltiples proveedores). Pero traslada la parte difícil, la confianza, a quien integra.

```
 App (UI)  →  wallet-core (SDK)  →  openid4vci-kt · openid4vp-kt · sdjwt-kt · statium · etsi1196x2
                    │                         + Multipaz (SecureArea, almacén, ISO 18013-5)
                    └──────────────►  Wallet Provider (atestaciones)
 Emisor (pid-issuer) ──► sdjwt-kt · statium           ← NO usa openid4vci-kt
 Verificador (endpoint) ─► sdjwt-kt · statium         ← NO usa openid4vp-kt
```

**[V]** Hallazgo de arquitectura: las librerías `openid4vci-kt` y `openid4vp-kt` son **solo del lado wallet**. El emisor y el verificador implementan su lado del protocolo con su propio código y solo reutilizan SD-JWT y la lista de estado.

### 5.2 La pila de librerías

| Librería | Versión / último commit | Qué implementa | Dato clave |
|---|---|---|---|
| `openid4vci-kt` | 0.14.x · 2-oct-2026 | OpenID4VCI 1.0 (rol wallet); PAR, PKCE, DPoP, ABCA draft 07, batch, diferida, notificación | Solo JVM. No trae lado emisor |
| `openid4vp-kt` | 0.16.x · 29-sep-2026 | OpenID4VP 1.0 (rol wallet); DCQL; `direct_post(.jwt)`, `dc_api`; 6 esquemas de `client_id` | Sin Presentation Exchange. Quitó SIOPv2 en 0.12.0 |
| `presentation-exchange-kt` | 0.5.x · abr-2026 | Presentation Exchange v2 (DIF) | **Obsoleta de hecho**: ningún repo la importa; OpenID4VP 1.0 usa DCQL |
| `sdjwt-kt` | 0.20.x · 2-oct-2026 | SD-JWT RFC 9901; SD-JWT VC draft 13; KB-JWT; type metadata | La más usada y activa. Endurecimientos recientes |
| `kmp-statium` | 0.5.x · 28-sep-2026 | Token Status List **draft 12**, JWT y CWT | **No verifica la firma por sí sola**; hay que inyectar el verificador. No tiene caché |

Observaciones **[V]**:
- Todas comparten Kotlin 2.2.21 y Ktor 3.3.3, y ninguna tiene CHANGELOG (los cambios se ven en `git log` y en `@Deprecated`).
- **Siguen borradores**: Status List draft 12, SD-JWT VC draft 13, ABCA draft 07 conviven con RFC 9901 y OpenID4VP/VCI 1.0 ya finales. De ahí la rotación rápida de APIs.
- Statium trae un `Ignore` para la verificación de firma, que su README marca "no para producción".
- Defensa notable: statium limita la descompresión a 16 MB para evitar bombas DEFLATE.

### 5.3 La cartera (Wallet Unit)

**Aplicación de referencia (`eudi-app-android-wallet-ui`)** [V]
- Kotlin 2.4, Compose, arquitectura multimódulo en capas, patrón MVI, DI con Koin, `minSdk 29`. Último commit 22-sep-2026.
- La app **no habla con las librerías EUDI directamente**: pasa por `wallet-core`.
- Dos *flavors*, `dev` y `demo`, que cambian URLs y el tamaño del lote (10 en demo, 60 en dev).
- Presentación: remota por OpenID4VP, por DC API y en **proximidad** por ISO 18013-5 (QR, NFC, BLE).
- Revocación: un trabajo periódico (`RevocationWorkManager`) consulta la lista de estado de cada documento y lo marca como revocado si el estado es inválido o suspendido.
- Panel de transparencia: pantalla de transacciones sobre el registro de `wallet-core`.
- Firma cualificada: integra la librería RQES (ver §5.7).
- **No encontré** pseudónimos, passkeys ni WebAuthn.

**SDK (`eudi-lib-android-wallet-core`)** [V]
- Módulos: `wallet-core`, `document-manager`, `transfer-manager`. Último commit 2-oct-2026. 164 archivos de test sobre 262 de producción.
- Claves: abstracción **`SecureArea`** de Multipaz. Por defecto usa Android Keystore (con StrongBox si existe); se puede conectar un HSM remoto o un eID con una implementación propia. El SDK **no trae** ningún WSCD distinto del Keystore.
- Confianza: listas ETSI TS 119 612 y TS 119 602 vía `etsi1196x2`.
- Registro: certificados de registro de emisores y de partes usuarias, en JWT y CWT, con comprobación de estado.
- ZKP: integración `multipaz-longfellow` (documentada en `ZKP.md`).
- DC API: presente pero **desactivada por defecto**.
- Respuestas cifradas de emisión: **no implementadas** (hay un `TODO`).
- `document-manager` es un repo antiguo, **discontinuado y divergido** del equivalente dentro de `wallet-core`.

**Variante de verificación de edad (`av-app-android-wallet-ui`)** [V]
Es un *fork* oficial de la app, pero con otro propósito: probar edad. Presenta solo `mso_mdoc`, sin SD-JWT ni QES. Añade **onboarding por pasaporte** (lectura NFC del chip, coincidencia facial local con modelos ONNX) y **ZKP Longfellow**. Aquí sí exige autenticación del usuario por clave (BIOMETRIC_STRONG). Declara explícitamente que sus endpoints son *mock* y que la política de lectores en la demo no debe usarse en producción.

### 5.4 El Wallet Provider (`eudi-srv-wallet-provider`) [V]
- Kotlin 2.4, Java 25, Ktor, arquitectura hexagonal, Exposed R2DBC. Tags hasta v0.2.1; último commit 8-oct-2026.
- Endpoints: `/challenge`, emisión de **WIA** y de **atestación de claves** (con variantes `platform-key-attestation/android|ios` y `jwk`/`jwk-set`), metadata (RFC 9728) y `/jwks`.
- **No implementa listas de estado**: pide a un servicio externo (`eudi-srv-statuslist-py`) un índice y lo mete en el claim `status`.
- **No tiene endpoint de revocación ni suspensión de unidades.** Revocar una WIA o una atestación ocurriría en el servicio de estado, no aquí.
- Validación de Android: solo **Android Key Attestation** (paquete, huellas de firma, StrongBox opcional, desafío de un solo uso). No hay Play Integrity.

### 5.5 El emisor

Hay **dos emisores de referencia** con roles distintos [V/I]:

| | `eudi-srv-pid-issuer` (Kotlin) | `eudi-srv-web-issuing-eudiw-py` (Python) |
|---|---|---|
| Rol | **Referencia limpia**: solo *Credential Issuer* (servidor de recursos) | **Demo e interoperabilidad**: emisor + flujo de usuario |
| Stack | Kotlin 2.4, Spring Boot 4.1 (WebFlux), Arrow, R2DBC | Flask 3.1, Python 3.13, APScheduler |
| Arquitectura | Hexagonal (`domain / port / adapter`) | Módulos planos |
| Authorization Server | **Externo y obligatorio** (Keycloak con extensión de client attestation) | Otro repo externo, no incluido |
| Credenciales | PID mdoc y SD-JWT, mDL, Learning Credential | Unas 30: PID, mDL, edad, EHIC, PDA1, IBAN, diploma... |
| Origen de datos | Usuario de Keycloak (demo) y un mock de mDL | Formulario de pruebas, nodo eIDAS de pruebas, OpenID4VP |
| Seguridad del credential endpoint | DPoP obligatorio, cifrado de petición y respuesta **requerido** por defecto | **No valida DPoP** en `/credential` (solo introspección) |
| Firma | Keystore JKS de software, `x5c` obligatorio | Claves PEM por país |

Detalles del emisor Kotlin **[V]**:
- Endpoints: metadata y `signed_metadata`, `POST /wallet/credentialEndpoint`, `/deferredEndpoint`, `/notificationEndpoint`, `/nonceEndpoint`, creación de ofertas.
- Solo `authorization_code`; el código preautorizado no está implementado.
- El `c_nonce` es un JWE sin estado, cifrado con una clave del keystore.
- Emisión diferida con repositorio **en memoria** (se pierde al reiniciar).
- Validación de la atestación de claves (`VerifyKeyAttestation.kt`): tipo, firma, `x5c`, algoritmos, `key_storage` y `user_authentication` exigidos (`iso_18045_high`), nonce, unicidad y estado.
- **Si no se configura la URL del servicio de confianza, confía en todos los Wallet Providers** (hay un aviso en el log).
- La **WIA no se valida dentro del emisor**: la comprueba el Authorization Server. El emisor solo recibe el `client_status`.

**Estado y revocación en el emisor Kotlin [V]**:
- Cada credencial recibe un índice por clave del lote en el servicio de estado (`/take`).
- Un trabajo programado (por defecto cada 8 horas) revisa el estado de la **WIA y de la atestación de claves** de cada credencial activa; si alguna es inválida, **revoca** la credencial (`/set`) y borra su registro local.
- Si no puede leer una lista, **solo avisa y no revoca** (falla abierto).
- **Sorpresa de diseño**: con la política de reutilización `None`, el emisor **no asigna estado a la credencial**, aunque otra función del mismo código declare que sí debería. El resultado real: un PID emitido con la configuración por defecto **no es revocable por su emisor**. Solo se revoca indirectamente, si su wallet o su almacén de claves figura como inválido.
- No hay una API de revocación a iniciativa del emisor (por cambio de datos o fallecimiento). Solo existe el flujo de petición del usuario en el emisor Python y la revocación derivada de la wallet.
- La Learning Credential nunca lleva estado.

**Servidor de listas de estado (`eudi-srv-statuslist-py`) [V]**
- Flask. `POST /take` y `POST /set` exigen clave de API; `GET /get` es público. Solo acepta `status=1` (válido o revocado, sin suspensión).
- Almacena en memoria y disco, con **asignación aleatoria de índices** (para no revelar el orden de emisión). Firma JWT (ES256) y CWT.
- Defectos verificados: falta una coma en la lista de tipos permitidos (dos tipos se concatenan), `app.debug = True`, **sin tests**, y cuando se agotan los índices vacía el estado en memoria. Es el repo más estancado (último commit nov-2025).

### 5.6 El verificador y la confianza

**Verificador (`eudi-srv-verifier-endpoint`) [V]** — Kotlin, Spring WebFlux, tag v0.12.0, activo (15-oct-2026).
- Crea solicitudes OpenID4VP firmadas (JAR) por valor o por referencia, con el **certificado de acceso** como clave. Soporta `pre-registered`, `x509_san_dns` y `x509_hash`. **No** soporta `verifier_attestation`.
- Respuesta `direct_post.jwt` cifrada por defecto, con clave efímera por transacción; también DC API.
- DCQL y *transaction data* (incluye tipos para firma cualificada).
- Valida mdoc (cadena, vigencia, `docType`, digests, estado vía CWT) y SD-JWT VC (cadena por `vct`, KB-JWT, estado vía JWT, type metadata).
- **Confianza**: si no se configura el servicio de validación, hace `log.warn("... Trusting all Attestation Issuers")` y **confía en todos**. Si se configura, delega en el `trust-validator`. No lee la lista de listas directamente.
- Su README admite que HTTPS y la protección de la API del verificador no están resueltas; CORS `*` por defecto.
- Con un certificado de registro solo hace `parse`; no se confirmó que valide su firma ni su cadena.

**Verificación presencial [V]**
- `eudi-lib-android-verifier-core` (v0.1.0, sin actividad desde sep-2025): engagement por QR o NFC, datos por **BLE**, confianza por un almacén de certificados que aporta el integrador. **No usa listas de confianza** ni el validador.
- La app multiplataforma trae anclas de PID **estáticas** de siete países. iOS solo tiene la interfaz.

**`trust-validator` [V]** — alfa v0.3.0.
- Una operación, `POST /trust`: recibe una cadena y un contexto (atestación del Wallet Provider, PID, QEAA, PuB-EAA, EAA, certificado de acceso, certificado de registro, estados...).
- Fuentes de anclas: lista ETSI TS 119 612 (vía DSS), lista de entidades TS 119 602 (JSON) o ambas, más un keystore.
- Revocación de certificados: comprobación activada por defecto, con preferencia por CRL.
- **Limitación explícita**: **la firma de las listas TS 119 602 no está implementada**; una lista descargada se acepta sin comprobar la firma. (La de la lista de listas sí se verifica.)
- Un único archivo de test.

**Generación de listas y registro [V]**
- `trustedlist-manager-py` (estancado desde jun-2025): genera XML TS 119 612 y lo firma con XAdES. **No genera** las listas JSON TS 119 602 que el validador ya consume. Sin tests.
- `relyingparty-registration-py`: registra partes usuarias paso a paso (Ley, persona, entidad, proveedor, parte usuaria, uso previsto, credencial) y emite el **certificado de acceso** (conforme a ETSI TS 119 411-8) y el **certificado de registro** (ETSI TS 119 475, en JWT y CWT, con índice de estado y caducidad de 6 meses). Soporta intermediarios. Búsqueda pública. Sin tests.
- `trustprovider-signer-java`: **no genera listas**; es un proveedor de firma remota (RSSP según CSC).
- **Certificate Transparency (Topic 55)**: no hay rastro en estos repos [V, por ausencia].

**Los RFC del consorcio EWC [V]**: el repositorio `eudi-wallet-rfcs` no es de la Comisión sino de un piloto. El RFC 002 (aprobado) describe cómo un verificador usa OpenID4VP, pero cita el **borrador 23** y no la versión final 1.0. El RFC 012 define una lista de confianza del consorcio y avisa de que no es autoritativa fuera de los pilotos.

### 5.7 La firma remota (RQES) [V]
- Cinco repos, todos **pre-1.0 con aviso de no producción**: `rqes-core` (v0.7.0), `rqes-ui` (v0.4.1), `rqes-csc-kt` (v0.8.0), `sca-java` (v0.3.0), `podofo` (fork C++).
- Protocolo **CSC API v2.2**. Hay dos variantes: la propia wallet hace de aplicación de firma, o lo hace un componente del sitio de la parte usuaria.
- Flujo en la cartera: se consulta al prestador, el usuario se autentica **en el navegador** con OAuth2 (con PAR opcional), la wallet calcula el hash **en el dispositivo**, pide autorización por credencial con `authorization_details` ligado a esos hashes, y firma con `signHash`.
- **En la cartera solo funciona PDF/PAdES** (por PoDoFo, en C++). Los formatos CAdES, XAdES y JAdES solo están declarados en enumeraciones; en el lado de la parte usuaria, DSS sí cubre más formatos.
- Pendientes: `signDoc` no está implementado, y hay un `TODO` para comparar lo autorizado con lo solicitado.
- El módulo `documentretrieval` imita un flujo de presentación, pero su README avisa de que **no está en la especificación CSC y puede eliminarse**.
- `sca-java` tiene **un solo test**.

---

## 6. Tres flujos recorridos en el código real

### 6.1 Emisión
1. La app abre una oferta y `openid4vci-kt` resuelve la oferta y las *metadata* del emisor y del servidor de autorización.
2. Prepara la solicitud: genera PKCE, la envía por **PAR**, y se autentica con **atestación de cliente (ABCA)**, para lo cual `wallet-core` pide la WIA al Wallet Provider (`POST /wallet-instance-attestation/jwk`).
3. Obtiene el código y lo canjea con **DPoP**.
4. Pide el `c_nonce`, construye la prueba de posesión (con la atestación de claves, que pide a `POST /key-attestation/jwk-set`) y llama al endpoint de credenciales, por lote si la política lo permite.
5. El emisor valida la prueba y la atestación, obtiene los atributos, asigna índices de estado (si su política lo permite) y firma.
6. La wallet guarda las credenciales en el `SecureArea` y notifica la aceptación.

**Hallazgo [V]**: en el paso 2, la app de referencia envía al Wallet Provider **solo la clave pública (JWK)**, sin la cadena de atestación de Android ni Play Integrity. Por eso, aunque el Wallet Provider tuviera la validación activada, en este flujo no podría verificar el dispositivo.

### 6.2 Presentación
1. El verificador crea una solicitud firmada con su certificado de acceso.
2. La wallet la resuelve con `openid4vp-kt`: autentica al verificador según el prefijo de `client_id` y comprueba su cadena con la política de confianza inyectada.
3. La wallet empareja la consulta DCQL con sus credenciales (esto lo hace `wallet-core`, no la librería), pide consentimiento y construye la respuesta con la prueba de posesión.
4. La respuesta viaja cifrada (`direct_post.jwt`) al verificador, que valida emisor, vigencia, estado y posesión.
5. En proximidad, el mismo contenido viaja por ISO 18013-5 con QR/NFC para el enlace y BLE para los datos.

### 6.3 Revocación y estado
- **Credencial**: el emisor marca el bit en la lista de estado. En el emisor de referencia solo ocurre para credenciales **con índice** y de forma derivada (ver §5.5).
- **Wallet / claves**: la WIA y la atestación de claves llevan su propio índice; si una se invalida, el emisor revoca lo emitido bajo ella en su siguiente barrido.
- **Consulta**: la cartera comprueba periódicamente con statium; el verificador comprueba al presentar. **La firma de la lista hay que verificarla con un verificador inyectado**, y statium no trae caché.
- **Lo que falta [V]**: no existe un punto en el Wallet Provider para revocar una unidad; no hay revocación a iniciativa del emisor en el emisor de referencia; y el servicio de estado es un único Flask con clave de API estática y solo dos estados.

---

## 7. La brecha entre diseño y realidad

Esta tabla resume los hallazgos que más importan para entender el estado real. Todos son **[V]** en código, salvo donde se indica.

| # | Diseño (norma / ARF) | Realidad en el código de referencia |
|---|---|---|
| 1 | Nivel de garantía **alto** y WSCD certificado | Solo Android Keystore/StrongBox; no hay WSCD remoto ni eID. El nivel *high* no es demostrable con el código por sí solo **[I]** |
| 2 | El Wallet Provider atesta que el dispositivo es legítimo | La app envía solo un JWK, sin atestación de plataforma; y por defecto el servicio acepta todo (modo *mock* declarado) |
| 3 | La atestación de claves refleja el nivel real de seguridad | El Wallet Provider declara **siempre** `iso_18045_high`, sin medirlo (`IssueKeyAttestation.kt:245-246`) |
| 4 | La autenticación del usuario protege las claves | En la configuración *demo* de la app, `userAuthenticationRequired = false`: el PIN solo bloquea la interfaz y no está ligado a las claves |
| 5 | El PID debe poder revocarse por su emisor (CIR 2024/2977 art. 5) | Con la política por defecto, **el PID no recibe índice de estado**; no se puede revocar directamente |
| 6 | Revocación a iniciativa del emisor | Inexistente en el emisor Kotlin; en el Python solo a petición del usuario o por la wallet |
| 7 | Las partes de confianza son las de las listas oficiales | Verificador y emisor, sin configuración, **confían en todos**; el validador no verifica la firma de las listas TS 119 602 |
| 8 | Generar listas de confianza conformes a la última versión | El gestor solo genera XML 119 612; no genera las 119 602 que el validador ya consume; está estancado |
| 9 | Autenticación de partes usuarias con registro y límites de datos | El registro existe y emite certificados; el verificador de referencia no comprobó que valide su firma ni su cadena |
| 10 | No vinculabilidad (art. 5a(16)) | Solo mitigada por lotes de uso único; el propio ARF admite que no se elimina sin ZKP |
| 11 | Firma cualificada gratuita y por defecto | Cartera solo con PAdES; `signDoc` sin implementar; librerías pre-1.0 |
| 12 | Certificate Transparency (Topic 55) | Sin rastro en emisor, verificador ni registro |
| 13 | Sellos y credenciales seguras de producción | Claves de ejemplo versionadas, perfiles con TLS desactivado, `SECRET_KEY="dev"`, clave privada de ejemplo en un repositorio Python **[V]** |
| 14 | Verificación de estado con firma comprobada | Statium la deja a cargo del integrador y trae un `Ignore`; sin caché |
| 15 | Estándares finales | Conviven borradores (Status List 12, SD-JWT VC 13, ABCA 07, DC API en borrador) |

**Cómo leer esta tabla [I]**: ninguno de estos puntos es un "error" del proyecto en el sentido ordinario. Cada repositorio declara en su README que es una versión inicial, no apta para producción, y que muchas validaciones son simuladas por defecto. El valor de la tabla es otro: **muestra qué trabajo queda por hacer a cada Estado que quiera pasar de la referencia a un servicio real**, y por qué la certificación (art. 5c) será el verdadero filtro.

---

## 8. Cronograma: hasta fin de 2026 y después

### 8.1 Hechos [V] (de `mapa-normativo.md`, contrastados con los textos)

| Fecha | Hito |
|---|---|
| 30-abr-2024 | Se publica el Reglamento 2024/1183 (eIDAS 2.0) |
| 4-dic-2024 | Primeros cinco actos de ejecución: 2024/2977 (PID y EAA), 2979 (integridad y funciones), 2980 (notificaciones), 2981 (certificación), 2982 (protocolos). Entran en vigor a los 20 días |
| 7-may-2025 | Actos 2025/846 (correspondencia de identidad), 847 (brechas), 848 (registro de partes usuarias), 849 (lista de wallets certificadas). El art. 10 del 847 aplica desde 7-may-2026 |
| 8-abr-2026 | CIR 2026/798: alta remota |
| 19-ago-2026 | Arts. 6-9 del CIR 2025/1569 (QEAA y PuB-EAA) |
| 22-jul-2026 (entrada en vigor 11-ago-2026) | Modificaciones 2026/1730 (al registro), 2026/1731 (a 2977, 2979, 2980, 2982) y 2026/1735 |
| **24-dic-2026** | **Empiezan a aplicarse los CIR 2025/846 y 2025/848** (registro de partes usuarias) |
| 1-ene-2027 | Art. 1(3) del CIR 2026/1735 |
| 19-ago-2027 | CIR 2025/1566 y 1567 |
| 11-ago-2028 | Imagen facial del PID (ISO/IEC 39794-5) y **validación por la wallet de los certificados de registro** (CIR 2026/1731) |
| 21-may-2030 | Primer informe de la Comisión (art. 49) |

### 8.2 Derivados [I]
- **~24-dic-2026**: vence el plazo del art. 5a(1) (cada Estado ofrece una wallet). Es un cálculo (4-dic-2024 + 20 días + 24 meses), igual que el del mapa normativo; la fecha exacta debe confirmarse contra el DOUE.
- **~dic-2027**: aceptación obligatoria por privados con autenticación reforzada (art. 5f(2), 36 meses).

### 8.3 Lo que ocurre en el ARF en el último trimestre de 2026 [V]
El repositorio de discusión indica 15 temas en 2026, 11 de ellos reabiertos, con iteraciones hasta el **16-dic-2026**. Siguen abiertos: **G (ZKP), F (DC API), AC (vinculación criptográfica), AA (pagos), AB (firma), AD y AE (proximidad y liveness)**. Es decir, **las decisiones técnicas más importantes seguirán abiertas el mismo mes en que vence la obligación legal de ofrecer wallet.**

### 8.4 Estado real de los hitos a 6-nov-2026
- **Lista de wallets certificadas (art. 5d)**: **[NV]**. Ni el corpus ni el texto confirman que exista una lista publicada. Se verifica solo en el DOUE.
- **Esquema europeo de ciberseguridad (art. 5c(2))**: **[V]** según el ARF §7.2-7.3, el esquema aplicable sigue siendo "candidato"; solo EUCC está disponible, mientras EUCS y EU5G figuran como próximos. Hasta entonces rigen esquemas nacionales transitorios.
- **Esquemas nacionales**: **[V]** el ARF §7.2 dice que se establecerán "a tiempo para poner a disposición las wallets antes de fines de 2026". El estado real por país **[NV]**.

---

## 9. ¿Logrará los objetivos? Evaluación

Criterio: cada objetivo del §2 se valora por tres factores: (a) si depende de decisiones jurídicas o técnicas, (b) si hay norma y código que lo sostengan, (c) si hay riesgos documentados. El semáforo es **mi valoración [I]** apoyada en los hechos citados.

| Objetivo | Valoración | Fundamento |
|---|---|---|
| **Obligación de ofrecer wallet (5a(1))** | 🟢 Probable en lo formal | Hay actos de ejecución, ARF y código de referencia. Lo que se ofrezca podrá variar mucho entre Estados |
| **Gratuidad y voluntariedad (5a(13), 5a(15))** | 🟢 | Son decisiones jurídicas, no técnicas |
| **Reconocimiento legal de atestaciones (45b)** | 🟢 | Efecto de ley |
| **Registro de partes usuarias (5b)** | 🟡 | El CIR 2025/848 aplica desde dic-2026 y existe un registro de referencia. Pero la **validación de los certificados de registro por la wallet se pospone a ago-2028** (CIR 2026/1731), y el verificador de referencia no demuestra validarla |
| **Certificación a tiempo y nivel alto (5a(11), 5c)** | 🟠 Riesgo alto | Esquema europeo aún candidato. El código de referencia no demuestra garantía alta (§7, filas 1-4). Riesgo de heterogeneidad entre Estados y de lista incompleta en dic-2026 |
| **No seguimiento y no vinculabilidad (5a(16))** | 🟠 Parcial | Mitigada por lotes y organización; el ARF admite que sin ZKP no se elimina, y la ZKP no está elegida ni soportada por el hardware |
| **Pseudónimos (5a(4)(b))** | 🟠 | Topic abierto y revisado; **no encontré implementación en la app de referencia** |
| **Firma cualificada gratuita (5a(5)(g))** | 🟠 | Cartera solo con PDF/PAdES, librerías pre-1.0, `signDoc` sin implementar, Topic AB abierto |
| **Panel de transparencia y borrado (5a(4)(d))** | 🟡 | Hay pantalla de transacciones y registro; el flujo de petición de borrado a la parte usuaria está en el ARF (Topic 48), sin evidencia en el código analizado |
| **Revocación de PID por el emisor (CIR 2024/2977 art. 5)** | 🟠 | El emisor de referencia no asigna estado al PID con la política por defecto, y no hay revocación a iniciativa del emisor |
| **Aceptación obligatoria (5f(2), 5f(3))** | 🟡 | Depende de la voluntad del usuario. Sin masa crítica, las partes usuarias integran lo mínimo **[I]** |
| **Interoperabilidad entre wallets, emisores y verificadores** | 🟡 | Estándares abiertos y código común ayudan, pero se apoyan en borradores que siguen cambiando |
| **Despliegue amplio hacia 2030** | ❔ **[NV]** | La cifra de 80 % no consta en la norma; no hay datos de adopción en las fuentes |

### 9.1 Factores que más pesan [I]

1. **La confianza se trasladó al integrador.** El diseño modular es acertado, pero casi todas las validaciones críticas (firma de listas, cadena del emisor, atestación del dispositivo) quedan en "inyecte su propia política". Si cada Estado lo hace distinto, la interoperabilidad real depende de la certificación.
2. **El hardware manda.** El nivel alto depende de que el dispositivo ofrezca un WSCD fiable. Con wallets móviles, eso significa depender del fabricante del sistema operativo o de un HSM del proveedor.
3. **El calendario legal va por delante del técnico.** Se exige la wallet en dic-2026 mientras ZKP, DC API, vinculación, pagos y firma siguen abiertos.
4. **La privacidad es una promesa en tensión.** El texto exige no vinculabilidad, pero la técnica disponible solo la mitiga. Ahí está el riesgo reputacional y jurídico más grande.
5. **La referencia no es producto.** Declara no estar lista para producción; el trabajo de endurecimiento (confianza abierta por defecto, atestaciones simuladas, claves de ejemplo) recaerá en cada proveedor.

### 9.2 Lo que sí es un logro claro [I]
- Se creó un **marco abierto y coherente** (norma, arquitectura, estándares, código) en pocos años, con ARF versionado públicamente y reabierto de forma transparente.
- La apuesta por **estándares abiertos reutilizables** (OpenID4VCI/VP, SD-JWT, ISO 18013-5, Token Status List) hace que el trabajo sirva más allá de la UE.
- La separación de actores y las **atestaciones de wallet** son una buena idea de diseño: permiten al emisor exigir garantías sin ver al usuario.

### 9.3 Conclusión
**[I]** A fines de 2026 es razonable esperar **wallets nacionales disponibles en lo formal**, pero con **grado de garantía y funcionalidad muy desigual**, y con piezas centrales (ZKP, firma cualificada completa, pagos, vinculación criptográfica) todavía en definición. Los objetivos jurídicos "blandos" (oferta, gratuidad, voluntariedad, reconocimiento legal) son los más alcanzables. Los más exigentes técnicamente (garantía alta demostrable, no vinculabilidad, privacidad frente a emisores) dependerán de la certificación, del hardware y de decisiones aún abiertas. Que el ecosistema cumpla lo que promete lo dirá, en la práctica, el primer informe de evaluación (art. 5f(5)) y el de 2030 (art. 49).

---

## 10. Qué aprender de este caso

Cuatro lecciones para un arquitecto:

1. **Legislar y construir a la vez.** La UE usó pilotos y código de referencia para llegar a la ley, no al revés. Funciona para acelerar, pero deja la norma persiguiendo estándares que cambian.
2. **La confianza es el problema difícil.** El código sabe firmar y verificar; lo difícil es decidir en quién confiar. Por eso la arquitectura gira alrededor de listas, registros y atestaciones.
3. **Privacidad y seguridad tiran en direcciones opuestas.** Las atestaciones estables dan seguridad pero crean identificadores; los lotes dan privacidad pero cuestan operación y revocación más compleja.
4. **Lee los valores por defecto.** En casi cada repo, lo que *no* configuras hace que el sistema confíe en todos o acepte todo. Eso es un patrón de diseño de referencia: favorece probar, pero es peligroso copiar.

### Ejercicios de comprensión
1. Elige un requisito del art. 5a(16) y traza, en el código, qué mecanismo lo cubre y cuál no.
2. Sigue una credencial desde `openid4vci-kt` hasta el almacén de claves e identifica dónde se aplica la política de reutilización.
3. Compara los dos emisores de referencia y decide cuál usarías para un servicio real y por qué.
4. Identifica, en el flujo de presentación, **cada punto de inyección de confianza** y qué pasaría si estuviera vacío.
5. Explica por qué la vinculación al dispositivo pasó de obligatoria a recomendada y qué se gana y se pierde.

---

## 11. Lo que este tratado no sabe

Para no prometer de más:

- **No** hay datos de adopción ni la cifra del 80 % en las fuentes **[NV]**.
- **No** se sabe qué Estados tienen wallet certificada al 6-nov-2026 ni si existe la lista del art. 5d **[NV]**.
- **No** constan críticas de la sociedad civil, criptógrafos o autoridades de datos sobre dependencia de plataformas o vinculabilidad **[NV]**. Habría que buscarlas fuera del corpus.
- **No** constan los resultados de los pilotos ni quién desarrolla los repositorios **[NV]**.
- **No se leyeron**: el servidor de autorización del emisor Python, el validador de estado ni el emisor de la variante Age Verification más allá de lo descrito.
- Los análisis de código **no ejecutaron tests**; los conteos son de archivos.
- Los requisitos ARF "no cubiertos" se contrastaron por **ausencia** en búsquedas de código, lo que no es una prueba de que no existan en otro repositorio.
- Las fechas derivadas (24-dic-2026, dic-2027) deben confirmarse contra el Diario Oficial.
- El análisis de la variante `av-app-android-wallet-ui` excluye un módulo local no oficial que se encontró sin versionar.

### Fuentes principales
- Código: `Wallet/` (Issuer, Verifier, Trust, Wallet) y `librerias/`, a fecha 6-nov-2026.
- Norma: `fuentes/regulaciones/02014R0910-20241018.txt` (arts. 5a-5f, 45b-45h, 49) y `32024R1183.txt` (considerandos 1-5, 14, 24).
- ARF: `fuentes/arf/arf-v3.0.0.txt` (§1.2-1.8, §3, §4.3-4.5, §6.1, §7.2-7.4), `fuentes/arf-repo/CHANGELOG.md` y `docs/discussion-topics/`.
- Requisitos: `fuentes/hlr-arf-3.0.0.csv` (725 HLR) y `conocimiento/ue/mapa-normativo.md`.
