# Mapa normativo UE (eIDAS 2.0 y actos de ejecución)

> Categoría: **NORMATIVO-UE**. Títulos, fechas y reglas de aplicación **extraídos de los textos oficiales** (Cellar) guardados en
> `fuentes/regulaciones/<CELEX>.txt`. Verificado 2026-10-13. Buscar el texto: `python3 -I scripts/buscar.py "<tema>" -f norma -d <celex>`.
> "EIF" = entrada en vigor: salvo indicación, el **vigésimo día** siguiente al de publicación en el DOUE (fecha de publicación = columna DOUE).
> El valor jurídico lo tiene el texto del DOUE; esta tabla es una ayuda de navegación. Relevancia: **A** = afecta directamente al piloto, **B** = considerar/evolución, **C** = contexto.

## Base
| Acto | CELEX | Materia | Rel. |
|---|---|---|---|
| Reglamento (UE) 910/2014 (eIDAS) | `32014R0910` original · `02014R0910-20241018` **consolidado** | Identificación electrónica y servicios de confianza. Art. 5a = wallets (cada Estado miembro ofrece ≥1 **dentro de 24 meses desde la EIF de los actos de ejecución**; actos publicados 4-dic-2024, EIF a los 20 días ⇒ plazo ≈ 24-dic-2026, cálculo derivado). Art. 22 = listas de confianza | A |
| Reglamento (UE) 2024/1183 | `32024R1183` | Modifica 910/2014: marco europeo de identidad digital (eIDAS 2.0). Publicado 30-abr-2024 | A |

## Actos de ejecución del núcleo de la wallet (DOUE 4-dic-2024)
| Acto | CELEX | Materia | Rel. |
|---|---|---|---|
| CIR 2024/2977 | `32024R2977` | PID y atestaciones electrónicas de atributos (EAA) emitidas a wallets | A |
| CIR 2024/2979 | `32024R2979` | Integridad y funcionalidades básicas de las wallets | A |
| CIR 2024/2980 | `32024R2980` | Notificaciones a la Comisión sobre el ecosistema de wallets | B |
| CIR 2024/2981 | `32024R2981` | **Certificación** de las wallets (Anexo I = registro de riesgos; Anexo III = requisitos funcionales; Anexo IV = WSCD nivel *High*) | A |
| CIR 2024/2982 | `32024R2982` | Protocolos e interfaces (OpenID4VCI/VP, ISO 18013-5…) | A |

## Actos de 2025 sobre la wallet (DOUE 7-may-2025)
| Acto | CELEX | Materia | Aplicación | Rel. |
|---|---|---|---|---|
| CIR 2025/846 | `32025R0846` | Correspondencia transfronteriza de identidad de personas físicas | desde **24-dic-2026** | C |
| CIR 2025/847 | `32025R0847` | Reacciones ante brechas de seguridad de wallets | art. 10 desde **7-may-2026** | A |
| CIR 2025/848 | `32025R0848` | **Registro de partes usuarias de wallets** (certificados de acceso y de registro; Anexo IV = política de certificados de acceso) | desde **24-dic-2026** | A |
| CIR 2025/849 | `32025R0849` | Información a la Comisión para la lista de wallets certificadas | EIF estándar | B |

## Atestaciones, identidad y alta remota
| Acto | CELEX | Materia | Aplicación | Rel. |
|---|---|---|---|---|
| CIR 2025/1566 | `32025R1566` | Estándares de referencia para verificar identidad y atributos (certificados/QEAA cualificados) | desde **19-ago-2027** | B |
| CIR 2025/1569 | `32025R1569` | QEAA y EAA de organismos públicos responsables de fuentes auténticas (PuB-EAA) | arts. 6-9 desde **19-ago-2026** | B |
| CIR 2026/798 | `32026R0798` | Estándares y especificaciones para el **alta remota** de usuarios en la wallet (DOUE 8-abr-2026; ETSI EN 319 401 y requisitos OVR) | EIF estándar | B |

