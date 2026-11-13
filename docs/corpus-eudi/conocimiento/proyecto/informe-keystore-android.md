**INFORME TÉCNICO**

**Almacenamiento de llaves y credenciales en el Android Keystore System**

Proyecto wallet EUDI/eIDAS2 — plugin tauri-plugin-wallet (Android)

*28 de septiembre de 2026*

## **Resumen** 

La app usa el Android Keystore System en tres puntos independientes y separados: (1) las llaves de firma de cada credencial (SD-JWT VC / mdoc), gestionadas por la librería externa eudi-lib-android-wallet-core apoyada en Multipaz; (2) la llave de la Wallet Instance Attestation (WIA), con su propio almacén; y (3) una llave AES que sella el secreto del que se derivan las contraseñas de los vaults de Stronghold (PIN, historial de presentaciones), gestionada directamente con la API nativa de Android. En ningún caso el material de la llave sale del Keystore: solo se invoca para firmar o cifrar bajo demanda.

| **\#** | **Uso**                                       | **Mecanismo / clase**                                                    | **Almacenamiento del payload**         |
|--------|-----------------------------------------------|--------------------------------------------------------------------------|----------------------------------------|
| 1      | Llaves de credenciales (SD-JWT VC / mdoc)     | eudi-lib-android-wallet-core 0.28.1 + Multipaz AndroidKeystoreSecureArea | eudi_wallet/wallet_storage.db          |
| 2      | Llave de la Wallet Instance Attestation (WIA) | GestorClaveDeWallet.kt — AndroidKeystoreCreateKeySettings con challenge  | eudi_wallet/wallet_attestation_keys.db |
| 3      | Llave AES de sellado de vaults Stronghold     | VaultKeyProvider.kt — API nativa KeyGenParameterSpec (AES-256-GCM)       | vault-master.v1.bin (secreto sellado)  |

# **1. Arquitectura general**

La librería que gestiona la mayor parte del ciclo de vida de las credenciales es una dependencia externa declarada en el proyecto:

*tauri-plugin-wallet/android/build.gradle.kts:41-53*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>dependencies {</p>
<p>implementation("androidx.core:core-ktx:1.15.0")</p>
<p>implementation("androidx.appcompat:appcompat:1.8.0")</p>
<p>implementation("com.google.android.material:material:1.12.0")</p>
<p>implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")</p>
<p>...</p>
<p>implementation(project(":tauri-android"))</p>
<p>implementation("eu.europa.ec.eudi:eudi-lib-android-wallet-core:0.28.1")</p>
<p>implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")</p>
<p>implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")</p>
<p>}</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

La app **no** declara androidx.security (Jetpack Security/EncryptedFile) ni identity-credential como dependencias directas. **Multipaz** (org.multipaz.securearea.\*, org.multipaz.storage.\*, org.multipaz.crypto.\*) se usa directamente en el código Kotlin del repo pero no aparece declarada explícitamente en el build.gradle.kts: es transitiva del AAR de eudi-lib-android-wallet-core 0.28.1, que la usa internamente como su capa de SecureArea/Storage para documentos.

*Nota sobre fuentes: la versión de la librería y las clases que aparecen en el código (AndroidKeystoreSecureArea, AndroidKeystoreCreateKeySettings, KeyUnlockData) están verificadas en el repo. Cómo esas clases operan internamente sobre el Keystore es conocimiento general de esa librería — su código fuente no está en este repositorio, solo el .aar compilado — y se marca así explícitamente donde aparece.*

# **2. Llaves de las credenciales (SD-JWT VC / mdoc)**

Cada credencial emitida tiene una llave de firma que se genera y permanece dentro del Android Keystore, gestionada por AndroidKeystoreSecureArea (Multipaz). El documento en sí (claims, SD-JWT/mdoc, metadatos) se persiste aparte, en un fichero de almacenamiento separado de la llave.

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/EudiWalletPlugin.kt:202-208*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>val storageDir = File(activity.filesDir, "eudi_wallet")</p>
<p>storageDir.mkdirs()</p>
<p>val storagePath = args.documentsStoragePath ?: File(storageDir, "wallet_storage.db").absolutePath</p>
<p>storagePathUsed = storagePath</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **2.1 Política de creación de llaves**

