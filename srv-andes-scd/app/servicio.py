"""Logica compartida: usuarios y credenciales de firma."""

from __future__ import annotations

from cryptography import x509

from . import ca, config, db, seguridad


class AutenticacionError(Exception):
    pass


def autenticar_o_registrar(documento: str, pin: str, nombre: str) -> tuple[str, bool]:
    """Valida el PIN de un firmante, o lo registra si es la primera vez.

    En el prototipo la identidad no se comprueba (etapa 2: presentacion del PID). Devuelve
    (nombre, es_nuevo).
    """
    documento = documento.strip()
    if not documento.isdigit() or not (5 <= len(documento) <= 12):
        raise AutenticacionError("El número de documento debe tener entre 5 y 12 dígitos.")
    if not seguridad.pin_valido(pin):
        raise AutenticacionError("El PIN debe tener entre 4 y 8 dígitos.")
    with db.transaccion() as c:
        fila = c.execute("SELECT * FROM usuarios WHERE documento = ?", (documento,)).fetchone()
        if fila is None:
            nombre = nombre.strip()
            if len(nombre) < 3:
                raise AutenticacionError("Escribe tu nombre completo para registrarte.")
            sal = seguridad.sal_nueva()
            c.execute(
                "INSERT INTO usuarios (documento, nombre, pin_sal, pin_hash, creado) VALUES (?,?,?,?,?)",
                (documento, nombre, sal, seguridad.hash_pin(pin, sal), db.ahora()),
            )
            return nombre, True
        if fila["intentos"] >= 5:
            raise AutenticacionError("Demasiados intentos fallidos. Pide que te restablezcan el PIN.")
        if not seguridad.comparar(
            seguridad.hash_pin(pin, fila["pin_sal"]).hex(), bytes(fila["pin_hash"]).hex()
        ):
            c.execute("UPDATE usuarios SET intentos = intentos + 1 WHERE documento = ?", (documento,))
            c.commit()
            raise AutenticacionError("Documento o PIN incorrectos.")
        c.execute("UPDATE usuarios SET intentos = 0 WHERE documento = ?", (documento,))
        return fila["nombre"], False


def registrar_desde_pid(documento: str, nombre: str) -> tuple[str, bool]:
    """Firmante identificado con su PID: lo registra la primera vez (sin PIN) y devuelve (nombre, es_nuevo).

    El documento y el nombre vienen del PID que valido el verifier, no de lo que escriba la persona. Si ya estaba
    registrado (p. ej. con PIN, antes de usar el PID), se conserva su registro y su certificado.
    """
    documento = documento.strip()
    if not documento or len(documento) > 20:
        raise AutenticacionError("El documento del PID no es válido.")
    with db.transaccion() as c:
        fila = c.execute("SELECT nombre FROM usuarios WHERE documento = ?", (documento,)).fetchone()
        if fila is not None:
            return fila["nombre"], False
        c.execute(
            "INSERT INTO usuarios (documento, nombre, pin_sal, pin_hash, creado) VALUES (?,?,?,?,?)",
            (documento, nombre.strip(), b"", b"", db.ahora()),
        )
        return nombre.strip(), True


def id_credencial(documento: str) -> str:
    return f"andes-{documento}"


def asegurar_credencial(autoridad: ca.Autoridad, documento: str) -> str:
    """Una credencial de firma por persona: la crea (clave + certificado) la primera vez.

    Si la que tenia es de otro tipo de clave que el configurado (config.TIPO_CLAVE), la reemplaza: revoca
    el certificado anterior (`superseded`) y emite uno nuevo con el mismo identificador.
    """
    cid = id_credencial(documento)
    with db.transaccion() as c:
        existe = c.execute("SELECT serie, cert_pem FROM credenciales WHERE id = ?", (cid,)).fetchone()
        if existe:
            cert = x509.load_pem_x509_certificate(bytes(existe["cert_pem"]))
            if ca.tipo_de_clave(cert.public_key()) != config.TIPO_CLAVE:
                ca.revocar_certificado(autoridad, existe["serie"], "superseded")
                c.execute("DELETE FROM credenciales WHERE id = ?", (cid,))
            elif not ca.revocacion_por_http(cert):
                # Certificado emitido con direcciones de revocacion https (los visores de PDF de Linux no lo validan): se
                # emite uno nuevo. El anterior NO se revoca: las firmas ya hechas con el siguen siendo validas.
                c.execute("DELETE FROM credenciales WHERE id = ?", (cid,))
            else:
                return cid
        usuario = c.execute("SELECT nombre FROM usuarios WHERE documento = ?", (documento,)).fetchone()
        clave = ca.generar_clave_usuario()
        cert_pem, serie = ca.emitir_certificado_usuario(
            autoridad, clave.public_key(), usuario["nombre"], documento
        )
        c.execute(
            "INSERT INTO credenciales (id, documento, serie, cert_pem, clave_cifrada, creado) VALUES (?,?,?,?,?,?)",
            (cid, documento, serie, cert_pem, ca.cifrar_clave(clave), db.ahora()),
        )
    return cid


def credencial(cid: str):
    with db.transaccion() as c:
        return c.execute("SELECT * FROM credenciales WHERE id = ?", (cid,)).fetchone()


def credenciales_de(documento: str):
    with db.transaccion() as c:
        return c.execute("SELECT * FROM credenciales WHERE documento = ?", (documento,)).fetchall()
