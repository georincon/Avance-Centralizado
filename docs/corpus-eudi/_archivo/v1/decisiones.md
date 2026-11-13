# Decisiones del ecosistema Colombia

> Acumulativo. Cada decisión registra: qué, quién, fecha, motivo y estatus. Las decisiones son la
> fuente de verdad local sobre dirección del proyecto; si una cambia, se marca como "revisada" con
> el motivo y se registra la nueva.

| # | Fecha | Decisión | Motivo | Estatus |
|----|--------|----------|--------|---------|
| D-001 | 2026-10-11 | Adoptar el ARF v3.0.0 y el marco eIDAS2 como **referencia técnica de partida** del ecosistema, no como normativa colombiana. | Es la línea base técnica más madura de identidad digital; sirve para traducir principios, roles y protocolos al contexto nacional. | Vigente |
| D-002 | 2026-10-11 | Los repos de `referencia/` son **solo lectura** (referencia), no se editan ni se generan PRs desde aquí. | Se clonaron sin historial como biblioteca de conocimiento; evita divergencias y confusiones. | Vigente |
| D-003 | 2026-10-11 | Estrategia de entrada: arrancar con **EAA no cualificados** sobre un LoTE/análogo nacional antes de aspirar a PID "calificado". | El ARF §6.1 permite modelos de confianza alternativos para EAA no cualificados; reduce fricción legal y acelera pilotos. | Vigente (ver regla RN-003) |
| D-004 | 2026-10-12 | Tomar el **inventario institucional de `marco-institucional.md`** como fuente de verdad local de roles/instituciones (AND articula/presta SCD y Autenticación Digital; RNEC=PID/cédula digital; MinTIC=rector Gobierno Digital; SIC=datos/vigilancia; ONAC=acreditación). Pendiente validación con autoridades. | Verificado con fuentes oficiales (normas, sitios estatales, noticia 2026 del piloto VC MinTIC+AND). | Vigente (baja a provisional al validar con autoridades) |

## Diferidas / por decidir
- Alcance del proyecto: ¿wallet ciudadana, emisores, verificadores o ecosistema completo?
- ¿Qué autoridad ejerce el rol de Registrador/PID en Colombia (Registraduría vs. otra)?
- Prioridad de las primeras credenciales/atributos del piloto.
- Base legal local que habilita la emisión de un PID en formato wallet.