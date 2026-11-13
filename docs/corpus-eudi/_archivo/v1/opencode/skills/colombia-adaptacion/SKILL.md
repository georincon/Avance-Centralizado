---
name: colombia-adaptacion
description: >-
  Usar cuando se trabaje la adaptacion del ecosistema EUDI Wallet a Colombia: analisis de brechas
  UE vs Colombia, marco legal local (identidad, habeas data), MinTIC/transformacion digital,
  instituciones (roles que ejerce cada autoridad), credenciales y atributos prioritarios, reglas de
  negocio, plan de trabajo. Activar con "Colombia", "MinTIC", "cedula", "identidad digital",
  "adaptacion", "gap analysis", "marco legal", "attributos colombianos".
---

# Adaptación del ecosistema EUDI Wallet a Colombia

## Filosofía: adaptar, no copiar
La EUDI Wallet nace de un mandato regulado (eIDAS2) dentro del mercado único europeo. En Colombia no
existe ese mandato supranacional; la adaptación requiere traducir cada principio a roles,
instituciones, normas y servicios locales.

## Metodología por capas (matriz de brechas)
Para cada capa elabora una fila en una matriz comparativa (puedes guardarla en
`conocimiento/colombia/`):

| Capa | En la UE (principio) | Situación en Colombia | Brecha | Acción propuesta |
|------|----------------------|----------------------|--------|------------------|
| Marco legal | eIDAS2 / Implementing Acts | (investigar: normativa de identidad, firma electrónica, habeas data Ley 1581/2012 y su reglamentación, e.trámite) | | |
| Roles e instituciones | PID provider, wallet provider, attestation providers, WRP, DPA, supervisores | (quién emitiría la identidad, quién operaría la wallet, quién emite atributos, quién verifica, quién vigila datos) | | |
| Modelo de datos | PID/EAA, SD-JWT, mDL, rulebooks | (qué atributos de la cédula, registro civil, salud, educación) | | |
| Confianza | trusted lists / LoTEs, certificación | (registro de confianza local, acreditación) | | |
| Certificación y riesgo | FCAF + evaluación de seguridad | | | |
| Interoperabilidad | protocolos OpenID4VCI/VP, proximity | | | |
| Accesibilidad | requisitos del ARF cap. 8 | | | |
| Privacidad/datos | GDPR/DGA | (régimen de datos personales colombiano) | | |

## Qué investigar SIEMPRE con fuentes reales del año en curso
- Agenda digital / política de identidad digital del **MinTIC** y el estado actual de la cédula y
  documento de identidad electrónico.
- Marco de firma electrónica colombiano y servicios de confianza.
- Protección de datos personales (Ley Estatutaria 1581/2012 y normas que la desarrollan) y su
  supervisión (SIC - Superintendencia de Industria y Comercio).
- Sector público: interoperabilidad (ley de Gobierno Digital), carpeta ciudadana, "Ventanilla Única".
- Usa `websearch` y cita la fuente con URL y fecha. NO afirmes normativa de memoria.

## Entregables recomendados (guardar en `conocimiento/colombia/`)
1. `NOTAS.md` — contexto, decisiones, backlog de investigación, dudas abiertas.
2. `matriz-brechas.md` — la matriz completa por capa (punto de partida).
3. Rulebooks propuestos (basados en el template oficial del catálogo) para atributos/credenciales
   prioritarios.
4. Plan de trabajo iterativo: fase de modelado → pilotos → arquitectura técnica → MVP.

## Reglas
- No des por sentado que Colombia adoptará el marco UE tal cual; pregunta al usuario por el alcance
  (wallet ciudadana vs. conjunto de emisores vs. verificación en el Estado) y ajusta.
- Separa siempre lo que es decisión regulatoria (humana/institucional) de lo técnico.

## Reglas aprendidas y vicios a evitar (checklist de calidad CO)
Antes de dar una respuesta "Colombia por terminada", verifica:
- [ ] Usé `conocimiento/colombia/` (decisiones, reglas de negocio, diccionario) y respeté lo ya decidido.
- [ ] Distinguí normativo UE ↔ ARF/técnico ↔ estándar ↔ preliminar ↔ **propuesta local**.
- [ ] No afirmé normativa colombiana de memoria; toda ley/decreto citado tiene fuente oficial y URL.
- [ ] No usé siglas de roles sin definirlas (Registrador, Access CA, LoTE… uso el `diccionario.md`).
- [ ] Cada credencial propuesta se apoya en el `rulebook template` y en annex-3.01/3.02 como modelos.
- [ ] Mejoras/referencias nuevas detectadas: quedaron escritas (regla, decisión o backlog).

## Vicios típicos (evitar)
- ❌ Traducir "trusted list" por "lista de confianza" sin aclarar que en Colombia no existe aún el
  equivalente registrado; hay que marcar la brecha.
- ❌ Proponer "adoptar GDPR" como si fuese aplicable; se refiere al régimen local de datos.
- ❌ Copiar la certificación CAB/FCAF sin decir quién la acreditaría localmente.
- ❌ Afirmar que la cédula ya es un PID interoperable; es una propuesta, no un hecho.