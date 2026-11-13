---
title: "Pérdida, robo, reinstalación y cambio de celular"
subtitle: "Qué plantean el ARF y eIDAS 2.0, y cuánto de eso tiene hoy el ecosistema Avance"
lang: es
---

**Proyecto:** Identidad Digital Soberana SSI  |  **Fecha del informe:** 30 de octubre de 2026
**Fuentes:** corpus del proyecto (`docs/corpus-eudi`): ARF 3.0.0 (requisitos de alto nivel, HLR), Reglamento (UE) 2024/1183 (eIDAS 2.0) y CIR 2024/2979; código y documentos del ecosistema.

# 1. En una frase

> **En la UE la clave privada de una credencial nunca se mueve ni se copia: cuando se pierde el teléfono o se cambia de wallet, no se «recupera» nada. Se revoca lo viejo y se vuelve a emitir lo nuevo.**

**Una imagen sencilla.** Es como una tarjeta bancaria. Si la pierdes, el banco **cancela** la tarjeta y te **entrega otra con otro número**. Nadie intenta copiar el chip de la vieja. Lo que hay que resolver es cómo pedir la cancelación sin tener la tarjeta en la mano y cómo recibir la nueva sin repetir todo el trámite.

# 2. Los cuatro casos

| # | Caso | Qué pasa con la clave | Qué hay que hacer |
|:-:|---|---|---|
| 1 | **Pérdida del celular** | Sigue en el teléfono perdido | Revocar la wallet, y después activar una nueva y pedir de nuevo los documentos |
| 2 | **Robo del celular** | Sigue en el teléfono robado | Igual que el anterior, pero con urgencia: hay que impedir que alguien la use |
| 3 | **Desinstalación por error** | Se borra con la app | Instalar de nuevo, activar una wallet nueva y pedir de nuevo los documentos |
| 4 | **Cambio de celular** | Se queda en el teléfono viejo | Llevar la lista de documentos al nuevo y pedirlos de nuevo |

En los cuatro casos el resultado es el mismo: **una wallet nueva con claves nuevas y documentos nuevos**. Lo que cambia es cómo se llega hasta ahí.

# 3. Lo que plantea la UE

## 3.1 Las dos defensas del robo

| Defensa | Qué dice | Dónde está |
|---|---|---|
| **El ladrón no puede usar la wallet** | Las claves de un PID viven en un módulo seguro del teléfono (WSCA/WSCD) que **exige autenticar a la persona con nivel de seguridad alto** antes de firmar. Además, la wallet pide el desbloqueo del dispositivo antes de mostrar cualquier dato. | `WIAM_14`, `WIAM_15` |
| **La persona puede cancelar** | El proveedor de wallet **debe** dar a la persona el derecho de pedir la revocación de su wallet, y **debe** darle un medio de identificarse que **no dependa de la wallet** (porque puede estar perdida). | CIR 2024/2979 art. 6(3)(b) y (c); `WURevocation_10`; `VCR_07` |

## 3.2 Cómo se revoca, paso a paso

```
       PERSONA                         PROVEEDOR DE WALLET                    EMISOR (PID / documentos)
          |                                     |                                         |
          | (1) pide revocar su wallet          |                                         |
          | (se identifica sin la wallet perdida)                                         |
          |------------------------------------>|                                         |
          |                                     |                                         |
          |                      (2) marca la wallet «revocada»                           |
          |                      en su lista de estado                                    |
          |                                     |                                         |
          | (3) aviso en menos de 24 h          |                                         |
          | por otro canal                      |                                         |
          |<------------------------------------|                                         |
          |                                     |                                         |
          |                                     | (4) consulta la lista de estado         |
          |                                     | cada cierto tiempo                      |
          |                                     |<----------------------------------------|
          |                                     |                                         |
          |                                     |                             (5) revoca los documentos
          |                                     |                             de esa wallet
          |                                     |                             PID: obligatorio
          |                                     |                             otros: opcional
          |                                     |                                         |
```