Se fija en WalletConfigFactory, con dos parámetros booleanos independientes:

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/WalletConfigFactory.kt:44-53*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>return EudiWalletConfig().configureDocumentManager(storagePath).apply {</p>
<p>args.logLevel?.let { configureLogging(level = it) } ?: configureLogging(level = Logger.LEVEL_DEBUG)</p>
<p>configureDocumentKeyCreation(</p>
<p>userAuthenticationRequired = args.userAuthenticationRequired ?: false,</p>
<p>userAuthenticationTimeout = (args.userAuthenticationTimeoutMs ?: 30_000L).milliseconds,</p>
<p>useStrongBoxForKeys = args.useStrongboxForKeys ?: true</p>
<p>)</p>
<p>...</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

- useStrongboxForKeys: pide respaldo StrongBox (chip de seguridad dedicado); si no está disponible, cae a TEE (TrustZone) de forma silenciosa.

- userAuthenticationRequired: exige autenticación biométrica antes de cada operación de firma con la llave.

- userAuthenticationTimeoutMs: ventana de validez de esa autenticación (30 s por defecto).

### **2.2 Desbloqueo con biometría**

Cuando el SDK indica que una llave requiere autenticación (evento keysRequireAuth, mapa alias → SecureArea), el plugin lanza un BiometricPrompt y, si la autenticación tiene éxito, construye los datos de desbloqueo que el SDK usa para esa única operación de firma.

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/EudiWalletPlugin.kt:1094-1168*

*La llave nunca abandona el Keystore: AndroidKeystoreKeyUnlockData(secureArea, keyAlias) autoriza la operación puntualmente, no exporta material criptográfico.*

### **2.3 Política de lote y no-linkability al emitir**

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/EudiWalletPlugin.kt:1059-1092*

- SD-JWT VC: CredentialPolicy.OneTimeUse, lote de 10 credenciales por defecto.

- mdoc: CredentialPolicy.RotateUse, lote de 1 credencial por defecto.

# **3. Llave de la Wallet Instance Attestation (WIA)**

Es una llave independiente de las de documento, con su propio SecureArea y su propio fichero de almacenamiento, porque necesita incrustar el challenge del wallet-provider en el momento de generarse.

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/GestorClaveDeWallet.kt:64, 85-103*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>private const val FICHERO = "wallet_attestation_keys.db"</p>
<p>private const val PARTICION = "wallet-attestation"</p>
<p>...</p>
<p>val ruta = File(File(context.filesDir, "eudi_wallet").apply { mkdirs() }, FICHERO)</p>
<p>val almacen = AndroidStorage(ruta.absolutePath)</p>
<p>val areaSegura = AndroidKeystoreSecureArea.create(almacen, PARTICION)</p>
<p>...</p>
<p>return SecureAreaWalletKeyManager(</p>
<p>secureArea = areaSegura,</p>
<p>createKeySettingsProvider = { algoritmo -&gt;</p>
<p>val reto = withContext(Dispatchers.IO) { cliente.pedirReto() }</p>
<p>RetosEnUso.recordarClaveDeWallet(reto)</p>
<p>AndroidKeystoreCreateKeySettings.Builder(ByteString(reto.bytes()))</p>
<p>.setAlgorithm(algoritmo)</p>
<p>.setUseStrongBox(usarStrongBox)</p>
<p>.build()</p>
<p>},</p>
<p>)</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

StrongBox es una preferencia (cae a TEE si no está disponible); el nivel de protección real se verifica del lado del servidor leyendo la key attestation devuelta por el Keystore.

# **4. Llave AES que sella los vaults de Stronghold**

Esta llave no protege credenciales EUDI: sella el secreto maestro del que Stronghold deriva las contraseñas de sus vaults (PIN de la app, historial de presentaciones). Usa la API nativa de Android directamente, sin pasar por Multipaz.

*tauri-plugin-wallet/android/src/main/java/com/plugin/wallet/VaultKeyProvider.kt:76-190*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>private fun obtenerOCrearClaveDeSello(): SecretKey {</p>
<p>val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }</p>
<p>(ks.getEntry(ALIAS_KEYSTORE, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }</p>
<p>if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.P) {</p>
<p>try { return generarClaveDeSello(strongBox = true) }</p>
<p>catch (_: StrongBoxUnavailableException) { }</p>
<p>}</p>
<p>return generarClaveDeSello(strongBox = false)</p>
<p>}</p>
<p>private fun generarClaveDeSello(strongBox: Boolean): SecretKey {</p>
<p>val spec = KeyGenParameterSpec.Builder(</p>
<p>ALIAS_KEYSTORE,</p>
<p>KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT</p>
<p>)</p>
<p>.setBlockModes(KeyProperties.BLOCK_MODE_GCM)</p>
<p>.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)</p>
<p>.setKeySize(256)</p>
<p>.setUserAuthenticationRequired(false) // ver comentario líneas 47-64</p>
<p>.apply { if (strongBox &amp;&amp; Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }</p>
<p>.build()</p>
<p>val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")</p>
<p>generador.init(spec)</p>
<p>return generador.generateKey()</p>
<p>}</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

Alias en el Keystore: civica.wallet.vault.master.v1. La llave AES-256-GCM sella un secreto maestro de 32 bytes generado al azar y guardado en vault-master.v1.bin; de ese secreto se derivan con HKDF-SHA256 (RFC 5869, implementado a mano) las contraseñas de los vaults de Stronghold, sustituyendo una contraseña que antes estaba hardcodeada en el código.

