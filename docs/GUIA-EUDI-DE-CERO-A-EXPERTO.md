# Guía del ecosistema EUDI Wallet: de cero a experto

Para quien llega como arquitecto junior y quiere entender el sistema completo: por qué existe, quién participa, cómo se
hablan las piezas, qué dice la norma y cómo está construido en este laboratorio (Avance).

Cómo se apoya en las fuentes: la normativa y el ARF salen del corpus local (`docs/corpus-eudi`, ARF v3.0.0 con 725
requisitos HLR, reglamentos de la Cellar y estándares ETSI/IETF/OpenID/W3C). El despliegue sale de `ECOSISTEMA.md` y de
lo que construimos y probamos en las sesiones. Las explicaciones de protocolos siguen las especificaciones citadas.
Donde algo es de prueba o simplificado en Avance, se dice.

---

## 0. Mapa de lectura

| Parte | Qué aprendes | Si te falta tiempo |
|---|---|---|
| 1. El porqué | Qué problema resuelve y qué ley lo manda | Leer |
| 2. Actores | Quién es quién y qué responsabilidad tiene | **Imprescindible** |
| 3. Anatomía de una credencial | PID, EAA, SD-JWT VC, mdoc, claims | **Imprescindible** |
| 4. Confianza | Listas de confianza, certificados, registro | **Imprescindible** |
| 5. Los flujos | Alta, emisión, presentación, revocación, firma | **Imprescindible** |
| 6. Seguridad y privacidad | WSCD, atestaciones, DPoP, privacidad | Leer |
| 7. Cómo está construido (repos) | Qué hace cada carpeta | Leer |
| 8. Cómo está desplegado en Avance | Contenedores, rutas, decisiones | Leer |
| 9. La norma, cómo consultarla | ARF, HLR, actos de ejecución | Leer |
| 10. Ruta de estudio y ejercicios | Plan de 4 semanas | Hacer |
| 11. Errores típicos de arquitecto | Lo que más se confunde | Leer |
| 12. Glosario | Siglas | Consultar |

---

## 1. El porqué

### 1.1 El problema
Hoy, para demostrar quién eres o un dato tuyo (edad, título, licencia) entregas una fotocopia, un PDF o inicias sesión con
una gran plataforma. Eso tiene tres defectos:

- **Revelas de más.** Para probar que eres mayor de edad entregas toda la cédula.
- **Dependes de un intermediario** que sabe dónde te identificas y puede rastrearte.
- **El verificador no puede estar seguro** de que el documento sea auténtico, esté vigente o sea tuyo.

### 1.2 La respuesta europea
El Reglamento (UE) **2024/1183**, llamado **eIDAS 2.0**, modifica el Reglamento 910/2014 (eIDAS 1). Obliga a cada Estado
miembro a ofrecer al menos una **Cartera Europea de Identidad Digital** (EUDI Wallet) a sus ciudadanos. El artículo 5a
regula las carteras. Las aplican actos de ejecución (CIR) publicados desde diciembre de 2024. Lista completa en
`corpus-eudi/conocimiento/ue/mapa-normativo.md`.

Principios de diseño que verás repetidos en cada decisión técnica:

1. **El usuario controla.** Sus credenciales viven en su teléfono, no en un servidor central.
2. **Divulgación selectiva.** Se entrega solo lo que se pide y el usuario lo aprueba.
3. **Minimización y no rastreo.** Que nadie, ni el emisor, pueda saber dónde usas tus credenciales.
4. **Confianza verificable.** Cada parte demuestra quién es con certificados contenidos en listas oficiales.
5. **Interoperabilidad.** Protocolos abiertos, para que cualquier cartera hable con cualquier emisor o verificador.
6. **Seguridad alta.** Las claves quedan en hardware seguro del dispositivo (nivel de garantía *High*).

### 1.3 Capas de normas (de lo general a lo concreto)

```
Reglamento eIDAS 2.0 (910/2014 modificado por 2024/1183)    ← la ley
        │
Actos de ejecución (CIR 2024/2977, 2979, 2980, 2981, 2982,  ← obligaciones concretas por tema
 2025/847, 848, 849, ...)
        │
ARF — Architecture and Reference Framework                  ← el "plano" técnico (v3.0.0 en el corpus)
        │
Estándares (OpenID4VCI/VP, SD-JWT VC, ISO 18013-5,          ← los protocolos y formatos
 ETSI TS 119 xxx, IETF Token Status List, W3C ...)
        │
Código de referencia (repos eudi-*)                         ← una implementación abierta
```

Dato clave para no confundirte: **usar el código de referencia no da certificación ni reconocimiento oficial.** El piloto
de Avance no entra en la lista de carteras certificadas por autodeclaración (ver el mapa normativo).

---

## 2. Los actores

### 2.1 Vista general (ARF, Figura 2: arquitectura de alto nivel)

```
                         ┌────────────────────────┐
                         │  Estado miembro /      │
                         │  Comisión: listas de   │
                         │  confianza, registro   │
                         └───────────┬────────────┘
                                     │ publica quién es confiable
      ┌─────────────────┐            ▼             ┌─────────────────────┐
      │ PID Provider /  │   ┌──────────────────┐   │ Relying Party       │
      │ Attestation     │──▶│  WALLET UNIT     │──▶│ (verificador)       │
      │ Provider        │   │  (del usuario)   │   │ pide y valida datos │
      │ (emisor)        │   └────────▲─────────┘   └─────────────────────┘
      └─────────────────┘            │ atestaciones (¿esta wallet es legítima?)
                              ┌──────┴────────┐
                              │ Wallet        │
                              │ Provider      │
                              └───────────────┘
              Usuario ──── controla ────▶ Wallet Unit
```

