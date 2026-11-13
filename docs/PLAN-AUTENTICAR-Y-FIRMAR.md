# Plan: botones **Autenticar** y **Firmar** de la New Wallet

Fecha: 2026-10-22 · Estado: borrador para decisión · Alcance: `app-new-wallet` y el ecosistema EUDI de Avance.

> **Cómo leer este documento.** Lo que dice "El código hace…" está verificado en el repositorio. Lo que dice
> "La intención de diseño…" es una **inferencia** a partir del código, los textos de la wallet y la arquitectura
> EUDI: no existe un documento del diseñador que la confirme. Lo marcado **(sin verificar)** no se ha probado.

---

## 1. Resumen en cinco líneas

1. **Autenticar** = *demostrar quién eres o compartir datos de tus credenciales* con quien te lo pide (en persona o en línea). No firma nada.
2. **Firmar** = *poner tu firma electrónica en un documento* (contrato, carta, formulario) eligiéndolo del teléfono o recibiéndolo de un servicio por QR.
3. Autenticar en línea **ya funciona** con los verifiers (banco, discoteca, contratación, EPS). En persona y Firmar **no están probados** en Avance.
4. Firmar necesita un **prestador de firma** que hoy no existe en el ecosistema: la wallet apunta al servidor de pruebas de la UE.
5. La decisión central es **quién respalda las firmas**. La propuesta es que sea **Andes SCD** (ver §4), con la firma etiquetada como *electrónica*, no "cualificada".

---

## 2. Qué hace cada botón

Evidencia: `dashboard-feature/.../ui/home/HomeViewModel.kt`, `HomeScreen.kt`, `resources-logic/.../strings.xml`, y capturas de pantalla del 2026-10-22 (14:39–14:40).

| Botón | Opción | El código hace… | La intención de diseño (inferida) |
|---|---|---|---|
| Autenticar | **En persona** ("muy cerca") | Comprueba que el Bluetooth esté activo y abre `ProximityScreens.QR` con `PresentationMode.Ble`. | Presentación por proximidad (ISO/IEC 18013-5): un lector cercano pide datos, sin internet. Ej.: puerta de una discoteca, un agente. |
| Autenticar | **En línea** (sitio web/servicio) | Abre el lector de QR con `QrScanFlow.Presentation`. | Presentación remota (OpenID4VP): un sitio muestra un QR y la wallet responde. Es lo que ya hacen los verifiers. |
| Firmar | **Desde el dispositivo** | Pantalla `DocumentSign`: eliges un archivo y se llama `EudiRQESUi.initiate(documentUri)`. | Firmar un documento propio con firma remota (RQES). |
| Firmar | **Escanear QR** | Lector de QR con `QrScanFlow.Signature` y `EudiRQESUi.initiate(remoteUri)`. | Un servicio web (notaría, empleador) ofrece un documento y la wallet lo descarga para firmarlo. |

Los botones **"Saber más"** abren textos informativos. Los nombres de los recursos de texto están cruzados
(`authenticate_learn_more_*` contiene el texto de *firmar*), pero la pantalla los vuelve a cruzar y **cada
botón muestra su texto correcto**: es solo un nombre confuso en el código.

Hallazgos menores de textos (para la Fase 4):
- La tarjeta de Firmar dice "eligiendo un archivo de tu dispositivo", pero el panel ofrece también "Escanear QR".
- El botón de la barra superior dice "Add" sin traducir (`home_screen_add_credential_button`).

---

## 3. Firmar, explicado desde cero

### 3.1 Autenticar no es lo mismo que firmar

| | Autenticar | Firmar |
|---|---|---|
| Qué hace | Compartes datos de una credencial (nombre, edad, afiliación). | Aplicas tu firma a un documento concreto. |
| Qué prueba | "Soy yo / esto es cierto sobre mí". | "Yo, esta persona, acepto *este* contenido, en *este* momento". |
| Resultado | Quien pregunta recibe un sí/no o unos datos. | Un archivo firmado que cualquiera puede verificar después. |
| Ejemplo | Mostrar que eres mayor de 18 en la discoteca. | Firmar un contrato de trabajo. |

