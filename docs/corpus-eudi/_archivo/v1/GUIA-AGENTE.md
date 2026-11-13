# Guía: cómo se construyó tu agente EUDI Experto (y cómo extenderlo)

Este documento te enseña la mecánica detrás del agente para que puedas mantenerlo, ampliarlo y
crear más agentes con la misma técnica. Está hecho para aprenderse en una sesión y consultarse luego.

---

## 1. ¿Qué acaba de pasar? (anatomía del agente)

Un "agente" en opencode (y en este ecosistema) = **una personalidad + un expertise cargado**.
El expertise se alimenta de 4 piezas que ya existen en tu proyecto:

```
Agente86/
├── AGENTS.md                     ← 0. Memoria operativa que toda sesión lee
├── opencode.json                 ← 1. Referencias (21 repos clonados = conocimiento)
├── .opencode/
│   ├── agent/eudi-experto.md     ← 2. Personalidad y cerebro (el agente en sí)
│   ├── skills/                   ← 3. Micro-manuales de acción (se disparan solos)
│   │   ├── arf-ue/SKILL.md
│   │   ├── eidas2-ue/SKILL.md
│   │   ├── eudiw-implementacion/SKILL.md
│   │   └── colombia-adaptacion/SKILL.md
│   └── command/                  ← Comandos: `/eudi` y `/practicar`
├── conocimiento/colombia/        ← LA MEMORIA local viva (decisiones, reglas, arquitectura, diccionario, brechas)
└── referencia/                   ← 21 repos de la UE clonados (la biblioteca)
```

### Pieza 1 — `opencode.json`
Declara `references`: cada entrada le dice a opencode *"estos repos de código están disponibles y
útilense para X"*. La clave `default_agent: "eudi-experto"` hace que al abrir opencode en esta
carpeta, el especialista sea el agente por defecto.

### Pieza 2 — `.opencode/agent/eudi-experto.md`
Es el "cerebro". La cabecera (frontmatter) define qué agente es; el cuerpo es su prompt (instrucciones
de personalidad y reglas). Fíjate en `mode: all`: eso le permite ser tanto agente principal (lo
eliges con Tab) como subagente (que otros agentes lo deleguen).

### Pieza 3 — `.opencode/skills/*/SKILL.md`
Skills son **manuales de acción que el modelo carga SOLO cuando el tema coincide** con su
`description`. Por eso no recarga todo cada vez: si preguntas "¿qué dice el ARF sobre el PID?", se
activa `arf-ue`. Reglas de oro de una skill:
- El archivo **debe** llamarse `SKILL.md` y vivir en su propia carpeta con el mismo nombre de la skill.
- `name` y `description` son obligatorios; la `description` decide cuándo se dispara, así que incluye
  las palabras clave que realmente vas a usar ("ARF", "openid4vci", "MinTIC", "Colombia"...).

### Pieza 4 — `.opencode/command/eudi.md`
Un comando cuyo frontmatter declara `agent: eudi-experto`. Al escribir `/eudi tu pregunta`, opencode
abre el agente especialista con esa petición.

---

## 2. Cómo usar el agente (día a día)

| Quiero... | Hago... |
|-----------|---------|
| Aprender o decidir algo | `/eudi ¿cómo funciona el modelo de confianza del ARF?` |
| Analizar código | `/eudi explica cómo emite credenciales eudi-srv-web-issuing-eudiw-py` |
| Avanzar en Colombia | `/eudi llena la matriz de brechas capa por capa` |
| Comparar estándares | `/eudi compara SD-JWT VC vs mDL para mi escenario` |
| Redactar un rulebook | `/eudi guíame para redactar el rulebook de la cédula colombiana` |
| Repasar/autoevaluarme | `/practicar` (ejercicios adaptados a mi nivel; corregidos y guardados) |

Cambios de configuración (este archivo, skills, agentes) **exigen reiniciar opencode** para que se
recarguen, porque la config se lee al arrancar.

---

## 3. Cómo extenderlo (recetas)

**Actualizar el ARF / traer un repo nuevo**
```bash
cd referencia && git pull          # actualiza un repo existente
git clone --depth 1 https://github.com/eu-digital-identity-wallet/<repo>.git
```
Luego añade una entrada en `references` de `opencode.json` con su `description` (así el agente sabrá
para qué le sirve).