### 2.2 Cada actor, con su responsabilidad

| Actor | Qué es | Responsabilidades clave | En Avance |
|---|---|---|---|
| **Usuario** (titular) | La persona. | Aprueba cada emisión y cada presentación. | Tú, con el teléfono. |
| **Wallet Instance / Wallet Unit** | La app instalada (instance) más sus claves ya activadas en el dispositivo (unit). | Guarda credenciales, muestra qué se pide, firma pruebas de posesión. | `app-new-wallet` y `app-avance-wallet` (Android). |
| **Wallet Provider** | Quien entrega la app y garantiza que cada unidad es legítima. | Emite la **atestación de la instancia (WIA)** y de **claves (WUA)**; puede **revocar** unidades (Tema 38). | `eudi-srv-wallet-provider`. |
| **PID Provider** | Emisor de la identidad básica. | Emite el **PID**, verifica al titular contra fuentes oficiales, **revoca** (CIR 2024/2977 art. 5). | `eudi-srv-issuer-pid-registraduria`. |
| **Attestation Provider** (QEAA, PuB-EAA, EAA no cualificada) | Emisor de otros atributos. | Emite títulos, licencias, certificados; los revoca. | UIS (académico), Tránsito (mDL), Notaría. |
| **Relying Party (RP)** | Quien pide credenciales para dar un servicio. | Se registra, declara qué pide y para qué, valida y respeta el tratamiento de datos. | Los 15 verificadores (`banco`, `eps`, `gym`...). |
| **Trusted List Provider** | Publica las listas de confianza. | Mantiene las listas y la LoTL (lista de listas). | `srv-tl-publisher`. |
| **Registrar de RPs** | Registra a las partes usuarias y emite *registration certificates*. | Limita lo que cada RP puede pedir (Temas 27 y 44). | Referenciado (`eudi-srv-web-relyingparty-registration-py`). |
| **Proveedor de identificación remota (onboarding)** | Comprueba que el usuario es quien dice (documento + biometría). | Habilita la emisión del PID con garantía. | `srv-onboarding` (backend + biometría). |
| **QTSP / servicio de firma** | Prestador cualificado de firma remota. | Firma con sello cualificado a pedido de la wallet (Tema 16). | `AndesSCD` y las librerías RQES. |
| **Validador de confianza** | Servicio que comprueba cadenas contra las listas. | Responde "¿este certificado es de un actor confiable?". | `eudi-srv-trust-validator`. |

### 2.3 Cuatro ideas que desordenan al principiante

1. **El emisor no es el wallet provider.** El emisor dice *qué eres*. El wallet provider garantiza *qué aplicación*
   lo guarda. Son dos confianzas distintas y se atestan por separado.
2. **El verificador no llama al emisor.** Comprueba firmas con certificados que obtiene de las listas de confianza. El
   emisor no se entera de cada uso. Eso es la privacidad: no hay "teléfono a casa".
3. **Wallet Instance ≠ Wallet Unit.** La *instancia* es el software. La *unidad* es esa instancia ya activada, con sus
   claves protegidas, atestada por el provider y vinculada al usuario.
4. **Cada rol puede ser una organización distinta.** En Europa, el PID lo emite el Estado y el Wallet Provider puede
   ser un privado. Aquí todo corre en un laboratorio, pero los roles siguen separados a propósito.

---

## 3. Anatomía de una credencial

### 3.1 Tipos de credencial (ARF)

| Sigla | Significado | Quién la emite | Ejemplo |
|---|---|---|---|
| **PID** | Person Identification Data | PID Provider autorizado | Cédula digital: nombre, fecha de nacimiento, número, retrato. |
| **EAA** | Electronic Attestation of Attributes | Cualquier proveedor (registrado) | Carné de gimnasio. |
| **QEAA** | EAA cualificada | Prestador cualificado (QTSP) | Título certificado por un QTSP. |
| **PuB-EAA** | EAA de organismo público de fuente auténtica | Organismo público | Certificado de antecedentes, título público. |

El *rulebook* de cada tipo define qué atributos lleva y con qué nombres (Temas 3, 4 y 12 del ARF; carpeta
`eudi-doc-attestation-rulebooks-catalog` en el corpus).

### 3.2 Dos formatos, mismo contenido

| | **SD-JWT VC** | **mso_mdoc** (ISO 18013-5) |
|---|---|---|
| Base | JSON/JWT firmado | CBOR/COSE firmado |
| Divulgación selectiva | Por "disclosures" con salt: el emisor firma solo *hashes* de cada atributo y el titular entrega los valores que quiere | Por elemento: cada atributo tiene su digest en el MSO (Mobile Security Object) firmado |
| Vinculación al dispositivo | Claim `cnf` con la clave pública del titular; se prueba con un JWT de presentación (KB-JWT) | Clave de dispositivo en el MSO; se prueba con `DeviceAuth` |
| Presencial (NFC/BLE) | No es lo normal | Sí, es el formato nativo |
| Identificador del tipo | `vct` (ej. `urn:eudi:pid:1`) | `docType` (ej. `eu.europa.ec.eudi.pid.1`) |

En Avance el PID de la Registraduría se emite **en los dos formatos**. Por eso en la base hay dos tipos de credencial
por ciudadano: `urn:eudi:pid:1` (SD-JWT) y `eu.europa.ec.eudi.pid.1` (mdoc).