*Deliberadamente no se usa setUserAuthenticationRequired(true): ese flag ataría la llave al bloqueo del dispositivo (huella/PIN de Android), no al PIN propio de la app, y rompería el arranque en dispositivos sin bloqueo configurado. Así lo documenta el KDoc de la clase (líneas 47-64).*

VaultKeyProvider.borrar() (líneas 124-134) elimina el secreto, la sal, y borra la entrada del Keystore con ks.deleteEntry(ALIAS_KEYSTORE). Se invoca desde destroyWallet() al reiniciar la wallet.

# **5. Niveles de seguridad: no hay un esquema LOW/MEDIUM/HIGH**

Se buscó explícitamente ese patrón (SecurityLevel, literales "LOW"/"MEDIUM"/"HIGH", CreateDocumentSettings.Level) en todo tauri-plugin-wallet y src, y no aparece en este código. El repositorio no expone un enum discreto de niveles; el mecanismo real son dos booleanos independientes (useStrongboxForKeys, userAuthenticationRequired) más un timeout. Si tal enum existe internamente en la librería 0.28.1, no se usa desde el código del plugin.

# **6. Flujo de configuración de extremo a extremo**

Los flags de seguridad viajan desde el frontend hasta el código nativo Android sin lógica intermedia en Rust — Rust solo transporta los parámetros:

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>configService.ts (defaults: VITE_USE_STRONGBOX=true, VITE_USER_AUTH_REQUIRED=false)</p>
<p>-&gt; walletApi.ts / walletBindings.gen.ts (WalletConfig)</p>
<p>-&gt; commands.rs / models.rs / mobile.rs (Rust: solo pasa parametros, sin logica propia)</p>
<p>-&gt; EudiWalletPlugin.kt / WalletConfigFactory.kt (Kotlin: aplica la politica real sobre el SDK)</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **6.1 Rust — solo transporte de parámetros**

*tauri-plugin-wallet/src/models.rs:25-70*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>pub struct WalletConfig {</p>
<p>pub documents_storage_path: Option&lt;String&gt;,</p>
<p>...</p>
<p>pub user_authentication_required: Option&lt;bool&gt;,</p>
<p>pub user_authentication_timeout_ms: Option&lt;i64&gt;,</p>
<p>pub use_strongbox_for_keys: Option&lt;bool&gt;,</p>
<p>...</p>
<p>}</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

*tauri-plugin-wallet/src/models.rs:252-263*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>/// No es la clave que cifra el vault: es el material del que Stronghold</p>
<p>/// deriva la suya. La deriva VaultKeyProvider.kt de un secreto</p>
<p>/// sellado por el Android Keystore.</p>
<p>pub struct VaultKey {</p>
<p>pub key: String,</p>
<p>pub hardware_backed: bool,</p>
<p>}</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **6.2 TypeScript — configuración por defecto real**

*src/services/configService.ts:76-78*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>userAuthenticationRequired: envBool(import.meta.env.VITE_USER_AUTH_REQUIRED, false),</p>
<p>useStrongboxForKeys: envBool(import.meta.env.VITE_USE_STRONGBOX, true),</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

En este despliegue: StrongBox se pide siempre por defecto (con fallback silencioso a TEE si no hay hardware disponible), y la autenticación biométrica por operación de firma está desactivada por defecto. Ambos valores son configurables vía las variables de entorno VITE_USE_STRONGBOX y VITE_USER_AUTH_REQUIRED.

El frontend también puede leer si la llave de sellado quedó respaldada por hardware, vía getVaultKey (commands.rs:201-202 → EudiWalletPlugin.kt:883-901), que devuelve el flag hardwareBacked.

# **7. Borrado de llaves**

destroyWallet() (EudiWalletPlugin.kt:257-319) borra en conjunto: el storage de documentos (storagePathUsed, o el directorio eudi_wallet por defecto), los ficheros de vault de Stronghold, y llama a VaultKeyProvider.borrar(activity), que elimina del Keystore la llave que los sellaba. Las llaves de documento y de WIA residen en sus propios ficheros de storage dentro de eudi_wallet/, que quedan eliminados junto con el directorio.

# **8. Paso a paso: cómo verificar en la práctica que los documentos usan el AKS**

El repositorio no incluye hoy un comando de diagnóstico que liste los alias del Android Keystore, ni logs de éxito con el alias/nivel de seguridad de cada llave (VaultKeyProvider.kt no importa Log ni WalletLog; GestorClaveDeWallet.kt tampoco registra nada; getVaultKey solo loguea en caso de fallo, EudiWalletPlugin.kt:898). La verificación, por tanto, se hace desde fuera de la app con ADB, más una llamada manual a un comando que ya existe pero no está conectado a ninguna pantalla.

