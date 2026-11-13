# Gimnasio de ejemplo (Cumbre Gym)

**Cumbre Gym** es un gimnasio ficticio que emite la **tarjeta de socio** a la wallet y controla el **ingreso y el egreso**
de sus sedes verificándola. Como la EPS y la notaría, muestra las dos caras del ecosistema en un mismo servicio:

- **Verifier.** Quien se hace socio se identifica presentando su PID desde la wallet (`verifier-gym`); en la puerta, la
  pantalla de entrada y la de salida piden la tarjeta de socio con su verifier.
- **Emisor.** La tarjeta se entrega como un documento de la wallet (SD-JWT VC por OpenID4VCI), firmado por el gimnasio,
  registrado en la lista **QEAA** y anulable con su status list (renovar, suspender o revocar la deja sin efecto).

Se levanta con `./levantar.sh` o desde la tarjeta «Gimnasio» del panel de control.

| Dirección | Qué es |
|---|---|
| `/gym/` | Portal del socio: planes, hacerse socio, renovar, recibir la tarjeta, ver visitas |
| `/gym/acceso` | Menú de la puerta: elige la sede y abre la pantalla de **entrada** o de **salida**; aforo en vivo |
| `/gym/acceso/entrada?sede=…` | Pantalla de entrada de una sede (QR + resultado a pantalla completa) |
| `/gym/acceso/salida?sede=…` | Pantalla de salida |
| `/gym/admin` | Aforo en vivo, quién está dentro, socios (suspender, reactivar, renovar), historial de accesos, cierre de sede |

## Cómo funciona el control de ingreso y egreso

1. El socio abre su wallet y escanea el QR de la pantalla. La wallet muestra qué se pide (número de socio, nombre, plan,
   estado, vigencia y **foto**: **no** el documento) y el socio acepta.
2. El verifier valida la firma, que el emisor es de confianza y que la tarjeta no está anulada (status list). El servidor
   comprueba además, en su sistema, que la membresía esté **activa y vigente** (la tarjeta puede ir por delante de lo que
   el gimnasio sabe: un socio suspendido no entra aunque conserve la tarjeta).
3. **Entrada:** se rechaza si ya hay un ingreso abierto (no se entra dos veces sin salir, ni por otra sede) o si el aforo de
   la sede está completo. **Salida:** siempre se deja salir; si no constaba el ingreso se anota igual.
4. La pantalla dice «puede pasar» (verde en la entrada, naranja en la salida) o «no puede pasar» (rojo) con el motivo, y
   vuelve sola a un código nuevo a los segundos. Muestra la **foto de la tarjeta** para que quien atiende la compare con la
   persona que tiene delante.

Quien ingresó y no marcó salida en 8 horas se da por salido (se fue sin pasar por el torniquete). El administrador puede
**cerrar una sede** para marcar la salida de todos los que siguen dentro.

## Seguridad y privacidad

- **Clave de puerta.** Las pantallas de entrada y salida piden la contraseña de administración (`ADMIN_CLAVE`): sin ella,
  cualquiera podría abrir la dirección desde su casa y registrar un ingreso sin estar en la sede. Cada dispositivo la
  escribe una vez. Cinco fallos seguidos frenan los intentos de esa IP durante un minuto.
- **El registro de accesos** guarda el número de socio, el tipo (ingreso, egreso, salida forzada, denegado), la sede, la hora
  y el motivo del rechazo. **No guarda el documento ni el nombre**: el nombre se une al mostrárselo al administrador. En el
  log del servidor tampoco salen documentos.
- La entrada **no pide el documento** del socio.
- **La foto.** Se toma del PID con el que el socio se afilia, se guarda en su ficha (solo para ponerla en la tarjeta) y viaja
  en la tarjeta, en el dato `picture`, que es el nombre que la wallet muestra como imagen. En la puerta se enseña en la
  pantalla y **no se guarda** en el registro de accesos ni sale en el log. Un socio afiliado sin foto la recibe al volver a
  pedir su tarjeta.
- **Límite conocido:** quien compara la foto con la cara es una persona; no hay reconocimiento facial. Si la tarjeta no trae
  foto, la pantalla lo avisa.

## Planes, sedes y aforo

Los planes (día, mensual, trimestral, anual) y las sedes (Cumbre Centro, Norte y Sur) están en `src/planes.js`. El pago es
**simulado**: el portal no cobra nada. El aforo máximo por sede sale de `AFORO_MAXIMO` en `.env` (60 por defecto).

Al **renovar** o **reactivar**, la tarjeta que el socio ya tenía queda reemplazada (su bit de la status list pasa a 1) y
recibe una nueva; al **suspender**, queda revocada. La tarjeta vale hasta el final de la membresía.

## Datos

`datos/socios.json` guarda los socios, sus membresías, las tarjetas entregadas y el registro de accesos (los últimos 5000).
Los socios se afilian solos en el portal con su PID: no hay semilla. `secretos/` tiene la CA y la clave de firma del gimnasio.

## Prueba sin teléfono

```bash
docker exec -i gym-gym-1 node --input-type=module < pruebas/probar-gym.mjs
```

Levanta otro servidor y un verifier simulado que entrega tarjetas reales (firmadas con la clave del gimnasio) y recorre
hacerse socio (con foto), entrar, salir, el aforo (con un máximo de 2), la suspensión, el vencimiento, los rechazos y la
privacidad del registro (58 comprobaciones). No prueba la emisión OpenID4VCI ni el escaneo con la wallet real: eso hay que verlo con el teléfono.

## Tras el primer arranque

El trust-validator tarda hasta 20 minutos en ver la lista nueva (verificar tarjetas falla mientras tanto). En la wallet:
Ajustes › Listas de confianza › Actualizar.
