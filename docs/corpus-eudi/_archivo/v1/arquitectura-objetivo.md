# Arquitectura objetivo del ecosistema Colombia (borrador por capas)

> Documento vivo: la arquitectura técnica que queremos alcanzar. Cada capa cruza con el ARF y con
> la `matriz-brechas.md`. Marcado: 🟢 definido / 🟡 en diseño / 🔴 pendiente. Es propuesta local, no
> normativa UE.

## Visión de alto nivel
Una wallet nacional que el ciudadano usa para identificarse, compartir atributos y firmar en línea,
con la confianza distribuida en listas publicadas. Se parte de lo que ya existe (Registraduría para
identidad, certificación digital local, sector fintech/público) y se reutiliza el máximo del stack
de referencia de la UE.

```
┌──────────────────────────────────────────────────────────────┐
│  CAPA 6 · Gobernanza y marco legal  (MinTIC, Congreso, SIC)  │  🔴
├──────────────────────────────────────────────────────────────┤
│  CAPA 5 · Certificación y riesgo (CAB/FCAF local, ONAC)      │  🔴
├──────────────────────────────────────────────────────────────┤
│  CAPA 4 · Confianza: Registrador, Access CA, LoTE análogo    │  🟡
├──────────────────────────────────────────────────────────────┤
│  CAPA 3 · Protocolos: OpenID4VCI/VP, SD-JWT, mDL, proximity   │  🟡
├──────────────────────────────────────────────────────────────┤
│  CAPA 2 · Servicios nodo: Wallet, Issuers, Verifier, RP reg.  │  🟢 (repos UE listos)
├──────────────────────────────────────────────────────────────┤
│  CAPA 1 · PKI / infraestructura de claves y dispositivos      │  🟡
└──────────────────────────────────────────────────────────────┘
```

## Capas y estado
1. **Claves y dispositivos (CA-01, 🟡)**: Secure Element/HSM, key attestation, WSCA/WSCD. En la
   UE lo resuelve `eudi-srv-wallet-provider` + KA/WIA (TS3). Decidir si se reutiliza PKI de firma
   electrónica local.
2. **Servicios nodo (CA-02, 🟢)**: ya existen como referencia y son la base para pilotos:
   - Wallet: `eudi-lib-android-wallet-core`, `eudi-lib-ios-wallet-kit`
   - Issuer: `eudi-srv-web-issuing-eudiw-py`, `eudi-srv-pid-issuer`
   - Verifier: `eudi-web-verifier`, `eudi-srv-verifier-endpoint`
   - Registro de RP: `eudi-srv-web-relyingparty-registration-py`
   - Listas de confianza: `eudi-srv-web-trustedlist-manager-py`
3. **Protocolos (CA-03, 🟡)**: OpenID4VCI/VP (`eudi-lib-jvm-openid4vci-kt`, `eudi-lib-jvm-openid4vp-kt`),
   SD-JWT (`eudi-lib-jvm-sdjwt-kt`), mDL (ISO 18013-5), proximity.
4. **Confianza (CA-04, 🟡)**: definir Registrador, Access CA, Providers of Registration
   Certificates y el LoTE análogo nacional.
5. **Certificación y riesgo (CA-05, 🔴)**: FCAF local (sección 7.5 ARF) + evaluación de seguridad.
6. **Gobernanza (CA-06, 🔴)**: base legal, institución rectora, régimen de datos, supervisión.

## Entregables técnicos pendientes (backlog de arquitectura)
- [ ] Fichas técnicas por componente (requisitos, puertos, datos, dependencias) partiendo de los repos referencia.
- [ ] Diagrama de despliegue de un piloto mínimo (issuer + verifier + wallet de prueba).
- [ ] Adaptación del rulebook template a una credencial colombiana piloto (p. ej. "título educativo" o "licencia de conducción").