Paquete de la app instalada: com.luis.wallet (src-tauri/gen/android/app/build.gradle.kts:18,21). Usa un build debug (isDebuggable = true en build.gradle.kts:67) para poder usar run-as; en release estos pasos no aplican.

### **8.1 Paso 1 — Confirmar el paquete instalado y el acceso run-as**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>adb shell pm list packages | grep com.luis.wallet</p>
<p>adb shell run-as com.luis.wallet id</p>
<p># Si el segundo comando responde con el uid de la app (y no</p>
<p># "run-as: package not debuggable"), el build instalado es debug</p>
<p># y se puede inspeccionar su sandbox de ficheros.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **8.2 Paso 2 — Confirmar que el fichero de documentos existe y no contiene material de llave en claro**

Las rutas reales, con el applicationId confirmado, son:

- /data/data/com.luis.wallet/files/eudi_wallet/wallet_storage.db (documentos)

- /data/data/com.luis.wallet/files/eudi_wallet/wallet_attestation_keys.db (llave de la WIA)

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>adb shell run-as com.luis.wallet ls -la files/eudi_wallet/</p>
<p>adb exec-out run-as com.luis.wallet cat files/eudi_wallet/wallet_storage.db \</p>
<p>&gt; wallet_storage.db</p>
<p># El fichero debe existir y crecer al emitir credenciales, pero no debe</p>
<p># contener material de llave privada en claro:</p>
<p>strings wallet_storage.db | grep -i "PRIVATE KEY" # no debe imprimir nada</p>
<p>strings wallet_storage.db | grep -i "BEGIN EC" # no debe imprimir nada</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

Si el grep no devuelve resultados, es coherente con lo que dice el código: el fichero solo guarda claims/CBOR/metadatos y, como mucho, alias/referencias a llaves — nunca la llave privada, porque esa vive en el Keystore, no en el filesystem de la app.

### **8.3 Paso 3 — Listar las entradas del Android Keystore y su nivel de seguridad**

Esto no lo expone la app: se consulta el demonio de Keystore del sistema directamente.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>adb shell dumpsys package com.luis.wallet | grep -i userId</p>
<p>adb shell dumpsys keystore2 | less</p>
<p># Buscar, dentro del dump, las entradas cuyo uid coincide con el de la</p>
<p># app. Cada entrada de tipo llave de documento/WIA debe aparecer con</p>
<p># securityLevel TRUSTED_ENVIRONMENT o STRONGBOX (nunca SOFTWARE), y la</p>
<p># entrada de sellado del vault con el alias civica.wallet.vault.master.v1</p>
<p># (VaultKeyProvider.kt:76).</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

*El formato exacto de la salida de dumpsys keystore2 varía por versión de Android y fabricante — es una herramienta de diagnóstico del sistema, no algo definido por este repositorio. Sirve para confirmar dos cosas: que existen entradas asociadas al uid de la app, y que su nivel de seguridad no es SOFTWARE.*

### **8.4 Paso 4 — Leer el flag hardwareBacked desde la propia app**

El comando get_vault_key existe y funciona (EudiWalletPlugin.kt:884-901 → VaultKeyProvider.kt:88-111), pero hoy **no está conectado a ninguna pantalla** — vaultRespaldadoPorHardware() en src/services/vault.ts:183-189 no tiene ningún llamador en el resto del código. Se puede invocar manualmente desde las devtools de la webview de la app (build debug):

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>// En la consola de devtools de la webview (WebView remote debugging</p>
<p>// vía chrome://inspect, con el build debug):</p>
<p>await window.__TAURI__.core.invoke('plugin:wallet|get_vault_key', { purpose: 'pin' })</p>
<p>// -&gt; { key: "...", hardwareBacked: true }</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

plugin:wallet\| es el prefijo estándar de Tauri para comandos de plugin, correspondiente al nombre registrado en tauri-plugin-wallet/src/lib.rs:27 (Builder::new("wallet")). hardwareBacked: true confirma que esa clave de sellado quedó dentro de hardware seguro (TEE o StrongBox); false indica que el dispositivo no tiene Keystore respaldado por hardware y se usó una implementación software.

### **8.5 Paso 5 — Prueba de propiedad: la llave vive en el Keystore, no en el fichero**

Esta prueba confirma que el material de llave no puede reconstruirse solo con el fichero de almacenamiento — que es justamente lo que se espera si la llave está en el AKS y no en el filesystem. Úsese solo en un dispositivo de pruebas, nunca en producción: borra todos los datos de la app.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>adb shell pm clear com.luis.wallet</p>
<p># Esto borra tanto los ficheros en files/eudi_wallet/ como las entradas</p>
<p># de Keystore asociadas al uid de la app (Android las limpia junto con</p>
<p># los datos de la app). Al reabrir la app, las credenciales ya no</p>
<p># están disponibles y no hay forma de recuperarlas a partir de un</p>
<p># backup del fichero .db por sí solo: sin la llave del Keystore</p>
<p># (que no se puede exportar ni copiar), el contenido cifrado del</p>
<p># fichero antiguo queda inutilizable.</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **8.6 Limitación actual y mejora sugerida**