### 3.3 Qué contiene una credencial por dentro (SD-JWT VC, simplificado)

```
header:  { alg: ES256, x5c: [certificado del emisor ...] }        ← cadena para validar al emisor
payload: { iss: https://emisor, vct: urn:eudi:pid:1,
           iat, exp,
           cnf: { jwk: clave pública del dispositivo },           ← "esta credencial es de esa clave"
           status: { status_list: { idx: 5866, uri: ".../..." } },← dónde mirar si fue revocada
           _sd: [ hash(nombre), hash(fecha_nac), ... ],           ← atributos ocultos por defecto
           _sd_alg: sha-256 }
firma del emisor
~disclosure(nombre)~disclosure(fecha_nac)~...                      ← lo que el titular decide entregar
~KB-JWT                                                            ← prueba de posesión, ligada al verificador
```

Ideas que debes retener:

- El emisor **firma hashes**; el titular **entrega valores**. Quien verifica recalcula el hash y lo compara.
- `cnf` ata la credencial a una clave guardada en el hardware seguro. Copiar la credencial a otro teléfono no sirve.
- `status` (índice y URI) apunta a la **lista de estado**: es lo que permite revocar sin avisar a nadie.

### 3.4 Credenciales por lotes y una sola vez
Para dificultar el rastreo entre verificadores, la wallet pide **varias copias** de la misma credencial, cada una con una
clave y firma distintas, y usa una por presentación (política de reutilización del ARF, Anexo II; ETSI TS 119 472-3).
Eso explica por qué una solicitud de PID en Avance genera **50 credenciales por formato** y 50 índices de estado.

---

## 4. Confianza: cómo se sabe quién es quién

Sin esto, el resto no se entiende. Todo gira en torno a la pregunta: **"¿por qué creo que esta firma viene de una
autoridad legítima?"**

### 4.1 Piezas

| Pieza | Para qué sirve |
|---|---|
| **Certificado X.509 del emisor** | Prueba criptográfica de que esa clave pública pertenece a ese emisor. Viaja en la credencial (`x5c`). |
| **Lista de confianza (Trusted List, ETSI TS 119 612)** | Lista oficial, firmada, de prestadores y de las anclas (CA) que respaldan sus certificados. |
| **LoTL (List of Trusted Lists)** | Índice que apunta a las listas de cada país o esquema. |
| **Ancla de confianza** | La CA raíz o intermedia listada. Si la cadena del emisor llega a ella, se confía. |
| **Certificado de acceso (access certificate)** | Con él una RP se autentica ante la wallet. Lo emite una *Access CA*. |
| **Certificado de registro (registration certificate)** | Dice qué atributos puede pedir esa RP y para qué (Temas 27 y 44). |
| **Atestación de wallet (WIA / WUA)** | El Wallet Provider dice: "esta instancia/clave es legítima". |

### 4.2 Qué se valida y quién lo valida

```
  Wallet valida al EMISOR      → ¿su certificado llega a un ancla en la lista (PID, EAA...)?
  Wallet valida al VERIFICADOR → ¿su certificado de acceso es válido? ¿su registro permite lo que pide?
  Verificador valida el EMISOR → misma cadena, más la lista de estado (¿revocada?)
  Emisor valida la WALLET      → atestación del Wallet Provider (¿instancia legítima? ¿clave en hardware?)
```

Es una **malla de confianza**: nadie confía en nadie por su cara, todos confían por cadenas de certificados que acaban
en listas oficiales.

### 4.3 Cómo está montado en Avance
- `srv-tl-publisher` publica las listas (`/TL/…`, `/LOTL/…`) y el servicio de PKI (AIA, CRLDP).
- `eudi-srv-trust-validator` carga la LoTL y responde si una cadena es confiable. Tiene una raíz propia, `Civica EUDI Root CA`.
- Cada emisor tiene su propia raíz (por ejemplo `REGISTRADURIA ISSUANCE ROOT`), registrada en la lista como PID Provider
  o como emisor de EAA. Detalle en `ECOSISTEMA.md` §1.
- **Cuidado práctico:** el APK de la wallet lleva embebida la CA de desarrollo (`pki-dev/ca.crt`), que es el ancla. Si
  regeneras la PKI, el APK deja de confiar y hay que recompilarlo. Por eso no tocamos la PKI al endurecer los emisores.

---

## 5. Los flujos, paso a paso

### 5.1 Flujo A — Instalación y activación de la wallet (Temas 40 y 9)

1. El usuario instala la app (la instancia).
2. La app genera un par de claves **dentro del hardware seguro** (Android Keystore / StrongBox).
3. Pide al **Wallet Provider** una **WIA** (Wallet Instance Attestation), con prueba de que corre en un dispositivo y
   aplicación auténticos (en Android, *key attestation* de Google; en el provider de Avance se valida el nombre de
   paquete y la huella de firma).
4. El provider emite también la **WUA** (Wallet Unit Attestation): declara que la clave está en un WSCD certificado.
5. Ahora la instancia es una **unidad**: puede pedir credenciales a emisores, que aceptan su atestación.
6. Cada unidad recibe un índice en la **lista de estado del Wallet Provider**. Si se pierde o roba el teléfono, el provider
   pone ese bit en 1 y los emisores dejan de confiar en esa unidad (Tema 38, `WURevocation_*`).

### 5.2 Flujo B — Obtener el PID (identificación y emisión; Tema 10)

Se hace **una vez**, y el PID es la base de todo lo demás.