| Pieza | Qué exige | Dónde |
|---|---|---|
| Proveedor de wallet | Pone `revoked` en todos los índices de las atestaciones (WIA) de esa instalación | `WURevocation_07` |
| Proveedor de wallet | Revoca a petición de la persona, **autenticándola antes** | `WURevocation_10` |
| Proveedor de wallet | Avisa a la persona en **máximo 24 horas**, con el motivo, por un **canal independiente de la wallet** | `WURevocation_14`, `WURevocation_16` |
| Emisor de **PID** | **Debe** comprobar periódicamente si la wallet o su módulo de claves fue revocado y, si es así, **revocar el PID de inmediato** | `WURevocation_18` |
| Emisor de **otros documentos** | **Puede** hacerlo, pero debe **publicar su política** | `WURevocation_19` |
| Emisor | Revoca el PID a petición directa de la persona (los demás documentos, «debería») | `VCR_07a`, `VCR_07b` |
| Emisor | Si la persona falleció, el emisor del PID pide al proveedor que revoque la wallet | `WURevocation_11`, `VCR_08` |
| Estado | Si hay una brecha de seguridad general, el Estado miembro actúa según el art. 5e de eIDAS | Reglamento 2024/1183 art. 5e |

## 3.3 Reinstalar y cambiar de celular

| Mecanismo | Qué es | Dónde |
|---|---|---|
| **Activación de una wallet nueva** | Al instalar, la app se activa ante el proveedor: verifica que es una instancia genuina, que el módulo de claves es de nivel alto, pide iniciar sesión en una cuenta del proveedor y le entrega las atestaciones nuevas | `WIAM_03` a `WIAM_10` |
| **Restablecimiento de fábrica** | Borra todos los documentos, el historial y las claves del dispositivo. Sirve, por ejemplo, antes de desinstalar | `WIAM_13` |
| **Reemisión manual** | La wallet debe dejar a la persona pedir de nuevo cualquier documento, de inmediato | `ISSU_58` |
| **Reemisión sin intervención** | Los proveedores deben procurar que la persona no tenga que hacer nada para renovar atestaciones y documentos | `ISSU_42` |
| **Lo viejo deja de valer** | Al reemitir, la wallet deja de presentar el documento anterior y el emisor lo revoca | `ISSU_62`, `VCR_09` |
| **Objeto de migración** | Ver abajo | `Mig_01` a `Mig_07b` |

**El objeto de migración (Topic 34).** Es una lista que la wallet mantiene al día y que sirve para pasar a otra wallet o a otro teléfono:

- Lleva, **por cada PID o documento ligado al dispositivo: el tipo, quién lo emitió y sus direcciones de servicio**. **No lleva** valores de atributos ni claves privadas (`Mig_03`).
- Si la persona lo autoriza, para los documentos **no** ligados al dispositivo puede llevar también los datos, para restaurarlos sin volver al emisor (`Mig_03a`, `Mig_07a`).
- Se guarda **fuera del teléfono**, en un almacenamiento que elige la persona, de forma que **la wallet nueva lo pueda recuperar aunque la vieja ya no exista** (`Mig_04`).
- Va protegido en confidencialidad e integridad y solo lo puede usar la persona (`Mig_05`).
- Al importarlo, la persona elige qué documentos quiere, y la wallet los pide de nuevo; también puede restaurar el historial (`Mig_07a`, `Mig_07b`).
- Si la persona borra un documento, también se borra su entrada (`Mig_03b`).

# 4. Cómo está hoy el ecosistema Avance

**Cómo leerlo.** Es una lectura del ecosistema tal como está, no una prueba formal.
**●** Existe  ·  **◐** Parcial  ·  **✖** No se encontró

## 4.1 Revocación

