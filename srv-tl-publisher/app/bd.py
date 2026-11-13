"""Acceso a la base de datos del TLP.

Se usa PyMySQL directamente en vez de un ORM. La razón es que casi todo lo que
hace este servicio son tres consultas de lectura con `JOIN` y dos inserciones;
un ORM encima de un esquema ajeno que no controlamos añadiría una capa de
modelos que habría que mantener sincronizada a mano con cada cambio del
upstream, a cambio de nada.
"""

import json
from contextlib import contextmanager

import pymysql
import pymysql.cursors

import config


@contextmanager
def conexion():
    """Conexión con `autocommit` desactivado.

    Publicar toca tres tablas y o pasa entero o no pasa: si se inserta la fila
    en `published_lists` pero falla el `superseded_at` de la anterior, quedan
    dos listas vigentes para el mismo esquema y quien consulte `current.xml`
    recibe una u otra según el orden de la consulta.
    """
    con = pymysql.connect(
        **config.BD,
        cursorclass=pymysql.cursors.DictCursor,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        yield con
    finally:
        con.close()


def _json(valor, por_defecto):
    """El TLP guarda estructuras multiidioma como JSON dentro de columnas TEXT.

    No siempre: algunas filas llevan el texto plano. Se intenta parsear y, si no
    es JSON, se envuelve con el idioma por defecto en vez de reventar — una
    lista con un nombre mal formado sigue siendo mejor que ninguna lista.
    """
    if valor is None:
        return por_defecto
    if isinstance(valor, (list, dict)):
        return valor
    try:
        return json.loads(valor)
    except (json.JSONDecodeError, TypeError):
        return por_defecto


# ─── Lectura ─────────────────────────────────────────────────────────────────


def esquemas(con):
    """Los esquemas publicables, con la fila de `trusted_lists` que los respalda."""
    with con.cursor() as c:
        c.execute(
            """
            SELECT s.scheme_slug, s.scheme_name, s.tsl_type, s.is_lotl,
                   s.description, t.tsl_id, t.schemeTerritory, t.operator_id
              FROM tl_schemes s
              JOIN trusted_lists t ON t.tsl_id = s.tsl_id
             ORDER BY s.is_lotl, s.scheme_slug
            """
        )
        return c.fetchall()


def operador(con, operator_id):
    with con.cursor() as c:
        c.execute(
            "SELECT * FROM scheme_operators WHERE operator_id = %s", (operator_id,)
        )
        fila = c.fetchone()
    if not fila:
        raise RuntimeError(
            f"la lista apunta al operador {operator_id}, que no existe en "
            "scheme_operators. Ejecuta scripts/sembrar-trusted-list.sh"
        )
    fila["operator_name"] = _json(fila["operator_name"], [{"lang": "en", "text": "?"}])
    fila["EletronicAddress"] = _json(fila["EletronicAddress"], [])
    fila["postal_address"] = _json(fila["postal_address"], [])
    return fila


def _puntos_del_actor(con, service_id):
    """Los supply points que declara un actor en la administracion."""
    with con.cursor() as c:
        c.execute(
            "SELECT uri FROM admin_puntos WHERE actor_id = %s ORDER BY orden, punto_id",
            (service_id,),
        )
        return [f["uri"] for f in c.fetchall()]


def proveedores_con_servicios(con, scheme_slug):
    """Proveedores y servicios que pertenecen a un esquema.

    La pertenencia NO la decide una columna en el proveedor, sino el
    `service_type` de cada servicio, resuelto contra `service_type_registry`.
    Es lo correcto: el art. 22a define las listas por ROL del servicio, y un
    mismo proveedor puede prestar servicios de dos roles distintos.
    """
    with con.cursor() as c:
        c.execute(
            """
            SELECT p.tsp_id, p.name, p.trade_name, p.postal_address,
                   p.EletronicAddress, p.TSPInformationURI,
                   sv.service_id, sv.service_type, sv.digital_identity,
                   sv.status, sv.status_start_date, sv.qualifier,
                   sv.ServiceName, sv.SchemeServiceDefinitionURI,
                   r.canonical_uri, r.additional_info_uri, r.supply_points
              FROM trust_services sv
              JOIN service_type_registry r ON r.service_type = sv.service_type
              JOIN trust_service_providers p ON p.tsp_id = sv.tsp_id
             WHERE r.scheme_slug = %s
             ORDER BY p.tsp_id, sv.service_id
            """,
            (scheme_slug,),
        )
        filas = c.fetchall()

    proveedores = {}
    for f in filas:
        p = proveedores.setdefault(
            f["tsp_id"],
            dict(
                tsp_id=f["tsp_id"],
                name=_json(f["name"], [{"lang": "en", "text": "?"}]),
                trade_name=_json(f["trade_name"], []),
                postal_address=_json(f["postal_address"], []),
                electronic_address=_json(f["EletronicAddress"], []),
                information_uri=_json(f["TSPInformationURI"], []),
                servicios=[],
            ),
        )
        # Los supply points de un actor son los suyos, no los del tipo de
        # servicio: dos emisores de PID distintos tienen URLs distintas. El
        # esquema publicado los guarda por tipo (es lo que dice TS 119 612), asi
        # que aqui se leen de la administracion, que si es por actor. Si el actor
        # no declara ninguno, se usan los del tipo.
        puntos_propio = _puntos_del_actor(con, f["service_id"])
        supply_points = puntos_propio or _json(f["supply_points"], [])

        p["servicios"].append(
            dict(
                service_id=f["service_id"],
                # `canonical_uri` gana cuando existe: el upstream trae tres URIs
                # de ServiceTypeIdentifier a los que les falta el segmento
                # `TrstSvc`. Ver la nota en el seed.
                service_type=f["canonical_uri"] or f["service_type"],
                service_type_declarado=f["service_type"],
                digital_identity=_json(f["digital_identity"], []),
                status=f["status"],
                status_start_date=f["status_start_date"],
                qualifier=_json(f["qualifier"], []),
                name=_json(f["ServiceName"], [{"lang": "en", "text": "?"}]),
                definition_uri=_json(f["SchemeServiceDefinitionURI"], []),
                additional_info_uri=f["additional_info_uri"],
                supply_points=supply_points,
            )
        )
    return list(proveedores.values())


def ultima_publicacion(con, scheme_slug):
    with con.cursor() as c:
        c.execute(
            """
            SELECT * FROM published_lists
             WHERE scheme_slug = %s
             ORDER BY sequence_number DESC
             LIMIT 1
            """,
            (scheme_slug,),
        )
        return c.fetchone()


def publicacion(con, scheme_slug, secuencia=None):
    """Una publicación concreta, o la vigente si no se pide número."""
    with con.cursor() as c:
        if secuencia is None:
            c.execute(
                """
                SELECT * FROM published_lists
                 WHERE scheme_slug = %s AND superseded_at IS NULL
                 ORDER BY sequence_number DESC LIMIT 1
                """,
                (scheme_slug,),
            )
        else:
            c.execute(
                "SELECT * FROM published_lists WHERE scheme_slug = %s AND sequence_number = %s",
                (scheme_slug, secuencia),
            )
        return c.fetchone()


def todas_las_publicaciones_vigentes(con):
    with con.cursor() as c:
        c.execute(
            """
            SELECT p.* FROM published_lists p
             WHERE p.superseded_at IS NULL
             ORDER BY p.scheme_slug
            """
        )
        return c.fetchall()


# ─── Escritura ───────────────────────────────────────────────────────────────


def registrar_publicacion(con, *, tsl_id, scheme_slug, secuencia, version,
                          issue_date, next_update, xml_sha256, xml_path,
                          signer_cert_sha256, signer_serial, descripcion):
    """Registra una publicación y marca como superada la anterior.

    Las cuatro operaciones van en una transacción. El `UNIQUE (scheme_slug,
    sequence_number)` es la red de seguridad real: si dos publicaciones
    concurrentes calculan el mismo número, la segunda falla con un error de
    clave duplicada en vez de sobrescribir una lista ya publicada.
    """
    with con.cursor() as c:
        c.execute(
            """
            UPDATE published_lists SET superseded_at = NOW()
             WHERE scheme_slug = %s AND superseded_at IS NULL
            """,
            (scheme_slug,),
        )
        c.execute(
            """
            INSERT INTO published_lists
                (tsl_id, scheme_slug, sequence_number, version, issue_date,
                 next_update, xml_sha256, xml_path, signer_cert_sha256, signer_serial)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            """,
            (tsl_id, scheme_slug, secuencia, version, issue_date, next_update,
             xml_sha256, xml_path, signer_cert_sha256, signer_serial),
        )
        # `trusted_list_updates` existe en el esquema del upstream desde el
        # principio y hasta ahora no escribia nadie: 0 filas. Es el registro de
        # auditoria de la lista, asi que aqui es donde toca alimentarlo.
        c.execute(
            """
            INSERT INTO trusted_list_updates (tsl_id, update_date, description)
            VALUES (%s, NOW(), %s)
            """,
            (tsl_id, descripcion),
        )
        # `trusted_lists.SequenceNumber` es lo que ve el operador en la interfaz
        # del TLP. Si no se actualiza, la interfaz muestra un numero distinto
        # del que lleva la lista publicada.
        c.execute(
            "UPDATE trusted_lists SET SequenceNumber = %s, issue_date = %s, next_update = %s WHERE tsl_id = %s",
            (secuencia, issue_date, next_update, tsl_id),
        )
    con.commit()
