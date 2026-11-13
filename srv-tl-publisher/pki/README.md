# PKI del ecosistema

Jerarquía de CAs propia. Es la Fase 1 de `PLAN_EIDAS2.md`, y existe por una
razón concreta: **hoy no se puede revocar nada**.

Los certificados que usa el stack son autofirmados sueltos, sin
`authorityInfoAccess` ni `crlDistributionPoints`. Sin esas dos extensiones un
validador no tiene dónde preguntar si un certificado sigue vigente — y no se
pueden añadir después, se hornean al emitir. Por eso la PKI va **antes** de
reemitir cualquier cosa.

## Uso

```bash
./crear-pki.sh eudi.miempresa.com     # ceremonia: raíz + 6 sub-CAs + CRLs
./emitir.sh --listar                  # perfiles disponibles
./emitir.sh rp-access --cn eudi.miempresa.com --san eudi.miempresa.com --p12
./revocar.sh issued/rp-access/cert.pem --motivo keyCompromise
./revocar.sh --regenerar-crl rp       # refrescar la CRL sin revocar nada
```

## La jerarquía

```
Civica EUDI Root CA            EC P-384, 20 años, autofirmada
  ├─ Civica TL Signing CA      firma las Trusted Lists
  ├─ Civica Wallet Provider CA claves de attestation del wallet-provider
  ├─ Civica PID Issuer CA      Document Signers del pid-issuer
  ├─ Civica QEAA CA            emisores de atestaciones cualificadas
  ├─ Civica QTSP Signing CA    certificados de firma del usuario (QES)
  └─ Civica RP Access CA       access certificates de relying parties
```

**P-256 en todo lo que no sea la raíz**, y no es una preferencia: el verifier
firma con ES256, y `setup_trusted_CAs()` del TLP construye `EC2Key(crv=1)`, que
es P-256 y nada más. Cualquier otra curva se ignora **en silencio**. La raíz va
en P-384 sólo por margen a 20 años.

## Decisiones que conviene conocer

**`openssl ca`, no `openssl x509 -req`.** El segundo firma pero no lleva
registro de lo emitido, y sin índice no hay CRL. Como el objetivo es
precisamente poder revocar, cada CA tiene su `index.txt`, su `serial` y su
`crlnumber`.

**El arco de OIDs es `2.25.<uuid>`, no `1.3.6.1.4.1.<PEN>`.** No tenemos un
Private Enterprise Number de IANA y apropiarse del de otro es incorrecto y
detectable. El arco 2.25 (ITU-T X.667) permite derivar un OID de un UUID propio
sin registrarse en ningún sitio. El UUID está en `perfiles.sh`.

**La cadena que se entrega no incluye la raíz.** `fullchain.pem` es hoja +
sub-CA. La raíz es el ancla de confianza: tiene que estar ya instalada en quien
valida, no viajar con lo que se valida.

**El dominio queda empotrado** en las URLs de AIA y CRLDP de cada certificado.
Cambiarlo obliga a reemitir toda la PKI — por eso `crear-pki.sh` lo guarda en
`dominio.txt` y `emitir.sh` usa ése y no otro.

## Lo que falta para que esto sirva de algo

Las URLs de AIA y CRLDP apuntan a `https://<dominio>/pki/...`, y **ahí todavía
no hay nada**. Servirlas es la Fase 1.3: un backend en HAProxy para
`/pki/ca/*.crt`, `/pki/crl/*.crl` y `/pki/ocsp`, más un cron que regenere las
CRLs cada 6 h.

Hasta entonces la revocación funciona localmente —`revocar.sh` lo comprueba con
`openssl verify -crl_check`— pero ningún validador remoto puede enterarse.

## Custodia

`crear-pki.sh` deja la clave de la raíz en `root/private/ca.key.pem` con
permisos `0400`. **Muévela a un soporte offline y bórrala de aquí**: sólo hace
falta para emitir una sub-CA nueva o regenerar la CRL de la raíz. Con ella se
puede emitir cualquier certificado que la app considere de confianza.

`PKI_ROOT_PASSPHRASE` la cifra, si prefieres eso a moverla.