| Pieza | Estado | Qué se vio |
|:---|:-:|---|
| Revocar las credenciales de un titular | **●** | Botón en el panel de la Registraduría, `./revocar.sh registraduria <cédula>` y `./revocar.sh uis <usuario>`, y la API interna, con motivo y bloqueo opcionales. Pide confirmación. Está en `docs/REVOCACION.md` |
| Revocar solo por estado de la wallet o de su clave | **◐** | Un trabajo cada 8 horas en los emisores revoca las credenciales cuya wallet o almacén de claves figura como inválido en su lista de estado. Corresponde a `WURevocation_18` |
| Registro de lo revocado | **●** | Se conserva con fecha y motivo (`api-request`, `admin-panel`, `wallet-revoked`); no se purga al vencer |
| Revocar **la wallet** (lo que dispararía lo anterior) | **✖** | El proveedor de wallet asigna índices en sus listas de estado, pero **no se encontró ningún endpoint para pedir su revocación**. Las rutas son de desafío, atestaciones de clave y de instancia, y metadatos |
| Identificarse **sin** la wallet perdida | **✖** | No se encontró un mecanismo de cuenta o de autenticación en el proveedor de wallet (`WIAM_06`, art. 6(3)(b)) |
| Avisar a la persona de la revocación | **◐** | Cada revocación registra un **aviso con plazo de 24 h** (`revocation_notice`), con el motivo en lenguaje claro. Se entrega solo si hay un webhook configurado; en la UIS el contacto sale de `datos_estudiantes`, pero **la Registraduría no guarda datos de contacto**, así que el aviso queda **PENDIENTE** y alguien debe avisar a mano (panel o `./revocar.sh avisos`) |
| Impedir que quien reportó el robo o el fraude reciba otra credencial | **●** | **Bloqueo de emisión por titular** (`blocked_subject`): se bloquea al revocar, con motivo `fraud` o el que corresponda, y la oferta de onboarding responde `403 SUBJECT_BLOCKED` |
| Motivo de la revocación | **●** | `user-request`, `data-changed`, `fraud`, `legal`, `other` y, en la automática, `wallet-revoked` |
| El PID revocable por titular | **◐** | Los **640 PID emitidos antes del 30-oct** no tienen titular guardado ni punto de estado propio: solo los alcanza el trabajo periódico |

## 4.2 Reinstalar y cambiar de celular

| Pieza | Estado | Qué se vio |
|:---|:-:|---|
| Respaldo automático de Android | **●** (correcto) | `allowBackup="false"` y reglas de respaldo vacías: los datos de la wallet no salen del teléfono. Es lo adecuado para que no se copien las claves |
| Objeto de migración | **✖** | No se encontró en el código de la wallet |
| Volver a pedir el PID | **◐** | Se hace repitiendo el onboarding. No se probó qué ocurre con el PID anterior de la misma persona |
| Reemisión de documentos | **◐** | Notaría, Cancillería y aerolínea tienen **reexpedición**: la copia anterior queda revocada y la nueva usa otro índice. EPS y gimnasio no la tienen |
| «Restablecer de fábrica» (`WIAM_13`) y reemisión manual (`ISSU_58`) | **◐** | No se verificaron en la app |

## 4.3 Un efecto práctico que conviene ver

Para anular un trámite en la notaría, la persona se identifica con su PID **desde la wallet**. Si el celular se perdió, **no puede anular por sí misma** lo que tenía. Es el mismo hueco: falta un medio de identificarse que no dependa de la wallet.

# 5. Qué aplicar, de menor a mayor afectación al ecosistema

**Cómo se ordenó.** De lo que **no toca nada de lo que ya funciona** a lo que **cambia piezas centrales**. Cada fase se puede hacer sola y deja el ecosistema funcionando. Cada una indica **qué hay que desarrollar**, **qué toca**, y **qué falla corrige**.

Un dato que ayuda a ordenar: el emisor ya guarda, por cada credencial nueva, el **índice de la wallet** (`client_status_list_*`) y el **del módulo de claves** (`key_storage_status_list_*`) junto al **titular** (`subject`). Con eso, el emisor sabe en qué lugar de la lista de estado del proveedor de wallet está cada instalación, aunque el proveedor no tenga un endpoint de revocación.

