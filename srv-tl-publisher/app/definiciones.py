"""Definiciones publicadas de los URIs propios que aparecen en las listas.

POR QUE ESTO EXISTE, Y POR QUE NO ES RELLENO

Dos sitios de cada Trusted List apuntan a URIs que son nuestros y no de ETSI:

  - `SchemeTypeCommunityRules`, porque este esquema NO es `EUcommon`: no es un
    esquema de la Union ni nuestra raiz esta en la LOTL de la Comision.
    Declarar `EUcommon` seria mentir en un campo que existe justamente para
    decir bajo que reglas opera la lista.
  - `AdditionalServiceInformation` de cada servicio, que senala que tres de los
    `ServiceTypeIdentifier` no estan en el registro de ETSI.

Un URI en esos campos que devuelve 404 es exactamente el mismo error que un AIA
que no responde: una promesa rota. La diferencia entre "hacer conforme una
desviacion" y "esconderla" es que la primera se puede leer.
"""

import config

_CSS = """
body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:46rem;
margin:3rem auto;padding:0 1.5rem;line-height:1.6;color:#1a1a1a}
h1{font-size:1.5rem;border-bottom:1px solid #ddd;padding-bottom:.5rem}
h2{font-size:1.1rem;margin-top:2rem}
code{background:#f4f4f4;padding:.1rem .3rem;border-radius:3px;font-size:.9em}
table{border-collapse:collapse;width:100%;margin:1rem 0}
th,td{border:1px solid #ddd;padding:.5rem;text-align:left;font-size:.9rem}
th{background:#f8f8f8}
.aviso{background:#fff8e1;border-left:4px solid #f0ad4e;padding:.8rem 1rem;margin:1.5rem 0}
"""


def _pagina(titulo, cuerpo):
    return f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{titulo}</title><style>{_CSS}</style></head>
<body><h1>{titulo}</h1>{cuerpo}</body></html>"""


def reglas_del_esquema():
    return _pagina(
        "Reglas de comunidad del esquema CIVICA",
        f"""
<p>Este documento define las reglas bajo las que opera el esquema de Trusted
Lists publicado en <code>{config.BASE_URL}/TL/</code>. Es el destino del campo
<code>SchemeTypeCommunityRules</code> de esas listas.</p>

<div class="aviso">
<strong>Este no es un esquema de la Unión Europea.</strong> Las listas de este
esquema declaran deliberadamente estas reglas propias y <em>no</em>
<code>http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon</code>.
La raíz de confianza de este ecosistema no figura en la lista de listas de la
Comisión Europea, y ningún prestador aquí listado está cualificado en el sentido
jurídico del Reglamento (UE) 910/2014. Declarar <code>EUcommon</code> sería
afirmar lo contrario.</div>

<h2>Qué sí es conforme</h2>
<p>Todo lo técnico: el XML sigue ETSI TS 119 612 v2.1.1 y valida contra su XSD;
la firma es XAdES sobre ECDSA-SHA256; los certificados llevan AIA, CRLDP y
políticas, y su revocación se comprueba por CRL y OCSP reales. Lo único simulado
es la <em>cualificación jurídica</em>.</p>

<h2>Reglas de publicación</h2>
<table>
<tr><th>Regla</th><th>Valor</th></tr>
<tr><td>Periodo de validez de una lista</td><td>{config.HORAS_VALIDEZ} horas</td></tr>
<tr><td>Republicación</td><td>automática al
    {int(config.FRACCION_REPUBLICACION * 100)}% del periodo, haya cambios o no</td></tr>
<tr><td>Número de secuencia</td><td>monótono por esquema, nunca reutilizado</td></tr>
<tr><td>Inmutabilidad</td><td>una publicación es accesible por su número para siempre
    en <code>/TL/&lt;esquema&gt;/&lt;n&gt;.xml</code></td></tr>
<tr><td>Firmante</td><td>hoja emitida por <code>Civica TL Signing CA</code>,
    con AIA hacia <code>{config.BASE_URL}/pki/ca/tl.crt</code></td></tr>
</table>

<h2>Criterio de estado de un servicio</h2>
<p>Un servicio figura como <code>granted</code> mientras su certificado esté
vigente y no revocado. La comprobación de revocación no se delega a la lista:
cada servicio publica sus <code>ServiceSupplyPoints</code> con la CRL y el
endpoint OCSP de la autoridad que lo emitió.</p>
""",
    )


_TIPOS = {
    "WP": ("Proveedor de wallet",
           "http://uri.etsi.org/TrstSvc/Svctype/Provider/Wallet",
           "http://uri.etsi.org/Svc/Svctype/Provider/Wallet",
           "Autoridad que atestigua instancias de wallet (WIA/WUA). "
           "Corresponde al art. 22a.1.a del Reglamento (UE) 2024/1183."),
    "PID": ("Emisor de PID",
            "http://uri.etsi.org/TrstSvc/Svctype/Provider/PID",
            "http://uri.etsi.org/Svc/Svctype/Provider/PID",
            "Autoridad que emite la credencial de identidad de la persona. "
            "Art. 22a.1.b."),
    "RPREG": ("Registrador de partes usuarias",
              "http://uri.etsi.org/TrstSvc/Svctype/CA/RPaccess",
              "http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
              "Autoridad que emite los access certificates de las partes "
              "usuarias y firma sus registration certificates. Art. 22a.1.e."),
    "QEAA": ("Prestador cualificado de QEAA",
             "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q", None,
             "URI del registro de ETSI. Se publica sin modificar."),
    "PUBEAA": ("Organismo del sector público emisor de Pub-EAA",
               "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA", None,
               "URI del registro de ETSI. Se publica sin modificar."),
}


def tipo_de_servicio(slug):
    if slug not in _TIPOS:
        return None
    nombre, canonico, upstream, descripcion = _TIPOS[slug]
    if upstream:
        desviacion = f"""
<div class="aviso">
<strong>Desviación señalada.</strong> Este identificador <em>no</em> figura en el
registro de ETSI. Se publica como <code>{canonico}</code>, que es la forma de los
borradores del CIR y del ARF y la única con alguna probabilidad de que un
validador de terceros la reconozca.
<p>El software de referencia del que parte este despliegue lo traía como
<code>{upstream}</code> — sin el segmento <code>TrstSvc</code> — mientras que en
la misma lista otros identificadores sí lo llevan. Es una errata suya, y se
corrige al publicar sin tocar lo que el operador tenga dado de alta.</p>
</div>"""
    else:
        desviacion = ("<p>Este identificador sí está en el registro de ETSI y se "
                      "publica sin modificar.</p>")

    return _pagina(
        f"Tipo de servicio: {nombre}",
        f"""<p>{descripcion}</p>
<table>
<tr><th>Identificador publicado</th><td><code>{canonico}</code></td></tr>
<tr><th>Lista</th><td><a href="{config.BASE_URL}/TL/{slug}/current.xml">{slug}</a></td></tr>
</table>
{desviacion}
<p><a href="{config.BASE_URL}/trust/schemerules/CIVICA">Reglas de comunidad del
esquema</a></p>""",
    )
