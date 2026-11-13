# Firma digital, ECD/ONAC, datos personales y criptografía (verificado)

> Categoría: **NORMATIVO-CO**. Fuente: `investigacion/C-confianza-datos.md` (2026-10-14). Convención de estados: `README.md`. Los textos de ley se leyeron con resumen automático (**RA**);
> los certificados de ONAC se leyeron íntegros. El director verificó por su cuenta el alcance de Andes SCD en el PDF oficial. **No es asesoría legal.**

## 1. Andes SCD y el alcance de su acreditación — VERIFICADO (texto íntegro)
- **Andes Servicio de Certificación Digital S.A.** está acreditada por ONAC: certificado **16-ECD-004**, esquema CEA 3.0-07 V2, otorgada 2016-12-12, renovada 2024-12-12, última actualización **2026-06-05**, vence **2029-12-11** (onac.org.co/certificados/16-ECD-004.pdf).
- **Alcance técnico acreditado: RSA 2048 bits (entidad final) y RSA 4096 bits (CA raíz y subordinadas), SHA-256**, FIPS 140-2 nivel 3 (dispositivos locales y centralizado). **El certificado no menciona ECDSA ni curvas elípticas** (0 coincidencias de ECDSA/P-256/P-384/secp, verificado por el director).
- Alcance de servicios: certificados de representante legal, pertenencia a empresa, función pública, profesional titulado, persona natural/jurídica, comunidad académica y facturación electrónica; estampado cronológico; correo y SMS certificados. **No lista certificados para "emisores de credenciales"**.
- Contraste: **GSE S.A.** (16-ECD-001) incluye ECDSA P-256 (entidad final) y P-384 (CA) (VERIFICADO por el informe C, texto íntegro).
**Consecuencia para el piloto (ver D-012):** ES256/P-256 no está prohibido, pero **no está cubierto por la acreditación vigente de Andes SCD**; una CA subordinada P-256 para notarías emisoras queda fuera del alcance acreditado salvo ampliación ante ONAC.

## 2. Marco de ECD — VERIFICADO-RA
- Firma digital = fuerza y efectos de la manuscrita **solo si** es única, verificable, bajo control exclusivo, ligada al mensaje y conforme a los reglamentos (Ley 527/1999 art. 28 par.). La firma **electrónica** se rige por el Decreto 2364/2012 (confiabilidad "apropiada"; incluye datos biométricos y claves) — compilado en el Decreto 1074/2015 (2.2.2.47).
- Entidades de certificación: personas jurídicas públicas o privadas (incl. cámaras de comercio y **notarías**) acreditadas por **ONAC** (Ley 527 art. 29 mod. DL 19/2012 art. 160; Decreto 333/2014 arts. 2-3). Actividades: art. 30 Ley 527 (mod. DL 19/2012 art. 161), p. ej. certificados, estampado cronológico, "cualquier otra actividad relacionada con la creación, uso o utilización de firmas digitales y electrónicas".
- **Abierta** (al público, con remuneración; patrimonio mínimo 400 SMMLV, garantía ≥7.500 SMMLV) vs **cerrada** (solo entre entidad y suscriptor, sin remuneración) (Decreto 333/2014 arts. 4-10 = Decreto 1074/2015 2.2.2.48).
- **La SIC ya no autoriza ECD** (arts. 41-42 de la Ley 527 derogados por DL 19/2012 art. 176): **ONAC acredita; la SIC supervisa** (Decreto 333/2014 art. 20 = 2.2.2.48.3.11). El Decreto 333/2014 derogó el Decreto 1747/2000.
- Criterio vigente de acreditación: **CEA-3.0-07 V02** (antes CEA-4.1-10); su **contenido no se leyó**.
- **No existe** en Colombia el concepto "servicio de confianza cualificado" ni equivalente a lista de confianza (TL/LOTL) o sello cualificado: hay "entidad de certificación acreditada por ONAC".
**Matiz a la Ley 588/2000 (tema A):** la «autorización de la SIC» a notarías como entidades de certificación usa lenguaje anterior al DL 19/2012; hoy el régimen es acreditación ONAC (el Decreto 333 incluye a las notarías). Papel real actual de la SIC con notarías: NO VERIFICADO.

## 3. Datos personales — VERIFICADO-RA
- **Biometría = dato sensible** (Ley 1581/2012 art. 5). Su tratamiento exige **autorización explícita** y ninguna actividad puede condicionarse a entregar datos sensibles (art. 6; Decreto 1377/2013 art. 6 = 2.2.2.25.2.3). Consentimiento previo, expreso e informado (art. 4 c); el silencio no es autorización; el responsable conserva prueba (Decreto 1377 arts. 7-8).
- Deberes del **responsable** (art. 17) y del **encargado** (art. 18); aviso de privacidad, política de tratamiento y **accountability** ("ser capaz de demostrar") (Decreto 1377 arts. 13-15, 26-27).
- **Transferencia internacional** prohibida a países sin nivel adecuado, con excepciones (art. 26; Decreto 1377 art. 24). Autoridad: SIC (Delegatura de Protección de Datos).
- El **Decreto 1377/2013 fue compilado** en el Decreto 1074/2015 (cap. 2.2.2.25) salvo su art. 25 (contrato de transmisión, no compilado). **Citar hoy el 2.2.2.25.x.** No afirmar «derogado» sin leer el art. 3.1.1 del 1074.
- Criterio de la SIC (comunicado oficial 8-jul-2026, Res. SIC 45710 de 18-jun-2026, caso World Foundation): consentimiento no libre ni informado; cifrar/seudonimizar/fragmentar **no anonimiza** el dato biométrico.
- Reforma: Proyecto de Ley Estatutaria 282/2026 Cámara (Gaceta 1217, 8-sep-2026) **existe como proyecto**; trámite no verificado (SECUNDARIO en detalle).

## 4. Criptografía — ausencia (NO verificada de forma exhaustiva)
No se halló norma colombiana (ONAC, SIC, MinTIC) que prohíba o exija ECDSA o RSA. Solo: el alcance por ECD en los certificados ONAC y controles genéricos de criptografía del MSPI de MinTIC (sin lista de algoritmos). No se consultó la normativa de la Superfinanciera (relevante solo si el verificador es entidad vigilada).

## 5. Implicaciones propuestas (PROPUESTA-LOCAL, validar con jurídico)
1. La credencial (nombres, mayoría de edad, referencia de trámite) es dato personal **común**; el **cotejo biométrico** es **sensible**: la credencial no debe contener ni derivar la plantilla biométrica, solo el resultado (RN-015).
2. Un «aceptar» en pantalla de la wallet **no basta por sí solo**: se requiere información previa mínima (finalidad, carácter facultativo, derechos, responsable) y prueba del consentimiento.
3. Roles probables: **notaría = responsable** (decide finalidad y medios); **operador/aliado tecnológico del emisor = encargado** (contrato; el art. 25 del Decreto 1377 no se compiló: verificar plantilla); **verificador = responsable propio** de lo que recoge; AndesSCD según sus datos de certificados.
4. Evitar alojar o transferir datos fuera de Colombia sin revisar el art. 26.