**Fase 1: probar quién eres (onboarding).** En Avance:
1. La wallet captura el documento y una selfie, y las envía a `srv-onboarding`.
2. El backend compara documento y rostro y devuelve una sesión **APPROVED** o rechazada.
3. Esa decisión es la que habilita pedir el PID. En un país real la verificación sería contra una fuente oficial.

**Fase 2: emisión por OpenID4VCI.** Los cinco pasos:

```
 Usuario/Wallet                      Emisor (Registraduría)             Keycloak (Authorization Server)
      │                                       │                                   │
 1.   │── POST /onboarding/offers ───────────▶│  (sesión aprobada + cédula)       │
      │◀── oferta: credential_offer_uri ──────│  incluye issuer_state = ticket    │
 2.   │── abre la oferta, lee metadata ──────▶│  /.well-known/openid-credential-issuer
 3.   │── autorización (PAR + PKCE + client attestation) ────────────────────────▶│
      │                                       │◀─ el AS redime el ticket ─────────│  identifica al ciudadano
      │◀──────────────── authorization code ─────────────────────────────────────│
 4.   │── token (code + DPoP) ───────────────────────────────────────────────────▶│
      │◀── access_token (ligado a la clave por DPoP) ───────────────────────────│
 5.   │── POST /credential (access_token + prueba de posesión + key attestation) ▶│ emisor
      │◀── credenciales (SD-JWT y mdoc) ──────────────────────────────────────────│
```

Qué hace cada pieza de seguridad en ese flujo:

| Mecanismo | Qué evita |
|---|---|
| **PKCE** | Que alguien robe el código de autorización y lo canjee. |
| **PAR** (Pushed Authorization Request) | Que se manipule la petición por la barra del navegador. |
| **Client attestation (WIA)** | Que una app cualquiera se haga pasar por una wallet legítima. |
| **DPoP** | Que un token robado se use desde otro dispositivo (el token queda atado a la clave). |
| **Prueba de posesión (proof)** | Que el emisor firme una credencial para una clave que el titular *no tiene*. |
| **Key attestation (WUA)** | Que la clave vinculada esté en hardware seguro y no en software copiable. |
| **Ticket de un solo uso (`issuer_state`)** | Específico de Avance: liga la oferta a la cédula ya verificada y caduca a los 10 minutos. |

En el emisor ocurre, por cada credencial pedida:
1. valida la prueba de posesión y la atestación de claves,
2. obtiene los atributos de la fuente de datos (en Avance, la tabla `cedulas`),
3. **reserva un índice en la lista de estado** (el servicio `status-list`),
4. firma la credencial con su clave de emisión,
5. guarda un registro (formato, tipo, fechas, índice, titular) para poder revocarla.

> Lección de este laboratorio: el emisor original de la UE **no asignaba índice de estado al PID** (política de reutilización
> `None`), y por eso un PID no se podía revocar. Lo corregimos. Es un buen ejemplo de por qué conviene leer el código y no
> fiarse de que "el estándar ya lo hace".

### 5.3 Flujo C — Presentar credenciales a un verificador (Temas 1, 6 y 43)

Estándar: **OpenID4VP 1.0** con consultas **DCQL** (en el corpus se anota que reemplaza a la presentation-exchange).

```
 Verificador (RP)                              Wallet                                 Usuario
      │── solicitud (QR / deep link / DC API) ──▶│                                       │
      │   { qué credenciales y atributos,        │ 1. valida certificado de acceso de    │
      │     nonce, client_id, response_uri }     │    la RP (¿confiable? ¿registrada?)   │
      │                                          │ 2. compara lo pedido con el registro  │
      │                                          │ 3. muestra: quién pide, qué, para qué │
      │                                          │──────────── pantalla de consentimiento▶│
      │                                          │◀─────────── aprueba (biometría/PIN) ───│
      │◀── vp_token (credenciales con solo ──────│
      │    los atributos aprobados + KB-JWT      │
      │    firmado con nonce y audiencia)        │
      │ 4. valida firma del emisor, cadena       │
      │    contra la lista de confianza,         │
      │    vigencia y bit de revocación          │
      │ 5. valida la prueba de posesión          │
      ▼  concede el servicio                      │
```

Puntos finos que un experto domina:

- **El nonce y la audiencia** atan la respuesta a *esa* solicitud: una respuesta capturada no sirve en otra sesión.
- **El mismo flujo existe en presencial** con ISO 18013-5 (NFC o BLE) y con la **Digital Credentials API** del navegador.
- **Divulgación selectiva real:** si la RP pide "mayor de 18", lo ideal es entregar solo ese dato derivado, no la fecha de
  nacimiento. El ARF contempla también **pruebas de conocimiento cero** (Tema 53) para casos más exigentes.
- **Pseudónimos (Tema 11):** una RP puede pedir un identificador estable *por RP*, sin que dos RP puedan correlacionar.
- **Panel de transparencia (Tema 19):** la wallet debe guardar un historial de lo que compartió, que el usuario puede ver.
- En Avance, hay **15 verificadores** (banco, EPS, aerolínea, migración...) que son instancias del mismo software de
  verificación con distinta política.

### 5.4 Flujo D — Revocación (Tema 7 y 38)

Cuándo y cómo se revoca depende de qué se revoca:

| Qué | Quién | Mecanismo | Norma |
|---|---|---|---|
| Una **credencial** (PID o atestación) | **Su emisor** | Bit en la *Token Status List* del emisor | CIR 2024/2977 art. 5 |
| Una **Wallet Unit** | El **Wallet Provider** | Bit en la lista de estado del provider | Tema 38 |
| **Claves** de la wallet | El provider | Estado de la clave (WUA) | Tema 9 |
| Un **RP** | El registrador / autoridad | Revocación del certificado de registro | Temas 27 y 44 |