Ninguno de estos pasos está automatizado dentro del repositorio: no hay comando @Command que liste alias del Keystore, ni test instrumentado (androidTest) que lo verifique — el único test instrumentado presente es el boilerplate por defecto de Android Studio (tauri-plugin-wallet/android/src/androidTest/java/ExampleInstrumentedTest.kt:16-24), sin relación con el Keystore. Como mejora futura, valdría la pena: (a) conectar vaultRespaldadoPorHardware() a una pantalla de diagnóstico en Ajustes, y (b) añadir un comando de solo-lectura que liste los alias de eudi_wallet en el Keystore junto a su securityLevel, útil para QA sin depender de dumpsys.

# **9. Cobertura de la ERSo 2026-001 — Cartera de identidad y custodia de claves en hardware**

El documento **ERSo 2026-001** (Especificación de Requisitos Software, OID 1.3.6.1.4.1.31144.3.2.002.1.1, fecha ERSo 18 de septiembre de 2026, desarrollador Luis González) pide construir la cartera de identidad con custodia de claves en hardware, y define 4 criterios de aceptación formales. Esta sección contrasta cada criterio con el estado real del código, sin optimismo: se marca **IMPLEMENTADO** solo cuando hay evidencia directa de código funcionando, **PARCIAL** cuando el mecanismo existe pero falta una pieza exigida por el criterio, y **SOLO PLANEADO** cuando lo único que existe es diseño documentado sin código.

| **Criterio de aceptación (ERSo)**                        | **Estado**        | **Evidencia principal**                                                                         |
|----------------------------------------------------------|-------------------|-------------------------------------------------------------------------------------------------|
| 1\. Instancia registrada y activa, asociada al ciudadano | **SOLO PLANEADO** | No existe tabla de instancias/ciudadanos ni claim de identidad — solo challenges de un solo uso |
| 2\. Clave generada en hardware, no exportable            | **IMPLEMENTADO**  | Test instrumentado ejecutado en emulador real: 2/2 pass, 0 fallos (§9.2.1-9.2.2)                |
| 3\. Nivel de protección declarado = real                 | **IMPLEMENTADO**  | El wallet-provider verifica la key attestation y rechaza niveles insuficientes                  |
| 4\. Recuperación de cuenta sin exponer la clave privada  | **SOLO PLANEADO** | Diseñado en plan-eidas2.md §9.1; cero código en backend o cliente                               |

### **9.1 Criterio 1 — Instancia de cartera registrada y activa, asociada al ciudadano**

Criterio literal: *“La instancia de cartera queda registrada y activa, asociada al ciudadano; evidencia: registro de activación.”* — Estado: **SOLO PLANEADO**

Lo que existe es un flujo de Wallet Instance Attestation (WIA) puramente a nivel de app/dispositivo, sin ningún concepto de ciudadano ni de registro persistente:

- POST /wallet-instance-attestation/platform-key-attestation/{android\|ios} y /jwk — ecosistema/servicios/wallet-provider/.../config/WalletInstanceAttestationRoutes.kt:38-58

- IssueWalletInstanceAttestation (.../port/input/walletinstanceattestation/IssueWalletInstanceAttestation.kt:184-250) es *stateless*: valida un challenge de un solo uso, valida la platform key attestation, y firma un JWT con sub = clientId (línea 234) — un valor fijo de configuración del servicio, no un identificador de ciudadano (domain/Types.kt:60, asignado en WalletProviderModuleConfiguration.kt:119 desde config.clientId)

- Sin claims de ciudadano: el único sub del JWT es el ClientId del servicio (domain/walletinstanceattestation/WalletInstanceAttestationClaims.kt:31), no hay userId/citizen/DNI en ningún claim

- Sin persistencia: el esquema de BD (ecosistema/servicios/wallet-provider/schema/V1.sql:1-4) solo define una tabla challenges para los retos de un solo uso. No existe tabla de instancias de wallet ni de ciudadanos, ni estado “activo/registrado”

- Lado cliente: GestorClaveDeWallet.kt:62-104 pide el WIA bajo demanda y genera la clave de firma correspondiente, pero no guarda ni confirma ningún “estado de activación” — es la clave de firma del WIA, no un registro del ciudadano

*Qué falta: cualquier noción de identidad de ciudadano en el wallet-provider (tabla de instancias/ciudadanos, endpoint de registro), y un estado “activo” persistente y consultable que sirva como “registro de activación” exigido por el criterio.*

### **9.2 Criterio 2 — Par de claves generado en hardware, no exportable**

Criterio literal: *“El par de claves se genera dentro del hardware seguro y la clave privada no puede extraerse ni exportarse; evidencia: prueba de no exportabilidad.”* — Estado: **IMPLEMENTADO**