**Crear una skill nueva** (ej. "regulacion-firma-electrico")
```bash
mkdir -p .opencode/skills/firma-electronica
```
Crea `.opencode/skills/firma-electronica/SKILL.md` con `name: firma-electronica`, una `description`
que liste tus disparadores, y el manual. Reinicia y listo.

**Crear otro agente** (ej. un revisor de código solo para el ecosistema): copia el patrón
`.opencode/agent/<nombre>.md`, cambia el prompt y si quieres restríngele permisos:
```yaml
permission:
  edit: deny       # solo lee y opina
  bash: ask
```

**Recargar el conocimiento legal**: el agente distingue normativa ↔ ARF ↔ discusión. Cuando el usuario
o tú actualicen `referencia/eudi-doc-architecture-and-reference-framework`, revisa su `CHANGELOG.md`
para ver la versión. Los textos legales vigentes siempre deben verificarse en EUR-Lex.

---

## 4. Cómo aprender (plan de progresión sugerido)

1. **Fundamentos**: cap. 1-2 del ARF (qué hace la wallet) + qué es eIDAS2 en 15 min.
2. **Roles y arquitectura**: cap. 3-4 (quién es quién; componentes de la wallet).
3. **Datos y protocolos**: cap. 5 (SD-JWT, mDL, OpenID4VCI/VP) con el código clonado al lado.
4. **Confianza y certificación**: cap. 6-7 (trust, certificación, FCAF).
5. **Adaptación Colombia**: ejecutar la skill `colombia-adaptacion` y llenar `matriz-brechas.md`.
6. **Práctica**: construir un MVP piloto (ej. un issuer emitiendo un atributo local con la
   referencia `eudi-srv-web-issuing-eudiw-py`).

Pídele al agente en cualquier momento un *test de autoevaluación* sobre un tema y te lo diseñará
según tu nivel.

---

## 5. El "entrenamiento" del agente (cómo se vuelve super herramienta)

"No entrenar" aquí no significa ajustar pesos del modelo: significa hacer crecer su **memoria y sus
hábitos** con cada trabajo. Ciclo que el proyecto ya implementa:

1. **Trabajamos** (investigar, comparar, diseñar, codificar).
2. **Capturamos** en `conocimiento/colombia/`: decisiones (`decisiones.md`), reglas
   (`reglas-negocio.md`), arquitectura (`arquitectura-objetivo.md`), brechas (`matriz-brechas.md`),
   términos (`diccionario.md`) y sesiones (`NOTAS.md`).
3. **Refinamos las reglas**: los errores y patrones aprendidos se escriben como "reglas aprendidas /
   vicios a evitar" dentro de las skills (ya incluidas en `colombia-adaptacion` y
   `eudiw-implementacion`).
4. **Verificamos**: con `/practicar` el agente nos examina y detecta huecos de conocimiento.

Estructura de memoria de Colombia (fuente de verdad local):
```
conocimiento/colombia/
├── NOTAS.md               # Contexto, sesiones, backlog, mapeo de roles propuesto
├── decisiones.md          # Decisiones tomadas (fecha, motivo) — acumulativas
├── reglas-negocio.md      # RN-xxx: cómo funciona el ecosistema CO
├── arquitectura-objetivo.md  # Arquitectura técnica por capas (con estado 🟢🟡🔴)
├── matriz-brechas.md      # Robustez UE → CO capa por capa
└── diccionario.md         # Términos bilingües UE→CO
```

Recomendación: cada vez que el agente aprenda algo nuevo de un trabajo, cierra la sesión pidiéndole
`guarda el aprendizaje` para que actualice la memoria. Así el ecosistema "se entrena" con el tiempo.

---

## 6. Avisos y límites honestos
- El contenido de los repos UE está bajo licencias propias (CC BY 4.0, etc.); respétalas en derivados.
- Nada de lo aquí contenido es asesoría legal. Para un producto real en Colombia, los textos
  normativos locales deben validarse con fuentes oficiales (las skills ya instruyen al agente a hacerlo).
- Los repos se clonaron con `--depth 1` (sin historial). Si necesitas historia, reclona completo.