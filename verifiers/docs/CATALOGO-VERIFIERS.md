# Catálogo de verifiers simulados (2026-10-22)

Qué verifiers faltan, de dónde sale cada caso y qué haría falta para simularlo en el ecosistema Avance.
Las fuentes son las del corpus del repositorio (`docs/corpus-eudi`): ARF y sus temas de discusión, RFC del consorcio EWC
(`eudi-srv-verifier-local-build/docs/references`) y los rulebooks colombianos. Lo que no está en el corpus se marca como tal.

## Ya construidos
| Verifier | Credencial | Caso |
|---|---|---|
| Discoteca (`/discoteca`) | PID (Registraduría) | Verificación de edad: solo `age_equal_or_over.18`, sin guardar nada |
| Banco (`/banco`) | PID; emite la **credencial de cuenta bancaria** (SD-JWT VC, QEAA) por cada cuenta y la **revoca** (status list) al cerrarla | Ingreso y apertura de cuenta sin contraseña; la wallet recibe la credencial al abrir la cuenta (QR + PIN) |
| Contratación (`/contratacion`) | Académica (UIS) + PID | Formación por nivel, solo graduados; identidad aparte y opcional; **firma del contrato de trabajo** desde la wallet (ver `FASE-7-firma-contratacion.md`) |
| EPS (`/eps`) | Carné e historia clínica (QEAA, emitidos por la EPS) | Afiliación y portal médico |
| Notaría (`/notaria`) | PID; emite 9 actos (PuB-EAA) | Poder, declaración, reconocimiento, fe de vida, autenticación de firma, permiso de menor, constancia de copia de escritura, declaración de convivencia, registro civil de nacimiento |
| Inscripción y becas (`/beca`) | PID + académica (presentación combinada) | Pre-evaluación sin identidad; identidad solo si cumple |
| Aerolínea (`/aerolinea`) | PID (nombre, documento, fecha de nacimiento, foto); emite el tiquete (QEAA); verifica además el permiso de salida de menor de la notaría | Venta de pasajes (propio o de un menor a cargo) y embarque con tiquete + PID; un menor en vuelo internacional presenta además el permiso en un segundo paso |
| Gimnasio (`/gym`) | PID para afiliarse; emite la **tarjeta de socio** (QEAA) y la verifica en la entrada y en la salida de cada sede | Membresía por plan, control de **ingreso y egreso** con aforo por sede en vivo, sin repetir entrada sin salir; la entrada solo ve nombre, plan y vigencia; el registro guarda el número de socio, no el documento; las pantallas de la puerta piden clave (ver `gym/README.md`) |
| Control de acceso (`/acceso`) | PID | Foto, nombre y documento mostrados al guardia, que decide; sin guardar la foto |
| Fondo de pensiones (`/pensiones`) | Fe de vida (PuB-EAA de la notaría) + PID | Prueba de supervivencia: emisor notarial de confianza, titular = PID, vigencia de 30 días; sin guardar datos |
| Cancillería (`/cancilleria`) | PID (nombre, documento, nacimiento, retrato); emite el pasaporte (PuB-EAA) | Solicitud, entrega única, reexpedición y reporte de pérdida; el retrato del PID va al pasaporte |
| Migración (`/migracion`) | PID + pasaporte (Cancillería) | Control de entrada o salida: comprueba emisor, vigencia, mismo documento y mismo nombre, muestra los dos retratos y el funcionario decide; sin guardar retratos |
| Carnet Estudiantil (`/uis-issuer`) | Lo emite el propio issuer de la UIS desde su página de programas (botón CARNET de cada programa ACTIVO); sin PID | Solo estudiantes ACTIVOS; datos básicos que pide una beca; vale hasta fin de periodo y lleva status list (revocable) |

## Casos reales de la UE y cómo se simulan

### Con las credenciales que ya existen (sin emisor nuevo)
1. **Admisión o beca con presentación combinada** (ARF, tema K). El ARF lo describe así: una universidad pre-evalúa
   a las personas con varias atestaciones de distintos emisores (PID + diploma + experiencia), y la identificación se
   pide después, en otra presentación; así se evita discriminar por el apellido. Ejemplo literal del ARF (§6.6.x):
   probar que se reside en una ciudad y que se tienen notas suficientes «sin revelar nombre, género ni dirección».
   *Aquí:* una solicitud DCQL con **dos credenciales** (PID: mayoría de edad / lugar; académica: nivel, estado, promedio).
   El portal aplica el umbral y muestra «cumple / no cumple» sin identidad. Pide cambios solo en el portal.
2. **Control de acceso con foto** (EWC RFC013, *Photo ID*). Credencial con retrato para entrar a un edificio, evento o
   campus. *Aquí:* el PID ya trae `picture`; el portal pide foto + nombre (o solo foto + mayoría de edad). Sin emisor nuevo.

### Con un emisor simulado nuevo (patrón de `wallet/eps` y `wallet/notaria` del otro desarrollo)
3. **Salud** (EPS Salud Cívica): afiliación (farmacia/IPS), ficha médica de urgencias (solo alergias y grupo sanguíneo,
   divulgación selectiva), carné de vacunación, citas médicas (afiliación vigente). Un solo emisor, cuatro verifiers.
   Es lo que pediste. Son datos sensibles (Ley 1581 de 2012): solo lo necesario, sin retención.
4. **Notaría**. El corpus trae tres rulebooks colombianos borrador: `actuacion-notarial-v1`,
   `identidad-verificada-notarial-v1`, `mayoria-edad-notarial-v1`. *Aquí:* verifier de trámite (PID + mayoría de edad)
   y emisor de la actuación notarial como PuB-EAA anulable con su status list.

### Dependen de capacidades de la wallet que no hemos comprobado (no los recomiendo todavía)
5. **Pagos con autenticación reforzada (SCA)** (ARF temas AA y W; EWC RFC007 y RFC008). Confirmar un pago con datos de
   transacción firmados por la wallet. Exige *transaction data* en OpenID4VP.
6. **Firma electrónica cualificada** (ARF tema AB). La app tiene entradas de firma; falta un prestador de firma remota.
7. **Pseudónimos** (ARF tema E, casos A–D: autenticación seudónima, registro con atributos, participación limitada
   —p. ej. una vez por persona—, seudónimo enlazable entre actores). Requiere soporte de la wallet.
8. **Representación de otra persona** (ARF tema I, p. ej. menores). Exige una atestación de representación.
9. **Licencia de conducción (mDL)**. Hay rulebook en el corpus, pero un hallazgo previo del otro desarrollo dice que la
   app usa el `docType` como espacio de nombres y no encontraría los datos del mDL.

## Orden propuesto
1. Admisión/beca (1) y control de acceso con foto (2): reutilizan lo construido y demuestran dos principios de la UE.
2. Salud (3): un emisor simulado y cuatro verifiers.
3. Notaría (4).
4. Lo del último bloque, solo después de confirmar el soporte de la wallet.