### 3.2 Qué es una firma electrónica, sin tecnicismos

Firmar es como sellar un sobre con un sello **que solo tú tienes** y que cualquiera puede comprobar:

1. Del documento se calcula una **huella** (hash): cambia una coma y la huella cambia.
2. La huella se **cifra con tu clave privada** (tu sello). Eso es la firma.
3. Se adjunta tu **certificado**: un documento emitido por una autoridad de confianza que dice "esta clave pública pertenece a *esta persona*, verificada por nosotros".
4. Se añade un **sello de tiempo** de un tercero: prueba de que el documento existía y estaba firmado en esa fecha.

Quien recibe el archivo comprueba tres cosas: que el documento no cambió, que la firma corresponde al certificado,
y que la autoridad que emitió el certificado es de confianza.

### 3.3 ¿Quién firma realmente? ¿Yo, o Andes SCD?

**Firmas tú.** Andes SCD no firma *por* ti: es el **tercero de confianza** que (a) comprueba tu identidad y te
emite el certificado, (b) opcionalmente **guarda tu clave** en un equipo seguro para que no viva en el teléfono
(firma remota), y (c) da el **sello de tiempo**. La decisión de firmar la tomas tú, autorizándola en la wallet.

### 3.4 ¿Persona natural o "como ente"?

| Quiero firmar… | Certificado que se necesita | Quién firma |
|---|---|---|
| Una carta o un formulario **a título personal** | Certificado de **persona natural** | La persona |
| Un contrato **en nombre de mi empresa** | Certificado de **representante legal** o **pertenencia a empresa** | La persona, actuando por la empresa |
| Un documento "de la empresa" sin una persona detrás | Sello de persona jurídica (no es firma de una persona) | La empresa |
| Un contrato **entre dos partes** | Un certificado por cada firmante | Cada parte firma por separado (firma múltiple) |

Andes SCD, en su página pública, lista estos tipos de certificado como parte de su alcance (según el estudio
`C-confianza-datos.md`, línea A13). Para el piloto, el caso base es el primero: **persona natural**.

### 3.5 Dónde vive la clave

| Modelo | Dónde está tu clave | Cómo firmas | Se usa en… |
|---|---|---|---|
| **Local** | En un token o en el teléfono. | Conectas el token o desbloqueas el teléfono. | Firma "de siempre" con USB/archivo. |
| **Remota (la que usa Firmar)** | En un servidor seguro (HSM) del prestador. | La wallet te identifica, tú autorizas, el servidor firma la huella. | Wallet EUDI (estándar CSC y RQES). |

La ventaja de lo remoto es que la clave nunca sale del servidor seguro y no depende del teléfono. El costo es que
**hace falta un servidor del prestador** con esa API.

---

## 4. ¿Con quién trabajamos? Actores del flujo de firma

```
Persona ──usa──> Wallet ──pide firmar──> Servicio de firma ──usa──> CA (certificado) + HSM + Sellado
   │                │                         (srv-firma)             (Andes SCD)
   │                └── descarga documento ── Sitio que pide la firma (empleador, notaría, EPS)
   └── autoriza con su identidad (PID de Registraduría) y con el desbloqueo de la wallet
```

