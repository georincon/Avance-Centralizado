# Notaría · pruebas con curl

Las mismas pruebas de la colección, para la terminal. `JAR` guarda la cookie de sesión (la identificación con la wallet vive en esa sesión).

```bash
export BASE_URL=https://civica-desarrollo.avance.org.co
export JAR=$(mktemp)
export TIPO_DOCUMENTO=poder
```

## 01 · Estado del servicio

Comprobaciones rápidas, sin sesión ni claves. Si estas dos salen en verde, la notaría está arriba y se puede seguir.

### Salud

Responde `ok` si el portal de la notaría está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/salud" | jq .
```

### Portal (página principal)

La página que ve la persona: elegir el trámite, identificarse, llenarlo y recibirlo en la wallet. Crea la cookie de sesión.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/" | head -c 300; echo
```

## 02 · Identificarse con el PID

Antes de cualquier trámite la persona presenta su PID desde la wallet, a través del verifier de la notaría (`verifier-notaria`). La notaría crea la solicitud y devuelve el enlace `haip-vp://…` (y el QR); la wallet responde y la notaría consulta el resultado. Los datos de la persona (documento, nombres, nacimiento y foto) salen del PID verificado: nadie los escribe.

### Crear solicitud de identificación

Pide el PID con documento, nombres, apellidos, nacimiento y foto. Devuelve el `id`, el `enlace` para la wallet y el `qr` (imagen). Con `"dispositivo": "mismo"` la wallet vuelve al navegador al terminar.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

### Consultar el resultado

El navegador pregunta cada par de segundos si la wallet ya respondió: `esperando` hasta que la persona presenta el PID; después `listo` con sus datos.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/solicitud/$SOLICITUD_ID" | jq .
```

**Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es `esperando`.

### Quién soy (sesión)

Los datos de la persona identificada en esta sesión. Antes de identificarse con la wallet responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/yo"
```

### Cerrar la identificación

Olvida a la persona de esta sesión (hay que volver a identificarse).

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/notaria/api/salir" | jq .
```

### Propósito vacío: debe rechazar

Hay que decir `identificacion`, o `verificacion` con un tipo de documento.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{}'
```

### Verificar un tipo inexistente: debe rechazar

Un `tipo` que la notaría no emite no abre ninguna solicitud.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{"proposito": "verificacion", "tipo": "no-existe"}'
```

### Solicitud desconocida

Un id que no es de esta sesión no revela nada.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/solicitud/id-que-no-existe"
```

## 03 · Trámites (requieren identificarse)

Los nueve trámites notariales: declaración extraproceso, poder especial, reconocimiento de documento, fe de vida, autenticación de firma, copia de escritura, registro civil, convivencia y permiso de salida de menor. Cada trámite queda en el protocolo con un número y se entrega a la wallet con una oferta (QR) y un PIN de 4 dígitos. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno. Hay que ser mayor de edad (se comprueba con la fecha de nacimiento del PID).

### Mis trámites

El protocolo de la persona identificada, del más reciente al más antiguo. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/tramites"
```

### Otorgar un poder especial

Crea el trámite y devuelve la oferta: `enlace` (`openid-credential-offer://…`), `pin` de 4 dígitos y `vence`. La oferta dura 15 minutos y sirve una vez. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "poder", "datos": {"apoderado_nombre": "Carlos Andrés Rojas Pérez", "apoderado_documento": "1020304050", "facultades": "Representarme ante la entidad de salud para reclamar mis medicamentos.", "vigente_hasta": "2027-12-31"}}'
```

### Declaración extraproceso

Declaración bajo juramento (Decreto 1557 de 1989). Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "declaracion", "datos": {"finalidad": "Caja de compensación familiar", "declaracion": "Declaro bajo la gravedad del juramento que convivo con mi familia y dependo económicamente de mis ingresos."}}'
```

### Fe de vida

Constancia de que la persona vive (válida 30 días). Pide confirmar que comparece personalmente. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "fe_vida", "datos": {"confirmo": true, "entidad_destino": "Fondo de pensiones"}}'
```

### Reconocimiento de documento (solo la huella)

El archivo **no sale del navegador**: el portal calcula su SHA-256 y solo la huella, el nombre y el tamaño van al documento. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "reconocimiento", "datos": {"documento_nombre": "contrato-arrendamiento.pdf", "documento_sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", "documento_bytes": 48213}}'
```

### Trámite desconocido: debe rechazar

Un `tipo` que la notaría no emite. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "no-existe", "datos": {}}'
```

### Datos inválidos: debe rechazar

La notaría valida lo que se declara: aquí el documento del apoderado no son dígitos y la facultad es muy corta. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites" \
  -d '{"tipo": "poder", "datos": {"apoderado_nombre": "Carlos Rojas", "apoderado_documento": "abc", "facultades": "corta"}}'
```