Cómo funciona la **Token Status List** (IETF, draft-21 en el corpus):

```
  Emisor: reserva índice N  ──▶  firma la credencial con status { idx: N, uri: U }
  Emisor: para revocar       ──▶  pone el bit N en 1 en la lista de la URI U
  Lista: se publica como JWT comprimido (1 bit por credencial, miles en pocos KB)
  Verificador: baja la lista (cacheable), lee el bit N  ──▶  0 = vigente, 1 = revocada
```

Por qué es buen diseño: la lista es **la misma para todos**, así que descargarla no revela *qué credencial* consultas
(privacidad del rebaño, VCR_18). El emisor no ve quién verifica.

Reglas de la UE sobre la revocación del PID y otras atestaciones (CIR 2024/2977 art. 5):
- solo el **emisor** revoca; motivos válidos: petición del usuario, wallet revocada, política del emisor;
- **debe avisar al usuario** con el motivo, dentro de las 24 h;
- es **irreversible**;
- el emisor debe mantener el registro y la información de estado accesibles.

En Avance, esto es lo que implementamos: botón con confirmación SÍ/NO, motivo, bloqueo opcional de nueva emisión, aviso
pendiente con plazo de 24 h, y el registro de la credencial **se marca**, no se borra. Detalle completo en
`docs/REVOCACION.md`.

### 5.5 Flujo E — Firmar documentos (Tema 16, y QES)
La wallet puede ayudar a firmar un documento con **firma electrónica cualificada** (QES):
1. La RP o el servicio de firma (QTSP) presenta el documento y los hash a firmar (protocolo **CSC** con RQES).
2. La wallet autentica al usuario y obtiene su **credencial de firma** en el QTSP.
3. El QTSP firma remotamente con una clave cualificada que el usuario controla.

Las librerías `eudi-lib-android-rqes-*`, `eudi-lib-jvm-rqes-csc-kt` y `eudi-srv-web-rpcentric-signer-sca-java` son esta
parte. En Avance está `AndesSCD` como QTSP de laboratorio. Ver `docs/PLAN-AUTENTICAR-Y-FIRMAR.md`.

### 5.6 Otros flujos que existen (para saber que están)
- **Migración a otra wallet** (Tema 34) y **recuperación** (`docs/RECUPERACION-WALLET-ARF.md`).
- **Borrado de credenciales** (Tema 51) y **solicitud de borrado de datos a una RP** (Tema 48).
- **Denunciar una solicitud sospechosa** (Tema 50).
- **Intermediarios de RP** (Tema 52), **wallets para personas jurídicas** (Tema 28), **representación** (Tema 29).
- **Interacción entre wallets** (Tema 30) y **autenticación fuerte para pagos** (Tema 20).

---

## 6. Seguridad y privacidad: el modelo mental

### 6.1 Dónde viven las claves
La clave privada del titular nunca sale del **WSCD** (Wallet Secure Cryptographic Device): Keystore/StrongBox, eSIM o una
tarjeta. El CIR 2024/2981 (anexo IV) pide nivel *High*. En el laboratorio de Avance, la wallet valida en el provider el
**paquete y la firma de la app**, y el provider es el que atesta; hay un informe sobre esto en
`corpus-eudi/conocimiento/proyecto/informe-keystore-android.md`.

### 6.2 Las tres atestaciones que más se confunden

| Atestación | Quién la emite | Qué afirma | Quién la usa |
|---|---|---|---|
| **WIA** (Wallet Instance Attestation) | Wallet Provider | "Esta instancia de app es auténtica" | El emisor, al autenticar la *aplicación cliente* |
| **WUA** (Wallet Unit Attestation) / key attestation | Wallet Provider | "Estas claves están en hardware certificado" | El emisor, antes de firmar una credencial vinculada a esa clave |
| **Credencial (PID/EAA)** | El emisor | "Estos atributos son del titular" | El verificador |

### 6.3 Privacidad: qué protege qué

| Amenaza | Defensa |
|---|---|
| El verificador pide más de lo necesario | Registro de la RP (limita lo pedible) + pantalla de consentimiento + divulgación selectiva |
| Dos verificadores comparan notas | Credenciales de un solo uso (lotes) + pseudónimos por RP |
| El emisor sabe dónde usas la credencial | No hay "llamada a casa"; lista de estado pública y agregada |
| Un token robado | DPoP (token ligado a clave) |
| Una credencial copiada | `cnf` + KB-JWT (prueba de posesión con nonce) |
| Un teléfono robado | Revocación de la unidad por el Wallet Provider + biometría/PIN |

### 6.4 Lo que NO resuelve el sistema (para no prometer de más)
- No impide que un verificador **guarde lo que le diste**; solo limita lo que puede pedir y deja trazas de denuncia.
- La lista de estado, al cachearse, hace que una revocación tarde en verse; es un compromiso de privacidad y disponibilidad.
- Un emisor mal hecho o malicioso emite credenciales falsas pero *firmadas*; por eso existe el registro y la lista de confianza.

---

## 7. Cómo está desarrollado: los repositorios

Carpeta `Wallet/` (código de referencia de la UE o variantes) y proyectos de `Avance/` (despliegue adaptado).

### 7.1 Mapa por rol

