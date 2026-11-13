# postman/notaria — pruebas de la notaría de ejemplo

Colección de Postman de [`verifiers/notaria`](../../verifiers/notaria) (**Notaría Avance**): el portal de trámites, la identificación con el PID, la verificación de documentos notariales y el emisor OpenID4VCI.
Sale del mismo marco que la colección general (`../marco.py`); cada prueba trae su explicación y sus comprobaciones automáticas. No lleva claves.

| Archivo | Qué es |
|---|---|
| `Notaria.postman_collection.json` | La colección: 5 carpetas, 31 pruebas |
| `Notaria.postman_environment.json` | Entorno (`base_url`, `tipo_documento`, y variables que las pruebas llenan solas) |
| `PRUEBAS-CURL.md` | Las mismas pruebas con `curl`, con cookie de sesión |
| `generar.py` | Generador: de aquí salen los tres archivos anteriores |

## Empezar

1. En Postman: **Import** → `Notaria.postman_collection.json` y `Notaria.postman_environment.json`; elige el entorno.
2. Carpeta **01 · Estado del servicio** → *Run folder*: debe salir todo en verde.
3. Para probar en tu equipo en vez de en civica-desarrollo, cambia `base_url` a `https://localhost`.

## Qué cubre cada carpeta

| Carpeta | Qué prueba | ¿Necesita la wallet? |
|---|---|---|
| 01 · Estado del servicio | Salud y portal | no |
| 02 · Identificarse con el PID | Crear la solicitud (enlace `haip-vp://…` y QR), consultar el resultado, quién soy, salir; rechazos de propósito y tipo | para el último paso (presentar el PID) |
| 03 · Trámites | Poder, declaración, fe de vida, reconocimiento; oferta, reexpedición y anulación; rechazos por datos inválidos | sí: sin identificarse responden **401** (también está verificado) |
| 04 · Verificar un documento notarial | Pedir cualquiera de los nueve tipos (`tipo_documento`) y consultar el resultado | para presentar el documento |
| 05 · Emisor OpenID4VCI | Metadatos, servidor de autorización, nonce, lista de estado y los rechazos del token y de la credencial | la entrega completa sí |

Los nueve tipos de `tipo_documento`: `declaracion`, `poder`, `reconocimiento`, `fe_vida`, `autenticacion`, `copia_escritura`, `registro_civil`, `convivencia`, `permiso_menor`.

## Qué no se puede automatizar aquí

- **Presentar el PID** (paso de la carpeta 02): lo hace la wallet escaneando el QR o abriendo el enlace. Hasta entonces el resultado es `esperando` y los trámites de la carpeta 03 responden 401.
- **Recibir el documento**: el token exige la WIA de una wallet certificada y DPoP, y la credencial una prueba de posesión de clave. Sin teléfono se prueba con `verifiers/notaria/pruebas/probar-notaria.sh`.
- Postman guarda la cookie de sesión sola: identifícate y ejecuta la carpeta 03 con el mismo entorno y sin cerrar la sesión.

## Regenerar

```bash
python3 postman/notaria/generar.py              # solo genera
python3 postman/notaria/generar.py --verificar  # además ejecuta cada prueba contra https://localhost
```
