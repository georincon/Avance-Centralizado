---
name: matriz-conformidad
description: Construye o actualiza matrices de conformidad del piloto contra los HLR del ARF y los CIR, por rol (Wallet Provider, Emisor/Attestation Provider, Verificador/Relying Party, CA). Úsala para preparar evidencias, planificar pruebas AC y medir la brecha frente a EUDI-UE. Disparadores: "matriz de conformidad", "cumple el ARF", "brecha UE", "qué falta para certificar".
---

# Matriz de conformidad piloto ↔ HLR ↔ CIR

1. **Alcance**: elige rol y Topics (`python3 -I scripts/hlr.py --topics`; por actor: `--actor "Wallet Providers"|"Attestation & PID Providers"|"Relying Parties"|"Member States & Registrars"`; transversales: `--actor "Protocols"|"Data Models"`).
2. **Filtra por aplicabilidad**: el piloto **no** es una wallet certificada ni PID Provider. Marca cada HLR: `A` aplica al piloto · `E` aplica a la evolución EUDI-UE · `NA` no aplica (justifica).
3. **Estado** por HLR aplicable: `cumple` · `parcial` · `no` · `sin evidencia`, con **evidencia** (`ruta:línea`, prueba AC-xx, documento) o la razón.
4. **Cruza con CIR** (`conocimiento/ue/mapa-normativo.md`): fecha de aplicación y acto modificatorio (2026/1730, 1731, 1735).
5. **Cruza con pruebas**: AC-01…23 y casos FCAF (`referencia/eudi-doc-functional-conformance-assessment`, `eudi-itb`). Aprobar pruebas ≠ certificación (RN-031).
6. Formato de tabla: `HLR | nivel | rol | aplica | estado | evidencia | CIR/fecha | acción`. Archivo: `conocimiento/ue/matriz-hlr-<rol>.md`.
Recuerda: HLR con texto "Empty" no son requisitos; los HLR que remiten a normas de pago (ISO 18013-5, ECCG ACM) se marcan "fuente no disponible".