| Carpeta | Lenguaje | Qué hace | Se relaciona con |
|---|---|---|---|
| **Wallet/eudi-app-android-wallet-ui** | Kotlin (Android) | UI de referencia de la wallet | Usa `eudi-lib-android-wallet-core` |
| **librerias/eudi-lib-android-wallet-core** | Kotlin | Núcleo: claves, almacén de documentos, emisión y presentación | Usa `openid4vci-kt`, `openid4vp-kt`, `sdjwt-kt` |
| **librerias/eudi-lib-android-wallet-document-manager** | Kotlin | Gestión de documentos | Parte del core |
| **librerias/eudi-lib-jvm-openid4vci-kt** | Kotlin | Cliente **OpenID4VCI** (pedir credenciales) | Wallet core |
| **librerias/eudi-lib-jvm-openid4vp-kt** | Kotlin | **OpenID4VP** (presentar), con DCQL | Wallet core y verificador |
| **librerias/eudi-lib-jvm-sdjwt-kt** | Kotlin | Crear y verificar **SD-JWT** | Emisor, wallet, verificador |
| **librerias/eudi-lib-kmp-statium** | Kotlin Multiplatform | Leer y verificar **Token Status List** | Verificadores y wallet |
| **librerias/eudi-lib-jvm-presentation-exchange-kt** | Kotlin | Presentation Exchange (obsoleto: OpenID4VP 1.0 usa DCQL) | Histórico |
| **Wallet/eudi-srv-wallet-provider** | Kotlin (Ktor) | **Wallet Provider**: WIA, WUA, estado de unidades | Wallets y emisores |
| **Issuer/eudi-srv-pid-issuer** | Kotlin (Spring WebFlux) | **Emisor** OpenID4VCI de PID/mDL (hexagonal) | Authorization Server, status-list, wallet |
| **Issuer/eudi-srv-statuslist-py** | Python (Flask) | Servidor de **listas de estado** (`/take`, `/get`, `/set`) | Emisores y verificadores |
| **Issuer/eudi-srv-web-issuing-eudiw-py** y su frontend | Python | Emisor alternativo (demo) en Python | Referencia |
| **Verifier/eudi-srv-verifier-endpoint** | Kotlin (Ktor) | Backend verificador: crea solicitudes y valida respuestas | Wallets |
| **Verifier/eudi-web-verifier** | Angular | Interfaz web del verificador | Verifier endpoint |
| **Verifier/eudi-lib-android-verifier-core** y la UI multiplataforma | Kotlin | Verificador **móvil/presencial** | Wallets (ISO 18013-5) |
| **Trust/eudi-srv-trust-validator** | Kotlin | Valida cadenas contra la **LoTL** y listas ETSI | Wallets, emisores, verificadores |
| **Trust/eudi-srv-web-trustedlist-manager-py** | Python | Alta y edición de **listas de confianza** | Publicador |
| **Trust/eudi-srv-web-relyingparty-registration-py** | Python | **Registro de RPs** y certificados de registro | Verificadores |
| **Trust/eudi-srv-web-trustprovider-signer-java** | Java | Firma de listas y servicios de confianza | Publicador |
| **Trust/eudi-wallet-rfcs** | Markdown | Borradores técnicos del programa (por ejemplo, RFC 002 de presentación) | Consulta |
| **librerias/eudi-lib-*rqes*, eudi-srv-web-rpcentric-signer-sca-java** | Kotlin/Java | **Firma remota cualificada** (CSC) | Wallet y QTSP |
| **librerias/eudi-lib-podofo** | C++ | Manipulación de PDF para firma | RQES |

### 7.2 Patrones de diseño que verás en el código

- **Arquitectura hexagonal** (en `eudi-srv-pid-issuer` y los forks): `domain/` (modelo), `port/input` (casos de uso),
  `port/out` (dependencias externas) y `adapter/` (web, base de datos, servicios). Si quieres cambiar de dónde salen los
  datos, cambias un adaptador. Así hicimos que el PID salga de la tabla `cedulas` (`GetPidDataFromRegistraduria`).
- **Errores como tipos** con **Arrow** (`Raise`/`either`): los casos de error de negocio son valores, no excepciones.
- **Reactivo**: Spring WebFlux y coroutines de Kotlin; acceso a base de datos con R2DBC.
- **Configuración por propiedades** (`application.properties` y variables `ISSUER_*`): casi todo se enciende o apaga ahí.
- **Una librería por protocolo**: el emisor, la wallet y el verificador comparten `sdjwt-kt`. Si dominas esa librería,
  entiendes las tres puntas.

### 7.3 Cómo se relacionan (flujo de dependencias de código)

```
 wallet-ui ─▶ wallet-core ─▶ openid4vci-kt ─┐
                          ├─▶ openid4vp-kt ─┼─▶ sdjwt-kt
                          └─▶ statium        │
 pid-issuer ─────────────────────────────────┤
 verifier-endpoint ──────────────────────────┘
```

---

## 8. Cómo está desplegado en Avance (laboratorio)

Resumen; la fuente completa es `ECOSISTEMA.md`.

### 8.1 Topología

```
 Internet ─▶ proxy público (190.29.100.206, cert comodín)
              └─▶ nw-nginx (único gateway)  ─▶ red docker "avance-eudi"
                    ├─ wallet-provider        ├─ onboarding + biometría
                    ├─ haproxy-reg  (issuer PID + Keycloak /idp-reg)
                    ├─ haproxy-uis  (issuer académico + Keycloak /idp-uis)
                    ├─ haproxy-tra  (issuer mDL Tránsito + Keycloak /idp-tra)
                    ├─ notaría (issuer)       ├─ status-list-* (uno por emisor)
                    ├─ tl-publisher + PKI     ├─ trust-validator
                    └─ 15 verificadores       └─ panel de control
```

