---
name: consultar-corpus
description: Consulta el corpus EUDI (ARF, 725 HLR, 43 normas UE, ETSI, OpenID, IETF, W3C, TS1-14, código de referencia y conocimiento del proyecto) con citas trazables. Úsala siempre que se pregunte por un requisito, artículo, estándar, protocolo o definición, antes de responder de memoria. Disparadores: "qué dice el ARF/CIR/eIDAS", "HLR", "requisito", "artículo", "ETSI", "OpenID4VCI/VP", "SD-JWT", "Token Status List", "WIA/KA".
---

# Consultar el corpus con citas

1. **Requisito del ARF** → `python3 -I scripts/hlr.py <ID>` (p. ej. `OIA_03`), `--topic N`, `--actor "Wallet"`, `--texto <palabra> --nivel SHALL`, `--topics` (lista de 40 topics).
2. **Texto libre** → `python3 -I scripts/buscar.py "<términos>" [-f fuente] [-d documento] [-n 8]`.
   Fuentes (`-f`): `arf` (repo, incl. discussion topics = PRELIMINAR), `arf-pdf`, `ts` (TS1-14), `norma` (UE; filtra con `-d <CELEX>`, p. ej. `-d 32025R0848`; consolidado = `02014R0910-20241018`),
   `etsi`, `openid`, `ietf`, `w3c`, `hlr`, `rulebook`, `codigo` (README/docs de `referencia/`), `conocimiento` (memoria del proyecto y arquitectura).
3. Si hay pocos resultados: prueba sinónimos en inglés (el corpus normativo está en inglés; solo `eidas-910-2014-es` está en español) o `--completo`.
4. **Lee el fragmento completo** antes de afirmar (`--completo` o Read del archivo citado). Un fragmento corta contexto.
5. **Código**: Grep/Read en `referencia/<repo>/`. Cita `ruta:línea`.
6. **Responde** con: clasificación (NORMATIVO-UE/TÉCNICO-ARF/ESTÁNDAR/PRELIMINAR/…), cita exacta (acto+artículo, HLR, ARF §, cláusula), y qué **no** se pudo confirmar.

Notas: ISO/IEC 18013-5/-7 y ECCG ACM v2.0 **no están** en el corpus (HLR `OIA_03` los exige): dilo en lugar de suplirlos. `R10–R14` son riesgos, no requisitos.
Si el índice no existe o el corpus cambió: `python3 -I scripts/construir_indice.py`.