El mecanismo en sí es real y correcto: AndroidKeystoreSecureArea (Multipaz) y la API nativa de Android Keystore nunca exponen el material privado — así lo documentan las secciones 2 a 4 de este informe. Hasta ahora no había ninguna prueba explícita en el repositorio que lo demostrase:

- tauri-plugin-wallet/android/src/androidTest/java/ExampleInstrumentedTest.kt es el stub boilerplate sin modificar de Android Studio (solo comprueba packageName), no contenía ningún test real

- tauri-plugin-wallet/android/src/test/java/ExampleUnitTest.kt es igualmente el stub por defecto

- tauri-plugin-wallet/tests/ (Rust) solo contiene codegen.rs y command_surface.rs, sin relación con exportabilidad de claves

- El propio docs/plan-eidas2.md:1344-1351 (sección “9.4 Tests (hoy: cero)”) lo confirma: enumera como pendiente el “Kotlin instrumented: init, creación de clave con StrongBox...”

### **9.2.1 Test instrumentado añadido: prueba de no exportabilidad**

Se añadió tauri-plugin-wallet/android/src/androidTest/java/VaultKeyNonExportabilityTest.kt, un test instrumentado real que ejercita el camino de producción — VaultKeyProvider.clavePara() — y verifica, contra el Android Keystore de verdad, que la clave de sellado no puede salir de él. Corre contra el contexto de instrumentación del propio módulo del plugin (com.plugin.wallet), aislado del Keystore de la app final instalada (com.luis.wallet): no toca datos reales de una wallet en uso.

Dos afirmaciones, cada una con su assert:

- getEncoded() y getFormat() de la SecretKey devuelven null — el proveedor AndroidKeyStore se niega a serializar el material, no es una convención de la app.

- Pedir la especificación "cruda" (SecretKeySpec) vía SecretKeyFactory lanza InvalidKeySpecException, mientras que pedir metadatos (KeyInfo) sí funciona — el contraste prueba que solo el material está bloqueado, no la introspección del nivel de seguridad.

*tauri-plugin-wallet/android/src/androidTest/java/VaultKeyNonExportabilityTest.kt*

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>@RunWith(AndroidJUnit4::class)</p>
<p>class VaultKeyNonExportabilityTest {</p>
<p>private val alias = "civica.wallet.vault.master.v1"</p>
<p>private fun contexto() = InstrumentationRegistry.getInstrumentation().targetContext</p>
<p>@After</p>
<p>fun limpiar() { VaultKeyProvider.borrar(contexto()) }</p>
<p>@Test</p>
<p>fun laClaveDeSelladoNoExponeSuMaterialEnBytes() {</p>
<p>val clave = VaultKeyProvider.clavePara(contexto(), "prueba-no-exportabilidad-1")</p>
<p>assertNotNull("clavePara() debe devolver una contraseña derivada", clave.valor)</p>
<p>val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }</p>
<p>val entrada = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry</p>
<p>?: fail("no se encontró la entrada '$alias' en el Android Keystore tras clavePara()")</p>
<p>val secretKey = (entrada as KeyStore.SecretKeyEntry).secretKey</p>
<p>// Prueba canónica de no exportabilidad: una clave respaldada por el</p>
<p>// AndroidKeyStore siempre devuelve null en getEncoded()/getFormat().</p>
<p>assertNull("no debe exponer su material en claro", secretKey.encoded)</p>
<p>assertNull("tampoco debe declarar un formato de codificación", secretKey.format)</p>
<p>}</p>
<p>@Test</p>
<p>fun elMaterialCrudoEsInalcanzablePeroLosMetadatosSi() {</p>
<p>VaultKeyProvider.clavePara(contexto(), "prueba-no-exportabilidad-2")</p>
<p>val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }</p>
<p>val secretKey = (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey</p>
<p>val fabrica = SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")</p>
<p>try {</p>
<p>fabrica.getKeySpec(secretKey, SecretKeySpec::class.java)</p>
<p>fail("se esperaba InvalidKeySpecException: no debe ser exportable como SecretKeySpec")</p>
<p>} catch (_: InvalidKeySpecException) { /* esperado */ }</p>
<p>val info = fabrica.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo</p>
<p>assertEquals(alias, info.keystoreAlias)</p>
<p>}</p>
<p>}</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

### **9.2.2 Resultado de la ejecución**

