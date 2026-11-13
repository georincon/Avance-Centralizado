# El ecosistema Avance explicado para todo público

Resumen con analogías, pensado para presentarlo a un grupo sin conocimientos técnicos. Para el detalle técnico (roles ARF, rutas, anclas
de confianza) ver `ECOSISTEMA.md` en la raíz del repositorio.

## La idea en una frase

Es una **billetera digital en el celular** donde guardas tus documentos (cédula, licencia, carné) en versión electrónica. Cuando alguien
te pide uno, **tú decides qué compartes y solo compartes lo necesario**. Ejemplo: probar que eres mayor de 18 años sin mostrar tu nombre
ni tu número.

## Los tres papeles

```
   EMISOR  ──── entrega el documento ────►  TÚ (con tu wallet)  ──── lo presentas ────►  VERIFICADOR
 (quien lo expide)                          (el celular lo guarda)                        (quien lo pide)
```

- **Emisor:** la entidad oficial que expide el documento (Registraduría, Tránsito, universidad…).
- **Titular y su wallet:** tú y la app que guarda y presenta tus documentos.
- **Verificador:** el sitio o negocio que necesita comprobar algo (banco, discoteca, gimnasio, aerolínea…).

Todos se fían unos de otros gracias a una **capa de confianza**: listas firmadas con las entidades autorizadas, como un directorio de
notarías reconocidas.

## Los actores del ecosistema

| Actor | Qué hace |
|---|---|
| **New Wallet** (app Android) | Guarda tus documentos, muestra qué vas a compartir y pide tu confirmación con huella o PIN |
| **Proveedor de wallet** | Certifica que la app es legítima |
| **Registraduría** | Emite la **cédula digital (PID)**, la identidad de base de la que dependen las demás credenciales |
| **Dirección de Tránsito** | Emite la **licencia de conducción digital**, por categorías |
| **UIS (universidad)** | Emite la **credencial académica** y el **carné estudiantil** |
| **Notaría** | Emite actos notariales (poder, declaración, fe de vida…). Una plataforma externa (SIGNO) los pide por una API |
| **EPS, gimnasio, aerolínea, cancillería** | Emiten carné de afiliado, tarjeta de socio, tiquete y pasaporte |
| **Banco, discoteca, control de acceso, contratación, migración, pensiones, beca** | Son los verificadores: piden la prueba que necesitan y la comprueban |

## Los servicios de apoyo (lo que no se ve)

- **Onboarding:** la primera vez, fotografías tu cédula, escribes tus datos y te tomas una selfie para comprobar que eres tú.
- **Listas de confianza y validador:** dicen qué emisores y verificadores son de fiar.
- **Listas de estado:** permiten anular un documento (por ejemplo, si lo pierdes).
- **Keycloak:** el «portero» de cada emisor; entrega la autorización sin pedirte contraseñas.
- **Servicio de firma electrónica (Andes SCD):** para firmar documentos desde la wallet.
- **Panel de control y proxy único:** para operar todo y exponerlo con una sola dirección.

## Cómo funciona, paso a paso

1. **Obtener tu cédula digital.** Haces el onboarding con tu cédula y una selfie. La Registraduría compara tus datos y tu cara con su
   registro. Si coinciden, entrega el documento a tu wallet.
2. **Obtener otros documentos.** Cada entidad emite el suyo. No usas contraseñas: la identidad se prueba una vez y se convierte en un
   permiso de un solo uso y muy corta vida.
3. **Usarlos.** El verificador pide solo lo necesario. La wallet muestra qué se compartiría y quién lo pide, y tú confirmas. El
   verificador comprueba que la firma sea de un emisor confiable, que el documento esté vigente y que sea tuyo.
4. **Anularlos.** Si pierdes el celular o hay fraude, la entidad revoca el documento y deja de ser válido en todos lados.

## Por qué es seguro y respeta la privacidad

- **Firmas digitales:** el documento no se puede alterar sin que se note.
- **Divulgación selectiva:** compartes solo el dato necesario.
- **Copias de un solo uso:** evita que dos verificadores te sigan entre sí.
- **Sin base central:** cada entidad guarda lo suyo y el verificador no guarda nada.
- **Tú decides y confirmas:** nada se comparte sin tu aprobación.
- **Revocación inmediata.**

## Una historia de ejemplo

Geovani instala la wallet y hace el onboarding. La Registraduría le entrega su cédula digital. Con ella pide su licencia en Tránsito y su
carné en la UIS. Al entrar al gimnasio, muestra solo su tarjeta de socio. En un trámite del banco, comparte nombre y cédula. Si pierde el
celular, la Registraduría revoca todo.

## Qué es de prueba (conviene decirlo)

- Las entidades son **simuladas** y los datos son de prueba.
- La comparación de la selfie mide parecido, pero **no hay prueba de vida real** (no distingue una foto de una persona).
- Los emisores y verificadores del entorno son del proyecto, no entidades reales.

## Glosario mínimo

- **PID:** cédula digital, la identidad base.
- **mDL:** licencia de conducción móvil.
- **Credencial:** cualquier documento digital firmado.
- **eIDAS 2.0 y ARF:** la norma europea y su arquitectura de referencia, que sigue este ecosistema.

## Cómo contarlo

- Orden sugerido: el problema de hoy, la billetera digital, los tres papeles, una historia completa, por qué es seguro y la demo en el
  teléfono.
- Analogías útiles: la **billetera física**, el **sello de la notaría** (la firma) y el **directorio de entidades autorizadas** (las
  listas de confianza).
