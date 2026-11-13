# Fase 3: discoteca (2026-10-21)

## Hecho
- Instancia `verifier-discoteca` (`_comun/instancias/discoteca`, actor #22 del tl-publisher), publicada en
  `https://civica-desarrollo.avance.org.co/verifier-discoteca/`. Retención de 5 minutos y solicitudes que vencen en 5.
- App de la puerta (`verifiers/discoteca`), publicada en `https://civica-desarrollo.avance.org.co/discoteca/`
  (alias `app-discoteca` en `avance-eudi`, regla `/discoteca` en `srv-nw-nginx`). Sin volúmenes, `read_only`,
  `cap_drop: ALL`. Solo pide `age_equal_or_over.18`.
- Comprobado: certificado de acceso emitido por `Civica RP Access CA` con EKU `clientAuth` + `1.0.18013.5.1.6` y
  SAN del dominio; la solicitud (`request.jwt`) sale en ES256 con `x5c` de solo la hoja, `client_id`
  `x509_hash`, `direct_post.jwt` y el DCQL esperado; el trust-validator da la hoja por confiable
  (`WalletRelyingPartyAccessCertificate`); la puerta crea la solicitud y devuelve el QR.

## Arreglos que hicieron falta en el ecosistema
1. `srv-tl-publisher/docker-compose`: el servicio `pki` no recibía `PKI_ISSUE_API_KEY` (503 al registrar).
2. Mismo compose: `pki` montaba la sub-CA `rp` en solo lectura, pero emitir necesita escribir `serial`,
   `index.txt` y `newcerts/`. Ahora solo `subca/rp` es de escritura; las demás siguen en solo lectura.
3. `admin.py` (`registrar_verifier`): devolvía `actor_id = 0` (`lastrowid` tras insertar con id explícito) y el
   certificado quedaba colgado de un actor inexistente, con el actor mostrado como «retirado». Corregido, y la fila
   del certificado 15 se movió al actor 22 (única fila huérfana).

## Pendiente
- Falta la prueba con una credencial: hay que emitir un PID **nuevo** (los anteriores no traen `age_equal_or_over`)
  con la Registraduría arriba y presentarlo con la wallet v2 (Fase 6) o con un simulador de wallet
  (`probar-presentacion`, aún sin portar).
- La hoja del verifier no aparece como ancla en RPREG hasta republicar; no es necesario porque la CA ya es ancla.

## Resultado de la prueba con la wallet v2 (2026-10-22)

Flujo completo comprobado con el teléfono (log de la wallet por adb + log del proxy + eventos del verifier):

1. **Bloqueo de la wallet** («Presentación bloqueada», `Untrusted x5c`): la lista RPREG se publicaba en un JSON propio y la
   wallet exige el bloque `LoTE` (ETSI TS 119 602). Corregido con `srv-tl-publisher/app/lote.py`. Además la wallet guarda
   cada lista 24 h: hubo que **borrar el caché de la app** (no los datos) para que bajara la nueva.
2. **Confianza wallet→verifier**: `validateCertificationTrustPath: result=Trusted`, ancla `Civica RP Access CA`.
3. **Emisión del PID**: había fallado por el **Wallet Provider caído** (502 en `/wallet-provider/wallet-instance-attestation/jwk`;
   `spartans-*` salieron con 137 durante la noche). Tras levantarlo, la emisión de mDoc y SD-JWT funcionó.
4. **Presentación**: la wallet envió exactamente 2 disclosures: `age_equal_or_over` (objeto con solo digests) y
   `["18", true]`. No viajó nombre, documento ni fecha de nacimiento. `iss` = `/pid-registraduria`, `vct` = `urn:eudi:pid:1`.
   El verifier consultó al trust-validator (`POST /trust`) y aceptó la respuesta (`Wallet response posted`).
5. Primer intento rechazado: `ContainsInvalidKeyBindingJwt: 'iat' is not within the acceptable time window` (el POST tardó
   ~9 s con el verifier en frío). El segundo pasó. Pendiente ver si se repite con el verifier ya caliente.

Observaciones:
- El historial de la wallet muestra «Compartiste 10 datos» y dos entradas «Discoteca Neón» (una por intento, incluso la rechazada).
  Es la forma en que la wallet cuenta; lo realmente enviado son las 2 disclosures de arriba.
- La puerta guarda el primer resultado de una solicitud: si el verifier rechaza el primer intento, esa pantalla queda en
  «error» aunque la wallet reintente con el mismo QR. Con un QR nuevo por persona no ocurre.
