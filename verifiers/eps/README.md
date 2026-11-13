# EPS de ejemplo: carné e historia clínica en la wallet

**EPS Salud Avance** es una EPS ficticia. No se usa la marca de ninguna EPS real.
Es emisor y verificador a la vez, como la notaría (`../notaria`):

- **Verificador.** El afiliado se identifica presentando su PID con el verifier de
  la EPS (`verifier-eps`). Con ese mismo verifier, una farmacia, una IPS o un
  médico comprueban lo que el paciente les presenta.
- **Emisor.** Entrega a la wallet dos credenciales (SD-JWT VC por OpenID4VCI),
  firmadas por la EPS y registradas en la lista **QEAA**:
  - **Carné de afiliado**: EPS, afiliado, número de afiliación, régimen, tipo,
    plan, IPS asignada, estado y fecha de afiliación.
  - **Resumen de historia clínica**: grupo sanguíneo, alergias, enfermedades
    crónicas, medicamentos actuales, vacunas, última consulta y observaciones.

```bash
./levantar.sh            # → https://civica.avance.org.co/eps/
pruebas/probar-eps.sh    # prueba completa sin teléfono
```

## Lo importante: cada dato se comparte por separado

La historia clínica es un dato sensible (Ley 1581 de 2012), y SD-JWT permite
divulgar cada dato por separado. Un médico de urgencias que pide solo
**alergias y grupo sanguíneo** recibe eso y el nombre y documento del paciente;
no recibe medicamentos, diagnósticos ni enfermedades crónicas. La wallet le
muestra al paciente qué se pide y él marca qué da.

## Cómo se usa