| Actor | Papel | Quién lo cumple en Avance |
|---|---|---|
| **Firmante** | Decide y autoriza. | La persona (Geovani, un afiliado, un candidato…). |
| **Wallet** | Identifica al firmante, muestra qué firma y pide su autorización. | `app-new-wallet`. |
| **Sitio solicitante** | Ofrece el documento y lo recibe firmado. | Contratación, EPS, banco… (nuevo: botón "Firmar"). |
| **Servicio de firma** (QTSP/ECD en la terminología europea) | Aloja la clave, firma la huella, entrega el archivo. | **No existe hoy.** Propuesta: `srv-firma`, API estándar CSC v2. |
| **Autoridad de certificación** | Emite el certificado de firma. | **Andes SCD** (según el contexto del proyecto, el equipo la opera sobre EJBCA Community). |
| **Sellado de tiempo** | Prueba de fecha. | Andes SCD ofrece "Estampado cronológico" (según su sitio). |
| **Verificador de la firma** | Comprueba el archivo firmado. | Cualquier lector de PDF/AdES; opcional: un verificador propio. |

**Recomendación:** Andes SCD es la autoridad detrás de las firmas, y `srv-firma` es el servicio del piloto que se
conecta a ella. Andes SCD ya tiene su propio "Circuito de Firmado" comercial (envío de documentos a firmantes);
**no se sabe si ofrece una API** que sirva para este flujo, y esa es la primera pregunta para ellos (§7).

### Lo que hay que decir con claridad (marco legal)

- En Colombia **no existe la figura "cualificada"** de la UE. Lo que se firme sería **firma electrónica** (Decreto 2364/2012, Ley 527/1999). La propia wallet ya dice "firma electrónica legalmente vinculante".
- La firma **digital** con efectos plenos (Ley 527, art. 28) exige certificado de una entidad acreditada por la ONAC. Andes SCD declara estar acreditada para su servicio de firma electrónica, pero **si este flujo de firma remota cae dentro de ese alcance lo debe confirmar Andes**.
- El estudio del proyecto avisa de otro límite técnico: el alcance acreditado de Andes SCD está en **RSA 2048/SHA-256**, y las credenciales del piloto usan **ES256/P-256**. Para firmas de documentos habría que usar RSA, o aclarar el alcance.
- No se debe afirmar equivalencia con la firma cualificada europea (eIDAS) ni con "QES".

---

## 5. Estado actual (qué hay y qué falta)

| Pieza | Estado |
|---|---|
| Autenticar **en línea** con los verifiers | ✔ Funciona (banco, discoteca, contratación, EPS). **(sin verificar)**: caso del mismo teléfono, sin escanear. |
| Autenticar **en persona** (BLE) | Código presente; sin pruebas en Avance. No hay lector. **(sin verificar)** si las credenciales están en formato mdoc, que es el que usa la proximidad. |
| Firmar: interfaz de la wallet | Presente (`DocumentSign`, `EudiRQESUi`). |
| Firmar: servidor de firma | ✘ `RQESConfigImpl` apunta a `walletcentric.signer.dev.eudiw.dev` con un secreto de demostración (servidor de pruebas de la UE). |
| Firmar: CA y sellado propios | ✘ No integrados. Andes SCD existe como CA del equipo, pero sin servicio de firma remota. |
| Firmar: sitio que pide la firma | ✘ Ninguno. |

---

## 6. Plan por fases

Estimaciones aproximadas para un equipo pequeño; se afinan al terminar la Fase 0.

### Fase 0 · Línea base (1–2 días)

- Probar en un teléfono real los cuatro caminos (en persona, en línea, firmar desde dispositivo, firmar por QR) y anotar qué falla.
- Comprobar en qué formato están emitidas las credenciales (SD-JWT o mdoc).
- **Salida:** tabla de resultados y lista de bloqueos. **Criterio:** cada camino con estado "funciona / falla / no aplica".

### Fase 1 · Autenticar en línea completo (3–5 días)

- Botón "Abrir mi wallet" en las páginas de verifier para el mismo teléfono (enlace directo).
- Matriz de pruebas: QR, mismo dispositivo, rechazo del usuario, credencial vencida, credencial revocada.
- Mensajes de error claros en español.
- **Criterio:** los cuatro verifiers pasan toda la matriz.

### Fase 2 · Autenticar en persona (1–2 semanas)