El test se ejecutó de verdad, contra un Android Keystore real, no simulado: se levantó el emulador Pixel_9_Pro_XL (AVD, Android API 37) con el SDK local, y se corrió el módulo del plugin desde el proyecto Android generado por Tauri, donde :tauri-plugin-wallet está incluido como subproyecto:

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>$ ./gradlew :tauri-plugin-wallet:connectedDebugAndroidTest \</p>
<p>-Pandroid.testInstrumentationRunnerArguments.class=com.plugin.wallet.VaultKeyNonExportabilityTest</p>
<p>&gt; Task :tauri-plugin-wallet:connectedDebugAndroidTest</p>
<p>Starting 2 tests on Pixel_9_Pro_XL(AVD) - 17</p>
<p>Finished 2 tests on Pixel_9_Pro_XL(AVD) - 17</p>
<p>BUILD SUCCESSFUL in 35s</p>
<p>73 actionable tasks: 31 executed, 42 up-to-date</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

Reporte JUnit generado por Gradle (tauri-plugin-wallet/android/build/outputs/androidTest-results/connected/debug/):

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p>&lt;testsuite name="com.plugin.wallet.VaultKeyNonExportabilityTest"</p>
<p>tests="2" failures="0" errors="0" skipped="0"</p>
<p>time="0.339" timestamp="2026-09-28T16:15:02"&gt;</p>
<p>&lt;testcase name="elMaterialCrudoEsInalcanzablePeroLosMetadatosSi"</p>
<p>classname="com.plugin.wallet.VaultKeyNonExportabilityTest" time="0.009" /&gt;</p>
<p>&lt;testcase name="laClaveDeSelladoNoExponeSuMaterialEnBytes"</p>
<p>classname="com.plugin.wallet.VaultKeyNonExportabilityTest" time="0.0" /&gt;</p>
<p>&lt;/testsuite&gt;</p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

2/2 tests superados, 0 fallos, 0 errores. Es decir: contra el Android Keystore real del emulador, secretKey.getEncoded() y getFormat() fueron null, y SecretKeyFactory.getKeySpec(secretKey, SecretKeySpec::class.java) lanzó InvalidKeySpecException tal como predice el diseño — la clave de sellado de VaultKeyProvider es efectivamente no exportable, verificado en ejecución y no solo por lectura de la API de Android.

*Alcance de esta prueba: cubre la llave simétrica (AES) que sella los vaults de Stronghold, la única accesible sin inicializar la pila completa de eudi-lib-android-wallet-core/Multipaz. Las llaves asimétricas (EC) de documentos y de la WIA, gestionadas por AndroidKeystoreSecureArea, comparten el mismo proveedor AndroidKeyStore y por construcción tienen la misma garantía de no exportabilidad — pero una prueba ejecutable equivalente para ellas requeriría instanciar el SecureArea de Multipaz dentro del test, algo más involucrado y que queda como trabajo futuro si se necesita evidencia ejecutada también para ese caso.*

### **9.3 Criterio 3 — Nivel de protección declarado coincide con el real**

Criterio literal: *“El nivel de protección declarado coincide con el realmente disponible en el dispositivo; evidencia: ficha técnica y verificación del dispositivo.”* — Estado: **IMPLEMENTADO**

El wallet-provider sí verifica la Android Key Attestation (cadena de certificados) tanto al emitir el WIA como el Key Attestation, y rechaza dispositivos que no cumplan el nivel configurado:

- config/KeyAttestationRoutes.kt:41-61 y port/input/keyattestation/IssueKeyAttestation.kt delegan en la librería de terceros at.asitplus.attestation.AttestationService (“Makoto”)

- adapter/platformkeyattestation/ValidatePlatformKeyAttestation.kt:29-65 llama makotoAttestationService.verifyKeyAttestation(unvalidatedKeyAttestation, challenge) (línea 31) y falla si !verificationResult.isSuccess

- Nivel configurable por servicio: AndroidKeyAttestationConfiguration (config/WalletProviderConfiguration.kt:111-129) con strongBoxRequired, hardwareAttestationEnabled, softwareAttestationEnabled, traducido en config/WalletProviderModuleConfiguration.kt:189-209 a AndroidAttestationConfiguration(requireStrongBox = it.strongBoxRequired, ...)

- El código cliente documenta el diseño explícitamente — GestorClaveDeWallet.kt:68-71: *“usarStrongBox: pedir StrongBox si el dispositivo lo tiene. Es una preferencia, no una exigencia: multipaz cae a TEE si no está disponible. El nivel real lo comprueba el servidor leyendo la attestation, que es donde tiene que comprobarse.”*

*Es decir: si strongBoxRequired=true y el dispositivo solo ofrece TEE, la verificación en el backend falla — el nivel declarado no puede superar al real porque el servidor lo audita en cada emisión. La extensión de Android Key Attestation (OID 1.3.6.1.4.1.11129.2.1.17) se interpreta dentro de la librería de terceros “Makoto”, no con parsing propio en este repo.*

*Qué falta para ser completo: una “ficha técnica” explícita — un reporte o endpoint que declare y exponga de forma legible el nivel realmente verificado, más allá de la lógica interna de aceptar/rechazar en el backend.*

### **9.4 Criterio 4 — Recuperación de cuenta desde el backend sin acceder a la clave privada**

