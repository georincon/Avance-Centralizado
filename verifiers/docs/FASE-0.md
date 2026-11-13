# Fase 0: línea base (2026-10-21)

Solo lectura: no se levantó, bajó ni modificó ningún stack ni ningún archivo del ecosistema.

## Estado observado

| Pieza | Resultado |
|---|---|
| Stacks del compose raíz | En pie: panel, verifier, nginx, onboarding, wallet-provider, tl-publisher, pki, trust-validator |
| `/pid-registraduria` | Responde 200. Lo levantaron durante la sesión: al principio daba 502 |
| `/uis-issuer` | Responde 502. Sus contenedores ya no están. Estaba arriba al empezar; Registraduría y UIS se turnan |
| `/trust-validator`, `/tl-publisher` | 200 |
| Listas de confianza | PID 5, PUBEAA 5, QEAA 5, RPREG 5, WP 7, LOTL 5 |
| RPREG | 3 anclas: `Civica RP Access CA`, `Civica Verifier Access CA`, `Avance Verifier Access CA` |

## Hallazgos

1. **El verifier desplegado es `generic-verifier` (Kotlin) y no tiene el flujo de presentación.**
   `/verifier/` da 200 y `/actuator/health` da 200, pero:
   - `/verifier/wallet/request.jwt/x` da 404.
   - `/verifier/ui/presentations/v2` da 401, y `/api/ping` también.
   `ECOSISTEMA.md` lo marca como operativo, pero no puede hacer una presentación. El README de `generic-verifier` lo confirma: es un esqueleto.
   Consecuencia: los verifiers nuevos tienen que usar el endpoint oficial, como el patrón del amigo.
   `contratacion` no puede apoyarse en lo que hay desplegado.

2. **El tl-publisher tiene la mitad de la API que usa el código del amigo.**
   Existe `POST /tl-publisher/api/v1/verifiers` (cabecera `X-Api-Key` = `TL_OPERATOR_KEY`), que valida el CSR y pide la hoja a `pki-servicio`.
   **No existe `integracion/verifier.env`**, que entrega la URL del trust-validator y las clases de documentos (PID, QEAA, PubEAA).
   Sin ella, el endpoint oficial acepta cualquier emisor o falla con `Could not find Attestation Classification`.
   Hay que construir ese endpoint o generar el archivo a mano.

3. **El perfil `rp-access` no lleva el EKU `1.0.18013.5.1.6`.**
   `pki-servicio/emision.py:126` pone solo `CLIENT_AUTH`, y el mismo perfil está en `pki/perfiles.sh`.
   Hay que cambiar los dos sitios. No hace falta tocar ninguna CA.

4. **El PID de la Registraduría no trae edad.**
   Los metadatos no mencionan `age_equal_or_over` ni `age_over_18` en ninguna de las 4 configuraciones
   (mdoc y SD-JWT, con y sin diferido). La discoteca necesita ese dato.
   Hay que parchear el emisor o decidir que la edad se calcule de `birthdate`.

5. **Las carpetas `wallet/eps` y `wallet/notaria` del amigo son emisor y verifier a la vez.**
   Cada una emite credenciales (QEAA o PUBEAA) y las verifica. Ver "Verifiers de salud" abajo.

## Verifiers de salud (pedido del usuario)

| Verifier | Pide | Credencial | Lista del emisor |
|---|---|---|---|
| Afiliación EPS | PID + carné de afiliado | QEAA (carné de la EPS) | QEAA |
| Ficha médica de urgencias | PID + alergias y grupo sanguíneo, sin el resto | resumen clínico (EPS) | QEAA |
| Carné de vacunación | PID + vacunas | resumen clínico u otro emisor | QEAA o PUBEAA, por definir |
| Citas médicas | PID + afiliación vigente | carné de afiliado | QEAA |

- Son datos sensibles (Ley 1581 de 2012): pedir solo los campos necesarios y **no guardar** las presentaciones
  (`CONSERVAR_PRESENTACIONES=PT5M` o menos).
- Dependen de que exista un emisor EPS, y hoy no hay ninguno en este ecosistema.
  `wallet/eps` lo trae, pero con el dominio y la red del otro ecosistema.
- Para probar verifiers de salud sin teléfono hace falta ese emisor, o credenciales de prueba emitidas a mano.

## Decisiones que hay que tomar antes de la Fase 1

1. **Edad en el PID:** ¿parchear el emisor de la Registraduría (`age_equal_or_over`) o calcular la edad de `birthdate` en la discoteca?
2. **Emisor EPS:** ¿se trae `wallet/eps` adaptado a este ecosistema, o se prueban los verifiers de salud con credenciales hechas a mano?
3. **Qué hacer con `avance-verifier` (Kotlin):** dejarlo como línea aparte, que es lo recomendado, o retirarlo de `/verifier` para que no confunda.
4. **UIS y Registraduría:** hoy se turnan. Para probar `contratacion` hay que tener la UIS arriba, y para la discoteca y el banco, la Registraduría.

## Comprobaciones pendientes (no se hicieron para no tocar nada)

- `./estado.sh` y `verificar-cadenas.py` completos.
- Cuál de las 3 anclas de RPREG firma la hoja actual de `avance-verifier`. Su hoja tiene emisor `Avance Verifier Access CA`, y su raíz coincide con una de las 3 anclas (huella 1C:46:50:34…).