### 8.2 Decisiones de arquitectura que conviene entender

| Decisión | Por qué |
|---|---|
| **Una lista de estado por emisor** | El emisor es responsable de su estado; cada uno administra el suyo (CIR 2024/2977 art. 5; también se admite delegarlo a un tercero). |
| **Un solo Wallet Provider** para varias apps | Misma garantía para `co.org.avance.wallet.dev` y `co.org.newwallet.app.dev`. |
| **Un solo gateway** | Un punto de entrada y de certificados; los haproxy internos solo reescriben rutas. |
| **Un solo trust validator y un solo onboarding** | Son transversales; no pertenecen a una wallet. |
| **URL pública = URL que enruta el proxy** | Regla de oro: las URL del metadata deben coincidir exactamente con las rutas del borde. |
| **Dominio de producción bloqueado** | Si algo "se escapa" a producción, falla por conexión, no en silencio. |
| **Dos Keycloak comparten cookies** | El borde público reescribe el `Path` a `/`; por eso el panel alterna una consola a la vez. |

### 8.3 Lo que es de laboratorio (honestidad)
- La verificación del onboarding es de prueba, no contra la Registraduría real.
- Las contraseñas de desarrollo, las llaves de API por defecto y la PKI de laboratorio deben cambiarse antes de cualquier uso real.
- No hay entrega automática de avisos de revocación (no hay correo ni SMS integrado); quedan pendientes de entrega.
- El piloto no está certificado ni figura en listas oficiales de carteras.

---

## 9. La norma y cómo consultarla

### 9.1 El ARF
Es el "plano" que une ley y código. Se organiza en dos partes de requisitos (HLR: *High-Level Requirements*):

- **Reglas de todo el ecosistema** (*Ecosystem-Wide Rules*).
- **Requisitos por actor** (*Actor-Specific Requirements*).

Cada HLR tiene un identificador (`VCR_03`, `OIA_03`, `WURevocation_*`...) y una palabra normativa: **SHALL** (obligatorio),
**SHOULD** (recomendado), **MAY** (opcional). El corpus tiene 725 HLR (558 SHALL).

Temas con más requisitos (los que más pesan al diseñar):

| Tema | Materia | HLR aprox. |
|---|---|---|
| 10 | Emisión de PID/atestaciones | ~110 |
| 27 | Registro de proveedores y partes usuarias | ~48 |
| 9 | Atestación de claves e instancia | ~47 |
| 31 | Notificación y publicación de proveedores | ~44 |
| 40 | Instalación, activación y gestión de la wallet | ~34 |
| 11 | Pseudónimos | ~32 |
| 7 | Revocación y comprobación | ~29 |
| 1 | Acceso a servicios en línea | ~31 |
| 12 | Rulebooks de atestaciones | ~39 |
| 44 | Certificados de registro | ~34 |

### 9.2 Cómo preguntar al corpus (herramientas del repo)
```bash
cd /home/geovani/Workspace/Avance/docs/corpus-eudi
python3 -I scripts/hlr.py ...          # consulta de requisitos HLR (ver su ayuda)
python3 -I scripts/buscar.py "revocación" -f norma -d 32024R2977   # busca en el texto de una norma
```
Útil para citar con precisión (acto, artículo, HLR) en vez de inventar numeración.

### 9.3 Línea de tiempo que debes recordar
- 2024-04-30: se publica el Reglamento 2024/1183 (eIDAS 2.0).
- 2024-12-04: primeros cinco actos de ejecución (2977, 2979, 2980, 2981, 2982).
- 2025-05-07: actos sobre brechas (2025/847), registro de RPs (2025/848), lista de carteras (2025/849).
- 2026-04: alta remota (2026/798). 2026-07: modificaciones (2026/1730, 1731, 1735).
- **2026-12-24**: arranca la aplicación de varias reglas (por ejemplo el registro de RPs).
- Los Estados deben ofrecer cartera dentro de 24 meses desde la entrada en vigor de los actos de ejecución.

(Las fechas exactas por obligación están en `mapa-normativo.md`; verifica allí antes de citarlas.)

---

## 10. Ruta de estudio (4 semanas) con ejercicios en este laboratorio

**Semana 1: conceptos y confianza**
- Leer §1–§4 de esta guía y `docs/EXPLICACION-ECOSISTEMA.md`.
- Ejercicio: abre `https://civica-desarrollo.avance.org.co/pid-registraduria/.well-known/openid-credential-issuer` y
  localiza: tipos de credencial ofrecidos, formatos, algoritmos y endpoints. Es el "menú" del emisor.
- Ejercicio: descarga una lista de estado pública (`/status-list/pid-registraduria/token_status_list/...`) y decodifícala
  (hicimos un script de 10 líneas con `zlib`). Calcula cuántos bits hay.

**Semana 2: emisión**
- Leer el flujo B y los repos `openid4vci-kt` y `eudi-srv-pid-issuer`.
- Ejercicio: sigue en el log del issuer una solicitud completa (`docker logs pid-issuer-reg`): oferta, token,
  credencial. Identifica cada paso del diagrama.
- Ejercicio: mira en `postgres-reg` la tabla `issued_credential` y relaciona cada columna con el flujo.