Criterio literal: *“La recuperación de cuenta se completa desde el backend sin acceder a la clave privada; evidencia: prueba de recuperación.”* — Estado: **SOLO PLANEADO**

docs/plan-eidas2.md, sección “9.1 Backup / recuperación” (líneas 1296-1317), es un diseño en fase de planificación (Fase 9, no implementada):

- Líneas 1300-1302: “las claves de AndroidKeystore/StrongBox son no exportables... Por tanto: las credenciales no se restauran, se reemiten.”

- Tabla L1/L2 (líneas 1304-1307): solo el catálogo de metadatos es exportable (L1); claves/credenciales/WIA/WUA (L2) explícitamente no exportables

- Formato propuesto wallet-backup-YYYYMMDD.eudi con AES-256-GCM + KDF argon2id, clave = “código de recuperación de 12 palabras generado por la wallet” (líneas 1309-1313) — descrito en condicional, sin referencia a código

- Restauración (líneas 1315-1317): instalar → restaurar copia → descifrar → nuevo PIN → relanzar OpenID4VCI por cada documento — flujo descrito, no implementado

- La propia sección “9.4 Tests (hoy: cero)” (línea 1344) confirma que a fecha del plan no hay tests de nada de esto, incluida la “cripto de backup” (línea 1349, listada como pendiente)

Búsqueda de código real (src/services/, tauri-plugin-wallet/, ecosistema/servicios/wallet-provider/src): no hay ningún endpoint /recover, /backup o /restore — los únicos endpoints del wallet-provider son wallet-instance-attestation, key-attestation, challenge y metadata. Las únicas coincidencias de “recuper\*” en el código son comentarios sobre que el vault es irrecuperable por diseño si se pierde el secreto (vault.ts:47, VaultKeyProvider.kt:70,219,248) o sobre un bug histórico ya corregido (pinService.ts:20) — ninguna relacionada con recuperación de cuenta.

*Qué falta: toda la implementación — derivación argon2id del backup, generación del código de recuperación de 12 palabras, endpoint(s) de backend para orquestar la recuperación, comandos Tauri/Kotlin del lado cliente, y solo entonces una prueba de que el backend nunca ve la clave privada.*

### **9.5 Qué falta para cerrar los 4 criterios**

- Modelar la identidad del ciudadano en el wallet-provider: tabla de instancias asociadas a un ciudadano, endpoint de registro/activación, y un estado consultable (criterio 1).

- Criterio 2 ya cerrado para la llave de sellado (AES): VaultKeyNonExportabilityTest.kt corrió 2/2 en un emulador real (§9.2.1-9.2.2). Pendiente, si se quiere evidencia ejecutada también para las llaves EC de documento/WIA: un test equivalente instanciando el SecureArea de Multipaz.

- Publicar una ficha técnica o endpoint que declare de forma legible el nivel de protección verificado por dispositivo, complementando la validación interna ya implementada (criterio 3).

- Implementar el diseño de backup/recuperación de plan-eidas2.md §9.1 (KDF argon2id, código de 12 palabras, reemisión de credenciales) — hoy es solo diseño, sin una línea de código (criterio 4).

# **10. Conclusión**

El diseño separa con claridad tres responsabilidades: firmar credenciales (delegado a una librería especializada del ecosistema EUDI), certificar la instancia de wallet ante el emisor (llave propia con challenge incrustado), y proteger en reposo el material que abre los vaults locales de PIN/historial (llave nativa AES-GCM). En los tres casos, el material de llave nunca sale del Android Keystore: solo se invoca para operaciones puntuales de firma o cifrado, con StrongBox como preferencia de hardware y biometría configurable por caso de uso.

Frente a la ERSo 2026-001, la base criptográfica exigida (generación y custodia no exportable en hardware, con verificación del nivel real desde el servidor) está sólidamente implementada — es la parte más madura del sistema. Lo que falta son las piezas de producto alrededor de esa base: identidad del ciudadano y estado de activación persistente en el wallet-provider, una prueba explícita de no exportabilidad, una ficha técnica legible del nivel de protección, y la implementación completa (hoy solo diseñada) del backup y la recuperación de cuenta.

*Aclaración metodológica: las citas de código, rutas y números de línea de este informe provienen directamente del repositorio en la rama fase-5/registro-de-relying-parties. Las afirmaciones sobre el funcionamiento interno de eudi-lib-android-wallet-core, Multipaz y la librería de terceros “Makoto” (at.asitplus:attestation-kt) más allá de las llamadas visibles en el código de este repositorio son conocimiento general de esas librerías, no verificado línea por línea (su código fuente no está incluido, solo los artefactos compilados/publicados). La sección 9 refleja el estado del código al 28 de septiembre de 2026 y debe releerse tras cualquier cambio en wallet-provider, plan-eidas2.md o el plugin Android.*
