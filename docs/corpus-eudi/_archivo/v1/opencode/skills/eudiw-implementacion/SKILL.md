---
name: eudiw-implementacion
description: >-
  Usar cuando se trabaje con el codigo de referencia de la EUDI Wallet: wallets Android (Kotlin) e
  iOS (Swift), librerias OpenID4VCI/OpenID4VP en JVM, servidores de emision (issuer en Python),
  verificadores web (TypeScript), SD-JWT, ISO 18013-5/mDL, flows de emision y presentacion. Activar
  con "implementar", "codigo", "openid4vci", "openid4vp", "SD-JWT", "wallet kit", "issuer",
  "verifier", "kotlin", "swift".
---

# Implementación de referencia EUDI Wallet

## Repositorios clonados en `referencia/`
| Componente | Ruta | Pila |
|------------|------|------|
| Wallet (ciudadano) Android | `referencia/eudi-lib-android-wallet-core` | Kotlin |
| Wallet (ciudadano) iOS | `referencia/eudi-lib-ios-wallet-kit` | Swift |
| OpenID4VCI (emisión) | `referencia/eudi-lib-jvm-openid4vci-kt` | Kotlin/JVM |
| OpenID4VP (presentación rich apps) | `referencia/eudi-lib-jvm-openid4vp-kt` | Kotlin/JVM |
| Issuer (servidor de emisión) | `referencia/eudi-srv-web-issuing-eudiw-py` | Python |
| Verifier / Relying Party web | `referencia/eudi-web-verifier` | TypeScript |
| Roadmap de la RI | `referencia/eudi-wallet-reference-implementation-roadmap` | — |

## Qué cubre el ARF para implementar (cap. 5)
- **Formato de credenciales**: SD-JWT VC (con ZKP opcional) y mDL (ISO/IEC 18013-5) como formatos del
  ARF. Se soportan varios formatos; el ARF define los requeridos.
- **Emisión**: OpenID for Verifiable Credential Issuance (**OpenID4VCI**) — credenciales emitidas como
  atestaciones (PID, EAA) incluyendo flujos de "issuance" interactivos y no interactivos.
- **Presentación**: OpenID for Verifiable Presentations (**OpenID4VP**) para escenarios *online*, y
  protocolos proximity (BLE/NFC para mDL) para escenarios presenciales.
- **Firma electrónica**: interfaces para QES (remota/local, SCA) — ver anexos 4.06–4.12 y TS12/13/14.

## Protocolo de implementación
1. Lee primero el código clonado correspondiente (firmas de APIs, paquetes, DSL) antes de escribir
   código nuevo. NO inventes firmas de las librerías oficiales.
2. Identifica los patrones de la referencia (p. ej. arquitectura core/UI en Android, wallet-kit en
   iOS, estructura del issuer Python) y respétalos en adaptaciones.
3. Cruza el diseño contra los requisitos funcionales del ARF (anexo 2 / sección 7.5 FCAF) y reporta
   qué R… se cumple.
4. Antes de introducir dependencias nuevas, verifica que la referencia no las usa (busca en
   build.gradle / Package.swift / requirements.txt). Si introduces una, justifícalo.
5. Si el objetivo es Colombia, plantea primero la pieza a construir (wallet? issuer? verifier?
   sistema de confianza?) y su interfaz con el marco local, antes de escribir código.

## Reglas aprendidas de la implementación (checklist)
- [ ] Verifiqué firmas de APIs leyendo el código clonado (no de memoria).
- [ ] Respeté los patrones de la referencia (core/UI en Android; wallet-kit en iOS; estructura
      Python del issuer; TypeScript del verifier).
- [ ] Nuevas dependencias: justificadas y comprobado que la referencia no las usa.
- [ ] Reporté qué requisito funcional del ARF (R…/Topic N) cubre la pieza.
- [ ] Crucé contra FCAF si la pieza toca funcionalidad que los casos de prueba pueden validar.

## Vicios típicos (evitar)
- ❌ Inventar clases/métodos de `eudi-lib-android-wallet-core` o `eudi-lib-ios-wallet-kit`.
- ❌ Mezclar flujos de emisión (OpenID4VCI) con presentación (OpenID4VP) en un mismo diagrama sin
  aclararlo.
- ❌ Usar SD-JWT sin el objeto de divulgación selectiva correcto (base64url-encoded, estructurado).