**Afiliado** (https://civica.avance.org.co/eps/ → Entrar a Mi EPS):
1. Te identificas con el PID: documento, nombres, apellidos y nacimiento.
2. Si no estás afiliado, te afilias eligiendo régimen e IPS. Los datos
   personales salen del PID.
3. Ves tu carné y tu historia, y los recibes en la wallet con un QR y un PIN.
4. «Lo que has recibido»: cada copia entregada, con su estado (vigente,
   reemplazada o revocada). Puedes revocar una, por ejemplo si pierdes el teléfono.

**Administración** (https://civica.avance.org.co/eps/admin, con la contraseña
`ADMIN_CLAVE` de `.env`):
- **Registra médicos con el verifier:**
  1. La página muestra un QR y el médico presenta su PID desde su wallet.
  2. Nombre y cédula llegan verificados: no se pueden escribir a mano.
  3. El administrador añade el registro médico (RETHUS) y la especialidad.

  El servidor toma los datos del resultado del verifier, no del navegador.
- **Aprueba o rechaza las solicitudes** de los médicos que se registraron con su
  PID.
- Activa, desactiva o elimina médicos. Es lo único con contraseña.

**Médico de la EPS** (https://civica.avance.org.co/eps/medico):
- **Entra con su wallet, sin contraseña.** Presenta su PID con el verifier de la
  EPS y pasa solo si su cédula está registrada y activa. Si el administrador lo
  desactiva, la sesión se le cierra en la siguiente acción.
- **Si no está registrado, se registra con su PID.** Nombre y cédula salen del
  PID verificado; él solo escribe su registro médico y su especialidad. Queda
  **pendiente** hasta que el administrador lo apruebe. Sin esa aprobación,
  cualquiera con un PID podría editar historias clínicas ajenas.
- En la historia queda quién la actualizó: el nombre sale del PID verificado y
  el registro médico, del administrador.
- Busca afiliados y edita la historia (grupo sanguíneo, alergias, crónicas,
  medicamentos, vacunas, última consulta, observaciones) y la afiliación
  (estado e IPS).
- **Al guardar la historia, las copias que el paciente tenía en su wallet
  quedan reemplazadas.** Su bit en la status list pasa a 1, así que una historia
  vieja ya no la acepta ningún verifier, y el paciente pide la nueva. Suspender
  o retirar la afiliación revoca los carnés.

**Quien atiende** (https://civica.avance.org.co/eps/ → Verificar):
- *Carné de afiliado*: para farmacias e IPS. Muestra si la afiliación está activa.
- *Historia clínica*: marca qué partes necesitas y la wallet del paciente pide su
  consentimiento.

Para cargar **tu propia historia**:
1. Entra a Mi EPS con tu PID y afíliate.
2. Entra al portal médico con tu wallet y solicita acceso, o pide al
   administrador que te registre.
3. En la administración, aprueba la solicitud.
4. Vuelve a entrar al portal médico, búscate y llena tu historia.
5. Vuelve a Mi EPS y recíbela en la wallet.

## Por qué QEAA

En este ecosistema hay tres listas de emisores: PID, QEAA y PUBEAA. Una EPS no es
un organismo público, aunque administra recursos públicos de salud, ni un
prestador cualificado eIDAS. Se registra en **QEAA** para estrenar esa lista, que
estaba vacía, y probar el contexto `QEAA` del trust-validator. Con PUBEAA
funcionaría igual: el rol se elige en `scripts/registrar.py`.

## Cómo está hecha

Es la notaría con otra fuente de datos. Comparten las piezas copiadas, sin
modificar salvo los textos:
- `abca.js`: la WIA de la wallet, comprobada con el trust-validator;
- `sdjwt.js` y `estado.js`: SD-JWT VC y status list;
- `verifier.js`;
- el emisor OpenID4VCI (`emisor.js`): código preautorizado, PIN, DPoP y CORS en
  la metadata.

Lo propio:

| Archivo | Qué es |
|---|---|
| `src/documentos.js` | Los dos tipos de credencial: `vct`, nombre, colores, datos, y cómo se arman desde el afiliado |
| `src/medicos.js` | `datos/medicos.json`: los médicos registrados por el administrador |
| `src/afiliados.js` | `datos/afiliados.json`: afiliados, historia (con versión) y **emisiones**, cada copia entregada con su índice en la status list y su estado |
| `src/semilla.json` | Afiliados de prueba (María 1020304050, Carlos 79876543) con historias **ficticias** |
| `src/servidor.js` | Portal del afiliado, portal médico, verificación |
| `src/publico/index.html` · `medico.html` · `admin.html` | Portal del afiliado (y verificar), portal médico, administración |

**Cada copia entregada tiene su propio índice en la status list** (se registra
al emitir). Por eso se puede revocar una sin tocar las demás, y reemplazar todas
las historias anteriores cuando cambia la historia.

Los datos de salud **no van al log**: solo eventos («historia actualizada»,
«entregado: Carné de afiliado»).

## Prueba

`pruebas/probar-eps.sh` hace de afiliada (María), de su wallet, de una farmacia,
de urgencias y del médico de la EPS:

1. Se identifica con el PID.
2. Recibe el carné y la historia. Comprueba la firma y que la EPS esté en QEAA.
3. La farmacia verifica el carné.
4. Urgencias pide solo alergias y grupo sanguíneo, y llegan solo esos dos, más
   el nombre y el documento.
5. Un médico sin registrar (la propia María, que es el único PID de prueba) se
   identifica con su PID y solicita acceso. Mientras está pendiente no entra;
   cuando el administrador lo aprueba, entra con su wallet. Después, el
   administrador lo registra él mismo con el verifier; sin la presentación del
   PID, el registro se rechaza.
6. El médico actualiza la historia, que sube de versión, y urgencias rechaza
   la historia vieja: «el documento fue anulado».

Cada ejecución modifica la historia de María: añade una alergia de prueba y sube
la versión. Para volver a la semilla, borra `datos/afiliados.json`.

## Problemas conocidos

Los mismos de la notaría (ver `../notaria/README.md`):
- tras el primer registro, el trust-validator tarda hasta 20 minutos en ver la
  lista QEAA, o se reinicia;
- en la app hay que actualizar las listas de confianza.

Además:
- la historia se guarda como texto («Penicilina; Mariscos»), no codificada en
  SNOMED o CIE-10, salvo el diagnóstico que escriba el médico;
- es un resumen, no la historia clínica completa.

No es una EPS real y sus credenciales no tienen validez.
