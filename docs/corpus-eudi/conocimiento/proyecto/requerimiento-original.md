**Documento de Arquitectura Identidad Digital Soberana**

Se requiere la realización del documento **de arquitectura** para el proyecto de **identidad digital soberana** por parte del Arquitecto del proyecto, basado en el diagrama de arquitectura inicialmente construido con el equipo, de tal forma que el documento tenga en cuenta los estándares:

- **W3C DIDs v1.1** (define cómo funciona el método DID).

- **W3C VC Data Model v2.0** (modelo de las credenciales).

- **eIDAS 2.0** (para alinear con la UE).

- El **diagrama de arquitectura** y la **creación del DID / DID Document**.

El documento debe incluir, entre los temas más importantes, los siguientes:

1.  **Método DID + suite criptográfica**

2.  **Esquema de las credenciales verificables (VC)**

3.  **API del Wallet Backend** — teniendo en cuenta que **esta API sí se debe construir** (no solo configuración

4.  **Modelo de confianza (Trust Model)**

**Contexto y restricciones del trabajo**

- El ecosistema se construye sobre **componentes ya existentes** (tomados del diagrama de arquitectura), que se **documentan, configuran y parametrizan** con el mínimo desarrollo.

- **Excepción explícita**: la **API del Wallet Backend sí se construye**. Para el resto, el documento debe mantener el criterio de **configurar/parametrizar antes que desarrollar**.

- Todo componente citado debe rastrearse al **diagrama de arquitectura** o a los estándares listados.

**Temas a incluir en el documento**

**1. Método DID + suite criptográfica**

Decidir y justificar el método DID (p. ej. did:web, coherente con el VDR operado por terceros) y la suite criptográfica de las verification methods.

- Par de claves: privada en **Secure Hardware**, pública construye el DID.

- Componente que genera (SSI SDK + Secure Hardware) y componente que resuelve (DID Resolver).

- Coherencia con eIDAS 2.0.

**2. Esquema de las credenciales verificables (VC)**

Modelo de datos de las credenciales sobre el VC Data Model v2.0.

- Esquema JSON de credentialSubject por tipo de credencial.

- Mecanismo de aseguramiento (firma/protección).

- Metadata, **revocación (status list)** y lógica de presentaciones verificables (VP).

- Componentes: Issuer Backend + Signature Service (emite), Credential Store (almacena), Holder App / SSI SDK (presenta).

**3. API del Wallet Backend *(se construye)***

Como esta API **sí se desarrolla**, el documento debe:

- Endpoints REST: gestión del DID (registro, publicación, **respaldo cifrado del DID Document**), gestión de credenciales y **emisión de presentaciones (VP)**.

<!-- -->

- Alineación con **OID4VC (emisión)** y **OID4VP (presentación)**.

- Requests/responses, tipos de datos, códigos de error, autenticación y comunicación **Wallet Backend ↔ VDR**.

- Especificar **qué endpoints existen (configurar) vs. cuáles se construyen** dentro de la API.

**4. Modelo de confianza (Trust Model)**

Cómo se establece y gestiona la confianza; quién emite/verifica y bajo qué reglas.

- Roles: Issuer, Holder, Verifier, Trust Registry.

- Registro público de acreditaciones y gobernanza (Trust Registry Storage / Governance Policies).

- **Revocación** (Status List / Revocation Service) y consulta de estado en el VDR.

- Validación de presentaciones por el Verifier Backend (resolver DIDs, consultar servicios).

**Diagramas que se deben realizar**

El entregable debe incluir (como mínimo) los siguientes diagramas, todos construidos sobre el diagrama de arquitectura base:

1.  **Diagrama de componentes del ecosistema** — posición y conexión de cada componente existente (capas: protocols, SSI, wallet, governance, aplicaciones).

2.  **Diagrama de secuencia — Generación y registro del DID** — flujo desde la creación del par de claves en Secure Hardware hasta la publicación del DID Document en el VDR.

3.  **Diagrama de secuencia — Emisión de VC / Presentación de VP** — flujos OID4VC y OID4VP.

4.  **Diagrama del modelo de confianza** — relaciones entre Issuer/Holder/Verifier/Trust Registry, con las acreditaciones y la consulta de revocación.

5.  **Diagrama de despliegue**— dónde corre cada componente y su conectividad.

Cada diagrama debe **referenciar los componentes existentes** y distinguir visualmente **configuración vs. construcción** (especialmente la API del Wallet Backend).

**Aspectos adicionales a considerar**

Además de los cuatro temas principales, el documento de arquitectura debe tratar:

- **Normativa y cumplimiento**: alineación con eIDAS 2.0 (nivel de garantía, servicios de confianza).

- **Seguridad** de las claves: custodia en Secure Hardware, respaldo y recuperación (escenarios de pérdida del dispositivo).

- **Revocación y suspensión** de credenciales y de acreditaciones.

- **Interoperabilidad**: conformidad con estándares W3C y flujos OID4VC/OID4VP.

- **Decisión de Arquitectura (ADR)**: cada elección técnica con fundamento, alternativas consideradas y efecto sobre los componentes existentes.

- **Configuración vs. desarrollo**: inventario por componente indicando qué se parametriza y qué (mínimo) se construye; la API del Wallet Backend a profundidad.

- **Modelo de amenazas / riesgos** y cómo la arquitectura los mitiga.

**Estructura propuesta del documento**

**1. Introducción**

- 1.1 Objetivo y alcance

- 1.2 Referencias (W3C DIDs v1.1, VC Data Model v2.0, eIDAS 2.0, diagrama base)

- 1.3 Notación y criterios (configuración vs. construcción)

**2. Visión general de la arquitectura**

- 2.1 Principios y restricciones

- 2.2 Vista de componentes del ecosistema

- 2.3 Capas del ecosistema

**3. Método DID y suite criptográfica** 

- 3.1 Selección del método DID y fundamento

- 3.2 Suite criptográfica y verification methods

- 3.3 Generación y registro del DID (secuencia, diagrama 2)

- 3.4 Resolución del DID

**4. Esquema de credenciales verificables** 

- 4.1 Modelo de datos VC

- 4.2 Esquemas JSON por tipo de credencial

- 4.3 Mecanismo de aseguramiento

- 4.4 Revocación (status list)

- 4.5 Presentaciones verificables (VP)

**5. API del Wallet Backend** 

- 5.1 Contrato REST completo y endpoints

- 5.2 Flujos de emisión y presentación (OID4VC/OID4VP; secuencia)

- 5.3 Comunicación Wallet Backend ↔ VDR

- 5.4 Manejo de respaldo cifrado del DID Document

**6. Modelo de confianza** 

- 6.1 Roles y relaciones

- 6.2 Registro de acreditaciones y gobernanza

<!-- -->

- 6.3 Revocación y suspensión de confianza

- 6.4 Validación de presentaciones por el Verifier

**7. Seguridad, normativa y cumplimiento**

- 7.1 Alineación eIDAS 2.0

- 7.2 Custodia y respaldo de claves

- 7.3 Modelo de amenazas y mitigaciones

- 7.4 Interoperabilidad y estándares

**8. Inventario de configuración vs. construcción**

- 8.1 Tabla por componente: config / parametrización / desarrollo mínimo

- 8.2 Detalle del desarrollo de la API del Wallet Backend

**9. Decisiones de arquitectura**

- 9.1 Registro de decisiones con fundamento y alternativas

**10. Apéndices**

- Diagramas completos, glosario y referencias.