**Semana 3: presentación y revocación**
- Leer el flujo C y D, `openid4vp-kt` y `eudi-srv-verifier-endpoint`.
- Ejercicio: presenta el PID en un verificador; luego revoca con `./revocar.sh --si registraduria <cédula>`; comprueba que
  el bit pasa a 1 en la lista pública y que el verificador rechaza.
- Ejercicio: revisa el aviso de revocación en el panel y relaciona el plazo de 24 h con el artículo 5.

**Semana 4: norma y arquitectura**
- Elige 5 HLR del tema 10 y 5 del tema 7 con `hlr.py`; para cada uno, ubica **dónde** lo cumple (o no) el código.
- Lee `corpus-eudi/conocimiento/proyecto/resumen-piloto.md` y la matriz de brechas.
- Entregable: un diagrama propio del flujo completo (usa los de esta guía como borrador) y una lista de 5 diferencias entre
  este laboratorio y un despliegue real.

---

## 11. Errores típicos de un arquitecto que empieza

1. **Pensar que el verificador consulta al emisor.** No: valida firmas y listas públicas.
2. **Confundir revocar con borrar.** Revocar marca la credencial (bit en la lista y registro con fecha/motivo); no toca los
   datos del ciudadano en el emisor. Son operaciones independientes.
3. **Confundir quién revoca qué.** El emisor revoca credenciales; el Wallet Provider revoca unidades de wallet.
4. **Asumir que el estándar lo hace todo.** Ejemplo real: el PID del emisor de referencia no tenía índice de estado.
5. **Olvidar la confianza.** Un SD-JWT bien firmado por una clave desconocida no vale nada si no llega a un ancla de la lista.
6. **Ignorar el hardware.** Sin WSCD o key attestation no hay vinculación fuerte; es un requisito, no un adorno.
7. **Mezclar PKI y TL.** La PKI emite certificados; la lista de confianza dice cuáles anclas se aceptan para cada propósito.
8. **Cambiar URLs sin mirar el proxy.** Las URL del metadata deben ser las que enruta el borde.
9. **Regenerar la PKI "para arreglar algo".** Rompe el ancla embebida en el APK de la wallet.
10. **Confundir EAA con QEAA con PuB-EAA.** Cambian el nivel legal, quién puede emitirlas y la lista donde se registra el emisor.
11. **Prometer revocación instantánea.** Depende de cuánto cachee cada verificador la lista.
12. **Tratar el piloto como certificado.** No lo es, y reutilizar el código de referencia no lo hace conforme.

---

## 12. Glosario

| Sigla | Significado |
|---|---|
| **ARF** | Architecture and Reference Framework: el plano técnico del ecosistema. |
| **HLR** | High-Level Requirement: requisito numerado del ARF. |
| **PID** | Person Identification Data: la identidad básica. |
| **EAA / QEAA / PuB-EAA** | Atestación electrónica de atributos / cualificada / de organismo público. |
| **WIA / WUA** | Atestación de instancia / de unidad (claves) de la wallet. |
| **WSCD** | Dispositivo criptográfico seguro donde viven las claves. |
| **RP** | Relying Party: la parte que verifica (verificador). |
| **TL / LoTL** | Lista de confianza / lista de listas (ETSI TS 119 612). |
| **QTSP** | Prestador cualificado de servicios de confianza. |
| **OpenID4VCI** | Protocolo para **emitir** credenciales a la wallet. |
| **OpenID4VP** | Protocolo para **presentar** credenciales a un verificador. |
| **HAIP** | Perfil de alta garantía de interoperabilidad (OpenID). |
| **DCQL** | Lenguaje de consulta de credenciales de OpenID4VP. |
| **SD-JWT VC** | Credencial JWT con divulgación selectiva. |
| **mdoc / MSO** | Credencial ISO 18013-5 en CBOR / su objeto de seguridad firmado. |
| **KB-JWT** | JWT de vinculación que prueba posesión al presentar. |
| **DPoP** | Token ligado a la clave del cliente. |
| **PKCE / PAR** | Protección del código de autorización / petición de autorización enviada por canal seguro. |
| **Token Status List** | Lista comprimida de bits que marca credenciales revocadas. |
| **QES / RQES** | Firma electrónica cualificada / firma remota cualificada. |
| **CSC** | Protocolo Cloud Signature Consortium para firma remota. |
| **CIR** | Reglamento de ejecución de la Comisión. |
| **OVR** | Alta remota (verificación remota de identidad). |

---

## 13. Dónde seguir leyendo (todo local)

| Qué | Dónde |
|---|---|
| Roles, URL y decisiones de Avance | `Avance/ECOSISTEMA.md` |
| Explicación para público general | `Avance/docs/EXPLICACION-ECOSISTEMA.md` |
| Revocación completa | `Avance/docs/REVOCACION.md` |
| Recuperación de wallet | `Avance/docs/RECUPERACION-WALLET-ARF.md` |
| Firma y autenticación | `Avance/docs/PLAN-AUTENTICAR-Y-FIRMAR.md` |
| Informe y consolidación del ecosistema | `Avance/INFORME-ECOSISTEMA-EUDI-2026-10-20.md`, `CONSOLIDACION-ECOSISTEMA-2026-10-20.md` |
| Mapa de normas UE | `corpus-eudi/conocimiento/ue/mapa-normativo.md` |
| Resumen del piloto y brechas | `corpus-eudi/conocimiento/proyecto/resumen-piloto.md`, `colombia/matriz-brechas.md` |
| ARF completo | `corpus-eudi/fuentes/arf/arf-v3.0.0.pdf` |
| Estándares (OpenID, IETF, ETSI, W3C) | `corpus-eudi/fuentes/estandares/` |
