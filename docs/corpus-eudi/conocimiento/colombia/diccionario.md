# Diccionario UE → proyecto Colombia

> Fija el término del estándar y su uso en el proyecto. Los términos se verifican contra `fuentes/arf-repo/docs/annexes/annex-1/annex-1-definitions.md`
> (buscar: `python3 -I scripts/buscar.py "<término>" -f arf -d annex-1`).

## Actores y componentes
| Término UE | Sigla | Uso en el piloto |
|---|---|---|
| Wallet Unit | WU | Wallet Instance + WSCA/WSCD (+ keystores, QSCD local, Wallet Unit Service opcional). Una instalación = una WU |
| Wallet Instance | — | La app Android (C01+C02) |
| Wallet Solution | — | Conjunto de software/servicios del proveedor (app + backend + provider) |
| Wallet Provider | — | Quien responde por la solución y sus atestaciones técnicas (C20). **Diferente** de D01 Wallet Backend |
| WSCA / WSCD | — | Aplicación/dispositivo criptográfico seguro; en Android, hardware seguro (StrongBox/TEE). El nivel real debe verificarse (no declarar el mismo nivel tras degradación) |
| Wallet Instance Attestation | WIA | Atestación técnica de la instancia (vigencia corta). **No** prueba identidad civil |
| Key Attestation | KA | Atestación sobre claves y su protección. (La sigla WUA de los insumos antiguos se reemplaza por WIA/KA) |
| PID / PID Provider | PID | **No se emite en el piloto** (D-005). Término europeo; no usar para credenciales notariales |
| Electronic Attestation of Attributes | EAA | Credencial de atributos **no cualificada**; el catálogo notarial es de este tipo conceptual (propio, no europeo) |
| QEAA / PuB-EAA | — | EAA cualificada / EAA de organismo público con fuente auténtica. Sin análogo colombiano reconocido hoy |
| Relying Party / Wallet-Relying Party | RP / WRP | Verificador / parte usuaria registrada (CIR 2025/848) |
| Access Certificate | — | Certificado que autentica a la parte usuaria ante la wallet |
| Registration Certificate | WRPRC | Certificado de registro: qué datos declaró pedir la parte usuaria (ETSI TS 119 475) |
| Trusted List / LoTE | TL / LoTE | Listas de confianza (ETSI TS 119 612) / lista de entidades de confianza (TS 119 602). **No llamar así a la lista colombiana** |
| Conformity Assessment Body | CAB | Evaluador de conformidad; análogo local posible vía ONAC (por investigar) |
| Authentic Source | — | Fuente oficial del dato. En el piloto: el proceso notarial en SIGNO; sin acceso a RNEC |

## Formatos y protocolos
| Término | Nota |
|---|---|
| OpenID4VCI 1.0 / OpenID4VP 1.0 | Emisión / presentación. Son especificaciones finales. "OID4VC" de insumos antiguos = OpenID4VCI. OpenID Connect = solo autenticación de cuentas |
| HAIP | Perfil de alta garantía de OpenID4VC (perfila VCI/VP, SD-JWT VC y mdoc) |
| DCQL | Digital Credentials Query Language: lenguaje de consulta de OpenID4VP 1.0 (reemplaza Presentation Exchange) |
| SD-JWT VC (`dc+sd-jwt`) | Formato base para presentación remota; divulgación selectiva |
| mdoc (`mso_mdoc`) / ISO 18013-5 | Formato y protocolo de proximidad; ISO de pago, no disponible en el corpus |
| Token Status List | Mecanismo de estado (revocación/suspensión) de SD-JWT/mdoc; **no** equivale a Bitstring Status List (W3C) |
| Key Binding JWT / DeviceAuthentication | Prueba de posesión de la clave del holder en SD-JWT VC / mdoc |
| DPoP | Prueba de posesión de token OAuth; clave **separada** del holder binding |
| DC API | W3C Digital Credentials API (presentación mediada por el navegador/SO) |

## Específicos del proyecto
| Término | Significado |
|---|---|
| `CO-EUDI-PILOTO-1` | Perfil técnico base colombiano inspirado en EUDI (§2.4) |
| `EUDI-UE` | Objetivo futuro de conformidad; no se declara satisfecho |
| `CO-W3C-OPCIONAL-1` | Extensión W3C/DID, apagada por defecto |
| D01 | Wallet Backend (desarrollo propio): cuentas, instancias, recuperación, respaldo opaco |
| I01 | Adaptador SIGNO: orden de emisión autorizada y consumo atómico |
| AndesSCD | CA del equipo (EJBCA Community) |
| SIGNO | Software notarial (PHP), fuente del procedimiento |
| ERSo | Especificación de Requisitos de Software (documento interno del equipo; 2026-001…008) |
| AC-01…AC-23 | Criterios de aceptación funcional y adversaria del piloto (§13.2) |
| `ACCEPTED/REJECTED/INDETERMINATE` | Resultado de verificación; indeterminado ≠ válido |
| ECD | Entidad de Certificación Digital (término colombiano; régimen por verificar en `investigacion/C-…`) |
| SNR / RNEC / ONAC / SIC / AND | Superintendencia de Notariado y Registro / Registraduría / Org. Nacional de Acreditación / Superintendencia de Industria y Comercio / Agencia Nacional Digital |