| Fase | Qué es | Desarrollo | Qué toca | Falla que corrige |
|:-:|---|---|---|---|
| **0** | **Procedimiento y política** | Ninguno. Escribir el procedimiento de atención: la persona se identifica (cédula y verificación de contacto o presencial) y el operador ejecuta `./revocar.sh registraduria <cédula> --motivo fraud --bloquear`. Publicar la política de cada emisor sobre qué hace si la wallet es revocada (`WURevocation_19`) | Nada | Pérdida y robo **ya se pueden atender**, a mano, para PID emitidos desde el 30-oct |
| **1** ✔ | **Script `revocar-wallet.sh`** (hecho, 30-oct) | Un archivo nuevo, del estilo de `revocar.sh`: dado un titular, lee en `issued_credential` los índices de wallet y de módulo de claves y los pone en 1 en la lista de estado del proveedor de wallet. El trabajo de 8 horas que ya existe en los emisores revoca después **todos los demás documentos de esa wallet** | Un archivo nuevo. **Ningún servicio cambia** | Pérdida y robo: se revoca **la wallet entera**, no solo el PID. Hay que comprobar que el servicio de estado del proveedor acepta esa operación con la clave del operador |
| **2** | **Conectar el aviso** | Configurar el webhook del aviso. Para la Registraduría, hacer que el contacto lo entregue el onboarding (que ya verifica el contacto) en lugar de quedar `PENDIENTE` | Configuración y una pieza pequeña (`GetSubjectContact`) en el emisor | Cumplir las 24 horas del aviso sin intervención manual |
| **3** ✔ | **Reexpedición en todos los emisores** (hecho en EPS y gimnasio, 30-oct) | Añadir a EPS y gimnasio la reexpedición que ya tienen la notaría, Cancillería y la aerolínea (la copia anterior queda revocada, la nueva usa otro índice) | Un endpoint nuevo y un campo en el protocolo de cada uno, sin tocar el resto | Reinstalación y cambio de celular: no quedan dos documentos vivos |
| **4** ✔ | **Revocar el PID anterior al reemitir** (hecho, apagado por defecto) | En el emisor del PID, una opción (apagada por defecto) que revoque el PID previo del mismo titular cuando se emite uno nuevo (`VCR_09`, `ISSU_62`). Apagada, nada cambia | Un parámetro en el emisor de la Registraduría, con pruebas | Reinstalación y cambio de celular: el PID viejo deja de valer. Cuidado: quien tenga dos teléfonos a la vez perdería el primero |
| **5** ✔ | **Pantalla «Perdí mi celular»** | Una página para la persona: cédula más un código al contacto que ya verificó el onboarding. Llama a la API interna de revocar con motivo `user-request` y, si corresponde, al script de la fase 1. Con límites de intentos y verificación fuerte | Una ruta nueva en el onboarding o en el panel. Es sensible: si falla, alguien podría revocar el PID de otra persona | Pérdida y robo sin depender del operador ni de la wallet perdida (art. 6(3)(b) del CIR 2024/2979) |
| **6** ✔ | **Endpoint de revocación en el proveedor de wallet** | En `wallet-provider-service`: revocar una instancia, con autenticación de la persona y registro de a quién pertenece (`WIAM_06`, `WURevocation_07` y `_10`) | El proveedor de wallet, que está en el camino de **toda** emisión | Hace por la vía oficial lo que la fase 1 hace por el atajo |
| **7** ◐ | **Objeto de migración en la app** | En la app: generar y mantener la lista (tipo, emisor y direcciones, sin datos ni claves), cifrarla, exportarla al almacenamiento que elija la persona e importarla en la wallet nueva para pedir de nuevo cada documento (`Mig_01` a `Mig_07b`) | La app Android, su almacenamiento y el flujo de emisión | Cambio de celular sin repetir cada trámite desde cero |

**Estado de las fases 5, 6 y 7 (30-oct).**