### Pedir de nuevo la oferta

Vuelve a mostrar el QR y el PIN de un trámite que todavía no está en ninguna wallet. Si ya se entregó, responde 409: para recibirlo otra vez hay que pedir la **reexpedición** (la copia anterior queda revocada). Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites/$TRAMITE_NUMERO/oferta" \
  -d '{}'
```

### Reexpedir (la copia anterior queda revocada)

Para quien perdió la wallet: emite una nueva copia y revoca la anterior en la lista de estado. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites/$TRAMITE_NUMERO/oferta" \
  -d '{"reexpedir": true}'
```

### Anular el trámite (revocar)

Anula el trámite: su bit en la lista de estado pasa a 1 y quien lo verifique lo rechaza («el documento fue anulado»). En un poder es revocarlo. Necesita haberse identificado con la wallet (carpeta 02): sin esa sesión responde 401. Postman guarda la cookie de sesión solo, así que basta con usar la misma pestaña/entorno.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites/$TRAMITE_NUMERO/anular" \
  -d '{"motivo": "Prueba de Postman"}'
```

### Anular un trámite ajeno: debe rechazar

Solo se anula un trámite propio.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/tramites/999999/anular" \
  -d '{}'
```

## 04 · Verificar un documento notarial

Cualquiera puede comprobar un documento notarial: elige el tipo, la notaría se lo pide a la wallet de quien lo muestra y dice si es auténtico y sigue vigente. Un poder revocado no pasa. Cambia la variable `tipo_documento` por: declaracion, poder, reconocimiento, fe_vida, autenticacion, copia_escritura, registro_civil, convivencia o permiso_menor.

### Crear solicitud de verificación

Pide a la wallet el documento del tipo `{{tipo_documento}}` con todos sus datos.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{"proposito": "verificacion", "tipo": "$TIPO_DOCUMENTO"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

### Consultar el resultado

Mientras la wallet no responde dice `esperando`; después entrega los datos ya verificados (firma del emisor, lista PUBEAA, estado y prueba de posesión), o el motivo del rechazo.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/solicitud/$SOLICITUD_ID" | jq .
```

**Paso manual:** abre el enlace `haip-vp://…` con la wallet que tiene el documento y acepta compartirlo.

## 05 · Emisor OpenID4VCI 1.0 (lo que hace la wallet)

El emisor entrega cada documento como SD-JWT VC por **código preautorizado con PIN**. No hay `authorization_code`: los documentos se piden en el portal, no desde la wallet. El token exige la **WIA** de una wallet certificada (lista WP) y DPoP, y el documento queda ligado a la clave del dispositivo. Estas pruebas cubren lo público y los rechazos; la entrega completa necesita la wallet (o `verifiers/notaria/pruebas/probar-notaria.sh`).

### Metadatos del emisor

Lo primero que lee la wallet: los nueve documentos que ofrece (`co.notaria.*`), su formato (`dc+sd-jwt`), sus datos y los colores de cada tarjeta. Es pública y con CORS.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/notaria" | jq .
```

### Metadatos (ubicación alterna)

La misma metadata al final de la ruta, que algunas wallets también prueban.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/.well-known/openid-credential-issuer" | jq .
```

### Servidor de autorización

Dónde pedir el token y qué exige (solo código preautorizado).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/oauth-authorization-server/notaria" | jq .
```

### Oferta desconocida

La oferta (la que va en el QR) dura 15 minutos y sirve una vez; una desconocida o vencida da 404.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/notaria/oferta/no-existe"
```

### Token con otro tipo de grant: debe rechazar

Solo se acepta `urn:ietf:params:oauth:grant-type:pre-authorized_code`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST "$BASE_URL/notaria/token" \
  -d grant_type=authorization_code \
  -d code=x
```

### Token con código inexistente: debe rechazar

Un código que venció o ya se usó: se pide otra oferta en la notaría.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST "$BASE_URL/notaria/token" \
  -d grant_type=urn:ietf:params:oauth:grant-type:pre-authorized_code \
  -d pre-authorized_code=no-existe \
  -d tx_code=0000
```

### Nonce

Nonce de un solo uso para que la wallet firme su prueba de posesión al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/notaria/nonce" | jq .
```

### Credencial sin token: debe rechazar

Sin `Authorization` no se entrega nada.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/credencial" \
  -d '{"credential_configuration_id": "co.notaria.poder_especial"}'
```

### Lista de estado (revocación)

Lista firmada (`statuslist+jwt`) con un bit por trámite: 1 = anulado o reexpedido. La consultan los verifiers para saber si el documento sigue vigente. No se guarda en caché.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/estado/1" | head -c 300; echo
```
