# Marco institucional colombiano para identidad digital (inventario verificado)

> Inventario de instituciones, normas y servicios existentes relevantes para el ecosistema de
> identidad digital en Colombia. Verificado vía web en oct 2026 (fuentes oficiales citadas).
> Categoría: **propuesta local basada en fuentes** (no es normativa UE; es el estado real del país).

## Piezas institucionales que YA existen (son activos del ecosistema)

### 1. Agencia Nacional Digital (AND) — articulador y prestador de Servicios Ciudadanos Digitales
- Entidad descentralizada indirecta adscrita al MinTIC; **articula y presta** los "Servicios
  Ciudadanos Digitales (SCD)": **Autenticación Digital**, **Interoperabilidad** y **Carpeta
  Ciudadana Digital**.
- Fuente: Decreto 620 de 2020 (art. 2.2.17.1.4) y sitio oficial `and.gov.co/servicios-ciudadanos-digitales`
- La Autenticación Digital define **niveles de confianza**: bajo–medio–alto (con certificados
  digitales) y **muy alto** (con mecanismos de la RNEC). → Es el lugar natural para el Registrador
  y rol de autenticación del ecosistema.
- NOTA legal: la identificación por medios digitales con cédula digital/biometría se rige por
  disposiciones de la RNEC (Decreto 620/2020, art. 2.2.17.1.3).

### 2. Registraduría Nacional del Estado Civil (RNEC) — fuente autóctona de identidad
- Emite la **cédula de ciudadanía** (física y **digital**, "equivalente funcional" desde dic 2020;
  vigencia 10 años; estándar OACI 9303 para documentos de viaje/lectura mecánica).
- Basa su identificación en **biometría facial y dactilar**; la versión digital se activa con QR +
  reconocimiento facial en la app "Cédula Digital Colombia".
- **Controversia institucional relevante**: la cédula digital se implementó vía acto administrativo
  (Decreto 620/2020 + Circular RNEC 011/2020); la Corte Constitucional ha indicado que la regulación
  de la identificación tiene **reserva de ley** (Congreso). Existen proyectos de ley para otorgar a
  la RNEC facultades expresas (p. ej. fijar características y formato digital de la cédula).
  → **Brecha normativa**: la evolución a un PID tipo wallet requiere base legal en ley.
- Fuente: `registraduria.gov.co/-Cedula-de-ciudadania-digital`, `minjusticia.gov.co` (LegalApp),
  MOE (análisis sobre la facultad legal de la RNEC), Decreto 620/2020.

### 3. SIC (Superintendencia de Industria y Comercio) — vigilancia de datos y firma
- Autoridad de protección de datos personales (Ley 1581/2012, Delegatura de Protección de Datos).
- Vigila entidades de certificación de firma digital. → Rol análogo al **Supervisory Body**.
- Fuente: Ley 1581/2012 (art. 19-20); SitIO SIC.

### 4. ONAC (Organismo Nacional de Acreditación de Colombia)
- Acredita **entidades de certificación** de firma digital/electrónica (Ley 527/1999 reformada).
  → Análogo al **National Accreditation Body** para CABs.
- Fuente: Ley 527 de 1999 (art. 30-31); normogramas cancillería/AGN.

### 5. MinTIC
- Rector de Gobierno Digital (Decreto 1078/2015; Decreto 767/2022), Estrategia Nacional Digital
  (END 2023-2026), Política de Gobierno Digital (PND Ley 2294/2023; CONPES 4144/2025 IA).
- Define lineamientos para SCD y las políticas de TI (seguridad, datos personales).
  → Rector y articulador normativo del ecosistema (análogo a la "Comisión" en la coordinación del
  marco, pero bajo el derecho colombiano).

## Marcos normativos de referencia
- **Ley 527 de 1999**: mensajes de datos, comercio electrónico, firmas digitales, entidades de
  certificación (reglamentada por Decreto 1747 de 2000; Decreto 333 de 2014). La firma digital tiene
  atributos (unicidad, verificable, control exclusivo, ligada al mensaje) con fuerza de firma
  manuscrita (art. 28).
- **Ley Estatutaria 1581 de 2012** + Decreto 1377/2013: régimen de datos personales (hábeas data);
  autorización previa, expresa e informada; principios de necesidad, finalidad, proporcionalidad.
  → En diseño del ecosistema, integrar estos principios a la RN-004/RN-010.
- **Decreto 620 de 2020**: SCD (autenticación/interoperabilidad/carpeta) y cédula digital.
- **Decreto 1078 de 2015 / Decreto 767 de 2022**: Política de Gobierno Digital y Marro de Referencia
  de Arquitectura Empresarial (MRAE).
- **END 2023-2026 / PND 2022-2026**: apuesta nacional por transformación digital; incluye impulso a
  "modelos de identidad digital y masificación de servicios ciudadanos digitales".

## Hecho relevante de 2026 (oportunidad de articulación)
- **MinTIC + AND + CDPI** realizaron un sprint (may-2026) con **prototipos de credenciales
  verificables (VC)** en varias entidades: "Cédula Rural" (Min. Agricultura), Migración Colombia,
  Min. Relaciones Exteriores, Policía Nacional. Objetivo declarado: desplegar **infraestructura
  digital pública (DPI)**. Fuente: `and.gov.co` (noticia 11 jun 2026).
  → El concepto de credenciales verificables ya está en la agenda oficial: nuestro ecosistema
  puede alinearse (y aportar los patrones EUDI Wallet como referencia técnica).

## Mapa preliminar UE → Colombia
| Rol en UE (ARF) | Correspondencia/pendiente en CO |
|---|---|
| Registrador | AND (articulación SCD) o autoridad por definir por ley; pendiente decisión |
| PID Provider | RNEC (cédula digital como PID; requiere base legal) |
| Wallet Provider | Por definir (Estado/MinTIC-AND y/o operadores certificados) |
| PuB-EAA Providers | Entidades públicas con fuentes auténticas (MinAgricultura, Salud, Educación, Migración, etc.) |
| Access CA / Provider reg. cert | Entidades de certificación acreditadas (ONAC/SIC) — PKI de firma a aprovechar |
| Supervisory Body (datos) | SIC |
| CAB / acreditación | ONAC |
| Trusted List / LoTE análogo | Por crear (lista nacional de entidades de confianza) |
| Marco legal | Por construir: reserva de ley para identidad digital (proyectos en trámite) |