- **Fase 6 (hecha y desplegada, 30-oct).** El proveedor de wallet tiene el endpoint interno `POST /internal/v1/wallet-instances/revoke` (clave `X-API-Key`, hasta 200 entradas, 8 pruebas que pasan). El emisor de la Registraduría lo usa cuando la API de revocación recibe `"wallet": true`: calcula los índices de wallet y de módulo de claves del titular, revoca sus credenciales y después le pide al proveedor revocar la wallet (22 pruebas del emisor pasan). **Probada de punta a punta en el ecosistema real:** con una fila de prueba y índices nuevos, la API del emisor con `wallet:true` devolvió `wallet: {revoked: 2, failed: 0}`; en la lista pública los índices de la wallet y del módulo de claves pasaron a 1 y el de una wallet de control siguió en 0. La fila de prueba se borró; los índices de prueba quedan consumidos. Una credencial **sin lista de estado propia** no se revoca (`withoutStatus`), como ya hacía el emisor: solo la wallet. **Hallazgo de seguridad:** el nginx central dejaba pasar `/wallet-provider/internal/…` desde internet; se agregó la regla que lo responde con 404 y se comprobó.
- **Fase 5 (hecha y desplegada, 30-oct).** El onboarding tiene `POST /api/v1/lost-device/start` y `/confirm` y la página `/onboarding/perdi-mi-celular.html`: documento, código al contacto verificado, y revocación por el emisor (motivo `user-request`, con wallet). Respuesta igual exista o no la cédula, 3 códigos por documento y 10 por IP por hora, auditoría sin el código. **Probada de punta a punta** con una cédula de prueba y una fila de prueba: código malo → 401 genérico; código bueno → 200 «Revocamos la wallet de tu celular anterior…»; los índices de wallet y de módulo de claves pasaron a 1 en la lista pública y el de control siguió en 0. 18 pruebas unitarias pasan, corridas en una copia de trabajo porque al proyecto original le faltan dependencias de prueba en la caché sin red. **Límite aceptado para el piloto:** el SMS y el correo se simulan en todo el ecosistema, y el onboarding devuelve el código (`OTP_EXPOSE_CODE=true`); la seguridad real depende de conectar un canal verdadero. Con el ecosistema de pruebas público, cualquiera con una cédula de sesión aprobada puede revocar sus credenciales: aceptable con datos de prueba, **no** con datos reales.
- **Fase 7 (lógica hecha, sin cablear).** `MigrationObject`, cifrado (PBKDF2 y AES-GCM) y exportar/importar, 16 pruebas que pasan. No hay pantallas, ni selector de archivos, ni re-emisión por OpenID4VCI, ni sincronización al emitir o borrar: no hay teléfono para probarlo. Ver `app-new-wallet/docs/objeto-de-migracion.md`.

**Estado de la fase 4.** Está escrita, probada y **apagada**. Al guardar una credencial con titular, si el titular tiene credenciales vigentes de **otra instalación de wallet** (otro `clientStatus`), esas se revocan con motivo `reissued-new-wallet` y se registra el aviso. Las copias de la misma wallet (lotes, otro formato, renovación) no se tocan. Si la revocación falla, la emisión continúa. Código: `RevokePreviousWalletOnIssuance.kt`, con 6 pruebas que pasan. Para **encenderla**: poner `REG_REISSUE_REVOKE_PREVIOUS_WALLET=true` y recrear el contenedor `pid-issuer-reg` (hay que reconstruir la imagen). **No se desplegó**: el servicio que está corriendo no tiene este cambio. Cuidado al encenderla: quien use dos teléfonos a la vez perdería el primero.

**Estado de la fase 3.** EPS y gimnasio ya permiten **reexpedir** desde su portal: el botón *Reexpedir* de cada documento vigente pide una copia nueva y, **solo cuando la wallet la recibe**, las anteriores de ese tipo pasan a «reemplazada» (bit 1 en su lista de estado). Si la persona no completa la entrega, no pierde nada. Lo que ya existía no cambió: cada copia sigue con su índice, y el botón *Revocar* sigue igual. Se comprobó la lógica de los almacenes con pruebas de código y que los servicios reconstruidos responden. **No se probó con una wallet real** la entrega con reexpedición.

**Estado de la fase 1.** El script está en la raíz del proyecto (`./revocar-wallet.sh`). Por defecto **simula** (muestra qué revocaría) y para revocar pide `--si` y escribir `SI`. Se probó solo en simulación: hoy no hay ninguna credencial con titular, así que **la revocación real no se ha ejecutado de punta a punta**. Lo que falta comprobar es que el servicio de estado del proveedor acepte la operación con la clave del operador.

