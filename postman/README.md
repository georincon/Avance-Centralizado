# postman/ — pruebas del ecosistema EUDI de Avance

Todo lo necesario para probar los servicios del ecosistema en `https://civica-desarrollo.avance.org.co`, con Postman o desde la terminal.
Cada proyecto es una carpeta, cada prueba trae su explicación, sus comprobaciones automáticas y un ejemplo de respuesta real.

| Archivo | Qué es | ¿Se comparte? |
|---|---|---|
| `Avance-EUDI.postman_collection.json` | La colección: 23 carpetas (una por proyecto) y 181 pruebas | sí |
| `Avance.postman_environment.json` | Entorno de Postman sin claves (las variables sensibles vienen vacías) | sí |
| `GUIA-POSTMAN.md` | Cómo importar y usar la colección, y cada prueba explicada | sí |
| `PRUEBAS-CURL.md` | Las mismas pruebas con `curl`, por proyecto, paso a paso | sí |
| `Avance-local.postman_environment.json` | Entorno con las claves ya puestas (importar y listo) | **no** |
| `DATOS-LOCALES.md` | Claves, datos de conexión de las bases (Postgres y MySQL), datos de prueba y catálogo de credenciales | **no** |
| `pruebasintegracion/` | API REST de órdenes de emisión para la plataforma de notarías (SIGNO QA): documento de consumo, modelo entidad-relación, colección de 24 pruebas y guía curl | sí (menos el entorno `-local`) |
| `notaria/` | Colección propia de `verifiers/notaria` (31 pruebas: portal, trámites, verificación y emisor), con su entorno, guía y generador | sí |
| `generar.py`, `marco.py`, `definiciones.py` | Generador: de aquí salen todos los archivos anteriores | sí |

Los dos archivos que **no se comparten** están en `.gitignore`: llevan claves y datos personales de prueba.

## Empezar en 2 minutos

1. En Postman: **Import** → `Avance-EUDI.postman_collection.json` y `Avance-local.postman_environment.json` (para compartir con el grupo: `Avance.postman_environment.json`).
2. Elige el entorno arriba a la derecha.
3. Clic derecho en la carpeta **`00 · Humo`** → *Run folder*. Debe salir todo en verde.

## Guion de demostración para el grupo (≈ 15 minutos)

1. **¿Está todo en pie?** — Carpeta `00 · Humo` en el *Runner*: 22 comprobaciones en segundos.
2. **La capa de confianza** — Carpeta `Confianza · tl-publisher`: las listas firmadas (PID, QEAA, PUBEAA, RPREG, LOTL). Luego `Confianza · trust-validator`: se toma la cadena real del verifier del banco y se valida (`trusted: true`); una cadena que no es un certificado se rechaza.
3. **Identidad antes de emitir** — Carpeta `Wallet · onboarding`: se crea una sesión, se verifica el contacto con el código (`devCode`), se acepta el consentimiento y queda auditado.
4. **Qué ofrece cada emisor** — Carpetas `Emisor · Registraduría` y `Emisor · UIS`: metadatos OpenID4VCI, claves y tipos de credencial.
5. **Presentar una credencial (OpenID4VP)** — Carpeta `Verificación · verifier OpenID4VP`: se crea una solicitud DCQL, aparece el enlace `haip-vp://…`; *con la wallet en mano* se abre y se acepta; la consulta devuelve los datos ya verificados. Cambiando la variable `instancia` se repite con cualquier entidad.
6. **Casos reales** — Carpetas del banco, contratación (nivel de formación → contrato → firma con Andes SCD), discoteca (solo «mayor de 18», sin guardar nada), EPS, notaría, gimnasio, aerolínea…
7. **Revocación** — Carpeta `Revocación · listas de estado`: la lista firmada que consultan los verifiers; cada entidad tiene la suya (`/<entidad>/estado/1`).
8. **Los rechazos también se prueban** — cada carpeta incluye casos que *deben* fallar (sin token → 401, sin clave → 401, datos inválidos → 400).

## Regenerar los archivos

```bash
python3 postman/generar.py --verificar
```

Ejecuta **cada prueba contra el sistema real** (`https://localhost`) antes de escribir los archivos, así lo documentado es lo que de verdad ocurre. Sin `--verificar` se generan igual, pero sin los ejemplos de respuesta real. Las claves se leen de los `.env` y de los contenedores en el momento de generar, y solo van a los archivos locales.

Para agregar o cambiar una prueba, edita `definiciones.py` (una línea por prueba) y vuelve a generar.

## Observaciones de la última verificación (27-oct-2026)

- **177 pruebas ejecutadas, todas bien**; 4 no se ejecutan a propósito porque cambian el sistema real o piden claves de administrador (reiniciar un proyecto, publicar una lista, token de Keycloak, sello de tiempo con archivo).
- **`trust-validator`:** el certificado de acceso del verifier del banco salió `trusted: true` con *todos* los contextos (`PID`, `QEAA`, `PubEAA`, `WalletProviderAttestation`), no solo con el suyo. Conviene revisar si el validador debe distinguir los roles.
- **`keycloak-uis`** estaba apagado durante la verificación (el panel apaga uno de los dos Keycloak con «Activar consola»); su prueba acepta 200 o 503.
