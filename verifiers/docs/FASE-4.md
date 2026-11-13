# Fase 4: banco (2026-10-22)

## Hecho
- `verifiers/banco/` (app: ingreso y apertura de cuenta con la wallet, sin contraseñas) publicada en
  `https://civica-desarrollo.avance.org.co/banco/` (alias `app-banco` en `avance-eudi`, regla `/banco` en
  `srv-nw-nginx`; `read_only`, `cap_drop: ALL`). Su verifier, `verifier-banco` (actor #23, ya con su certificado
  correctamente asociado), guarda cada presentación solo en memoria 30 min (`CONSERVAR_PRESENTACIONES=PT30M`) y sus
  QR vencen a los 5 (`VIGENCIA_SOLICITUD=PT5M`).
- Comprobado sin teléfono: las páginas responden, `/cuenta` sin sesión redirige al inicio, la cookie es
  `banco_sesion` (nombre propio: el borde público reescribe el `Path` a `/` y comparte cookies entre apps del dominio)
  y cada solicitud pide exactamente lo previsto.

## Adaptaciones al PID de la Registraduría
- **Datos que se piden.** El banco del ejemplo pedía `email` y `date_of_expiry`. El PID de la Registraduría **no los
  trae** (los emite vacíos) y, en DCQL, si la consulta incluye un dato que el documento no tiene, la wallet no encuentra
  ningún documento que la cumpla («documento no disponible»). Ahora:
  - ingreso: `personal_administrative_number`, `given_name`, `family_name`;
  - apertura: lo anterior + `birthdate` + `picture`.
- La página de inicio ya no promete correo ni vencimiento, y la cuenta no muestra filas vacías. El código sigue
  tolerando esos datos por si otro emisor los trae.
- Sin `date_of_expiry`, el banco no puede comprobar la vigencia por fecha: la vigencia la comprueba el verifier con la
  status list del emisor.

## Pendiente
- Prueba con la wallet v2 (ingreso, apertura y «mismo teléfono» con `wallet_response_redirect_uri_template`).
  Necesita la Registraduría arriba: el verifier comprueba el estado del PID en `status-list-reg`.
- `clientes.json` guarda datos personales de la prueba (nombre, documento, foto). Es un ejemplo; no usarlo con datos reales.