**Los 640 PID de pruebas (emitidos entre el 21 y el 27 de octubre).**
- No son 640 personas: vienen de **10 instalaciones de wallet**, con lotes de 20 a 100 copias por formato.
- Todos están **vigentes** en el registro del emisor (ninguno revocado), no tienen titular y **no tienen lista de estado propia**: ningún verificador puede ver que se revoquen.
- Si la wallet se borró, las claves se perdieron con ella: **no se pueden presentar**. En el emisor son solo un registro.
- Vencen entre el **21 y el 27 de noviembre** y el trabajo de 8 horas los **borra solo** al vencer. **Revocarlos sería peor**: un registro revocado nunca se purga y quedaría para siempre. **Borrarlos a mano tampoco aporta nada**, porque no cambia lo que ven los verificadores. Lo más limpio es **dejarlos vencer**.
- El bloqueo de emisión y el aviso con plazo **ya existen**; la fase 2 solo los conecta a un canal.

**Qué da cada tramo:**

| Tramo | Fases | Resultado |
|---|---|---|
| **Sin riesgo** | 0 y 1 | Pérdida y robo atendidos con revocación de la wallet entera, por un operador. No cambia ningún servicio |
| **Con poco cambio** | 2, 3 y 4 | Aviso automático y sin documentos duplicados al reinstalar |
| **Autoservicio** | 5 | La persona lo hace sola |
| **Camino oficial completo** | 6 y 7 | Cumple la forma que plantea la UE |

# 6. Prueba sugerida para verificarlo

1. **Fase 0:** con un PID emitido hoy, ejecutar la revocación por titular con motivo `fraud` y bloqueo. Comprobar que un verificador lo rechaza, que el aviso queda registrado con su plazo y que el onboarding responde `403 SUBJECT_BLOCKED`.
2. **Fase 1:** con dos documentos en una misma wallet (PID y carné), ejecutar `revocar-wallet.sh` y comprobar que **ambos** quedan revocados después del trabajo de 8 horas (o forzando la ejecución).
3. **Reinstalación:** desinstalar, instalar, volver a pedir el PID y comprobar qué ocurre con el anterior.
4. **Reexpedición:** en notaría, pedir la reexpedición y comprobar que la copia anterior es rechazada por el verificador.
5. **Cambio de celular:** con dos teléfonos, contar cuántos pasos lleva volver a tener cada documento.

# 7. Qué no se verificó

- No se leyó el código completo de la wallet ni del proveedor. «No se encontró» significa que no hay rastro en las rutas y archivos revisados, no que se haya probado que no existe. Conviene que lo confirme el equipo de la wallet antes de darlo por cierto.
- No se probaron las pruebas del apartado 6.
- Los requisitos se citan del corpus del proyecto (ARF 3.0.0). El texto del ARF y de los reglamentos puede cambiar; para una decisión formal, contrastar con la versión vigente.

# 8. Glosario mínimo

| Término | Significado |
|---|---|
| **ARF** | Marco de arquitectura y referencia de la cartera europea de identidad |
| **eIDAS 2.0** | Reglamento (UE) 2024/1183, que crea la cartera europea de identidad digital |
| **Wallet Unit / Instancia** | La wallet instalada y activada en un teléfono concreto |
| **Proveedor de wallet** | Quien entrega y certifica la wallet y emite sus atestaciones |
| **WIA** | Atestación de la instancia de wallet: dice «esta wallet es genuina y sigue vigente» |
| **KA** | Atestación de clave: dice qué módulo guarda las claves y de qué nivel es |
| **WSCA / WSCD** | Módulo seguro del teléfono donde viven las claves críticas |
| **Lista de estado** | Lista firmada con un bit por documento o wallet; 1 = revocado |
| **Revocar** | Marcar como no válido, sin borrar el registro |
| **Reemitir** | Entregar un documento nuevo, con otra clave, en lugar del anterior |
| **Objeto de migración** | Lista cifrada de qué documentos hay y dónde pedirlos de nuevo, para pasar a otra wallet |
| **Reexpedición** | Nombre local de la reemisión que revoca la copia anterior |

*Basado en el corpus del proyecto (ARF 3.0.0, Reglamento 2024/1183, CIR 2024/2979), `docs/REVOCACION.md`, el manifiesto de la wallet y el código del proveedor de wallet, a 30-oct-2026. Datos de prueba, sin validez legal. No es asesoría jurídica.*
