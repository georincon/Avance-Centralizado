# Fase 6: notaría — actos notariales viables (2026-10-22)

La notaría base (`verifiers/notaria`, `/notaria`) ya emite tres documentos como PuB-EAA (declaración extraproceso, poder especial,
reconocimiento de documento) y verifica el PID para identificar a quien hace el trámite. Este documento propone qué actos añadir, con
qué datos y quién los verifica. Es un simulador: **no es asesoría jurídica**.

## Qué está verificado y qué no

| Origen | Contenido |
|---|---|
| Verificado en el corpus (`docs/corpus-eudi/conocimiento/colombia/derecho-notarial.md`) | DL 960/1970 art. 24 (identificar comparecientes), 72 (reconocimiento: plena autenticidad y fecha cierta), 73 (autenticación de firma), 77 (la autenticación **no equivale** al reconocimiento ni da más fuerza), 78 (fe de vida, anotando el medio de identificación); Ley 588/2000 (copias y certificados como mensajes de datos); DL 019/2012 art. 18 y Res. SNR 2336/2018 (cotejo de huella con la RNEC); Sentencia C-159/2021 (inexequibles los arts. 59-63 del DL 2106/2019: escritura digital) |
| Conocimiento general, **sin verificar en el corpus** | Decreto 1557/1989 (declaración extraproceso), Ley 979/2005 (unión marital de hecho), permisos de salida de menores, Ley 962/2005 (divorcio ante notario), Decreto 902/1988 (sucesión notarial) |

Regla de diseño que sale del corpus: una notaría da **testimonio de hechos percibidos en una diligencia**, no una certificación general de identidad (eso es de la
RNEC). Por eso cada documento debe decir qué acto notarial es y no presentarse como prueba de identidad.

## Propuesta, por orden

| # | Documento (PuB-EAA) | Datos | Verifica | Notas de diseño |
|---|---|---|---|---|
| 1 | **Fe de vida** (art. 78) | titular, documento, fecha de la diligencia, medio de identificación (PID presentado), vigencia 30 días | fondo de pensiones | Anotar «medio de identificación» es lo que exige el art. 78. Vigencia corta, no reutilizable |
| 2 | **Autenticación de firma** (art. 73) | firmante, documento, nombre y SHA-256 del archivo, fecha | quien recibe el documento | Distinta del reconocimiento (art. 77): el texto del documento notarial debe decir que no confiere mayor fuerza. Reutiliza el flujo del hash |
| 3 | **Permiso de salida de menor** | padre o madre (PID), menor (nombre, documento), destino, fechas, acompañante | migración o aerolínea | Dos personas: el progenitor se identifica con PID; los datos del menor los escribe el compareciente y los **declara bajo juramento**. Primer caso con un tercero en el documento |
| 4 | **Declaración de convivencia** | ya existe como declaración extraproceso, con `finalidad` fija | EPS (afiliar beneficiario), seguridad social | Se enlaza con la EPS: nuevo verifier que acepta este documento. Pide solo `finalidad` y `declaración` |
| 5 | **Unión marital de hecho** (Ley 979/2005) | compañeros (nombres, documentos), fecha de inicio declarada | EPS, bancos, pensiones | Exige **dos comparecientes** (los dos con PID): hay que diseñar el trámite de doble identificación |
| 6 | **Constancia de copia de escritura** | número y fecha de la escritura, notaría de origen, SHA-256 | quien reciba la copia | Es una **constancia**, no la escritura: la escritura digital está afectada por C-159/2021 |

Fuera por ahora: matrimonio y divorcio (varios comparecientes, formalidades), sucesión notarial (muchas partes), testamento (reservado por naturaleza), registro civil (se cruza con la
RNEC), constancia de identificación biométrica (depende del convenio RNEC, no verificado).

## Cambios comunes que piden estos actos

1. **Mayoría de edad sin fecha de nacimiento.** Hoy la notaría pide `birthdate` del PID. El PID de la Registraduría ya trae `age_equal_or_over.18`
   (parche del 22-oct): pedir solo eso es más respetuoso con los datos y es lo que usan discoteca y banco.
2. **Vigencia por acto.** Hoy todos los documentos duran lo mismo. La fe de vida debe durar 30 días; el poder, hasta la fecha que indique.
3. **Documentos con terceros.** `datosDelDocumento` supone un solo compareciente; los números 3 y 5 necesitan una lista de participantes.
4. **Nuevos verifiers.** Fondo de pensiones, migración/aerolínea y la acogida en la EPS son portales pequeños como discoteca/banco, con `WRPRC_VCTS` propio.

## Decisión pendiente

Qué se construye primero. Recomendación: **1 fe de vida, 2 autenticación de firma y 3 permiso de menor**, con el cambio común 1 (edad sin fecha de nacimiento).
Cada acto nuevo es un `vct` y una configuración en `src/documentos.js`, su formulario en `src/publico/index.html` y su validación en `src/tramites.js`.


## Estado (2026-10-23)

- **Hecho:** el permiso de salida de menor ya lo consume la aerolínea (`/aerolinea`, embarque): para un menor en un vuelo internacional, además del tiquete y del PID del
  adulto responsable, se pide el permiso notarial y se cruza con el tiquete (otorgante = adulto que se identificó, menor = pasajero del tiquete, destino, y que la fecha
  del vuelo esté dentro del viaje autorizado). También ya estaban hechos «pedir solo mayor de 18» y la vigencia por acto.
- **Hecho (23-oct, tarde):** fe de vida consumida por el portal del fondo de pensiones (`/pensiones`, estilo corporativo amarillo/azul); **constancia de copia de escritura**
  (`urn:co:notaria:copia-escritura:1`, solo constancia, C-159/2021); **declaración de convivencia** (`urn:co:notaria:convivencia:1`, 90 días) que la EPS acepta para afiliar
  al compañero(a) como beneficiario (el cotizante la presenta; la EPS comprueba emisor = notaría, declarante = quien se identificó y vigencia). Entrega única y reexpedición en la notaría.
  **Sin probar con la wallet todavía.**
- **En pausa:** unión marital de hecho (dos comparecientes con PID).