1. Decidir si el PID y las credenciales se emiten también como mdoc.
2. Emitir mdoc desde el emisor de la Registraduría.
3. Crear un **lector** (modo lector o app) que pida solo "mayor de 18" para el caso de la discoteca.
4. Definir la confianza del lector (certificados de lector registrados en el tl-publisher).
5. Probar permisos de Bluetooth en varias versiones de Android.
- **Criterio:** un teléfono con wallet presenta "mayor de 18" a otro teléfono lector, sin internet.

### Fase 3 · Firmar (3–4 semanas, la más grande)

1. **Decisión previa (§7, pregunta 1):** demo rápida contra el QTSP de pruebas de la UE, o servicio propio.
2. Crear `srv-firma`: API CSC v2 (listar credenciales, autorizar, firmar hash), autorización de la persona con su PID, registro de firmas.
3. Conectarlo a la CA de Andes SCD para el certificado del firmante y al sellado de tiempo.
4. Endpoint de entrega de documentos para el flujo por QR.
5. Primer caso real: **firmar el contrato en Contratación** (y luego el consentimiento en la EPS).
6. Ajustar `RQESConfigImpl` y los certificados de confianza; mostrar las firmas en "Transacciones".
7. Verificador de firmas: pantalla o servicio que comprueba un PDF firmado.
- **Criterio:** una persona firma un contrato desde la wallet y un tercero comprueba la firma, el certificado y la fecha.

### Fase 4 · Experiencia y operación (1 semana)

- Textos en español (tarjeta de Firmar, "Add"), iconos y marca Avance.
- `srv-firma` en el panel de control y en `up.sh`.
- Documentación del flujo y guía de pruebas.

---

## 7. Preguntas abiertas

**Para Andes SCD**
1. ¿Tienen una API (o pueden exponer una) para **firma remota** compatible con CSC v2, o solo el "Circuito de Firmado"?
2. ¿Cómo se verifica la identidad para emitir el certificado de persona natural, y se puede apoyar en el **PID de la wallet**?
3. ¿Este flujo (clave en HSM, autorización desde la wallet) está dentro del **alcance acreditado por la ONAC**?
4. ¿Algoritmos admitidos para firmar documentos (RSA/SHA-256, ECDSA)?
5. ¿Condiciones y costo del sellado de tiempo y de volumen de firmas para el piloto?

**Para el equipo / legal**
1. ¿Demo con el servidor de pruebas de la UE, o construimos `srv-firma` desde el inicio? *(Recomendado: construirlo.)*
2. ¿Qué texto ve la persona: "firma electrónica" (así lo dice ya la wallet)? ¿Alguna advertencia de que no es cualificada?
3. ¿Qué documentos firmar primero: contrato de contratación, consentimiento de la EPS, otro?

---

## 8. Riesgos principales

| Riesgo | Efecto | Mitigación |
|---|---|---|
| Andes SCD no tiene API de firma remota. | La Fase 3 se retrasa o se limita a un servicio propio con la CA del equipo (no acreditado). | Preguntarlo primero; etiquetar claramente lo no acreditado. |
| Desajuste de algoritmos (ES256 vs RSA). | La firma de documentos no cabe en el alcance acreditado. | Usar RSA para firmar documentos; confirmar con Andes. |
| Las credenciales no están en mdoc. | La Fase 2 requiere cambiar el emisor. | Resolverlo en la Fase 0. |
| Prometer más que la ley. | Riesgo reputacional y jurídico. | Mantener la redacción "firma electrónica" y el marco de §4. |

---

## 9. Decisiones que se piden

1. Aprobar el **orden**: Fase 0 → Fase 1, y luego elegir entre Fase 2 (en persona) y Fase 3 (firmar).
2. Aprobar **Andes SCD como la autoridad detrás de las firmas** (sujeto a sus respuestas).
3. Aprobar el **primer caso de firma**: contrato en Contratación.