## Modificaciones de julio de 2026 (adoptadas 15-jul-2026; DOUE 22-jul-2026; EIF **11-ago-2026**)
| Acto | CELEX | Modifica | Fechas especiales |
|---|---|---|---|
| CIR 2026/1730 | `32026R1730` | CIR 2025/848 (estándares y especificaciones aplicables) | — |
| CIR 2026/1731 | `32026R1731` | CIR 2024/2977, 2979, 2980 y 2982 (estándares y especificaciones aplicables; incluye imagen facial ISO/IEC 39794-5) | art. 3(4) desde **11-ago-2028** |
| CIR 2026/1735 | `32026R1735` | CIR 2025/1569 | art. 1(3) desde **1-ene-2027** |
**Consecuencia:** no congelar el contrato técnico en los textos originales de 2024; los anexos vigentes son los modificados. Una matriz de conformidad debe desglosar cada obligación por fecha.

## Servicios de confianza (contexto/evolución de AndesSCD) — rel. B/C
| Acto | CELEX | Materia | Aplicación |
|---|---|---|---|
| CIR 2025/1567 | `32025R1567` | Gestión de dispositivos remotos de creación de firma/sello cualificados | 19-ago-2027 |
| CIR 2025/1568 | `32025R1568` | Revisiones entre pares de esquemas de eID | EIF estándar |
| CIR 2025/1570 | `32025R1570` | Notificación de QSCD/QSealCD certificados | 19-dic-2025 |
| CIR 2025/1571 | `32025R1571` | Informes anuales de organismos de supervisión | — |
| CIR 2025/1572 | `32025R1572` | Notificación de intención de iniciar servicios cualificados | 19-ago-2026 |
| CIR 2025/1929 | `32025R1929` | Vinculación de fecha/hora y precisión de fuentes de tiempo (sellos de tiempo cualificados) | — |
| CIR 2025/1942 / 1945 | `32025R1942` `32025R1945` | Servicios cualificados de validación / validación de firmas y sellos | — |
| CIR 2025/1943 | `32025R1943` | Estándares de certificados cualificados de firma y sello | — |
| CIR 2025/1944 | `32025R1944` | Entrega electrónica certificada cualificada | — |
| CIR 2025/1946 | `32025R1946` | Conservación cualificada | — |
| CIR 2025/2160 | `32025R2160` | Gestión de riesgos de servicios de confianza **no cualificados** | — |
| CIR 2025/2162 | `32025R2162` | Acreditación de organismos de evaluación de la conformidad (CAB) y esquema | — |
| CIR 2025/2527 | `32025R2527` | Certificados cualificados de autenticación de sitios web | 6-ene-2027 |
| CIR 2025/2530 | `32025R2530` | Requisitos para prestadores cualificados | — |
| CIR 2025/2531 / 2532 | `32025R2531` `32025R2532` | Libros mayores electrónicos (no obligan a blockchain en la wallet) / archivo electrónico | — |
| CIR 2026/248 | `32026R0248` | Formatos de firmas/sellos avanzados reconocidos por organismos públicos | art. 1(1) y 3(1) desde 23-feb-2027 |
| CID 2015/1505 y CID 2025/2164 | `32015D1505` `32025D2164` | Plantilla técnica de listas de confianza (ETSI TS 119 612); la 2025/2164 actualiza la versión del estándar | 2025/2164 desde 29-abr-2026 |

## Contexto
RGPD 2016/679 (`32016R0679`) · NIS2 2022/2555 (`32022L2555`) · Ley de Ciberseguridad 2019/881 (`32019R0881`) · DSA 2022/2065 (`32022R2065`) ·
Directivas de accesibilidad 2016/2102 y 2019/882. **No aplican a Colombia**; sirven de referencia comparada. El régimen colombiano de datos es la Ley 1581/2012 (ver `conocimiento/colombia/`).

## Para qué sirve esta tabla
- Citar con precisión (acto + artículo) y evitar la numeración inventada.
- Preparar la **matriz de conformidad** por rol (Wallet Provider, Emisor, Verificador): `conocimiento/ue/` (pendiente, ver PLAN-MAESTRO F1).
- Recordar que **reutilizar código UE no otorga certificación ni reconocimiento**; el piloto no se incorpora a la lista de wallets certificadas (CIR 2025/849) por autodeclaración.
