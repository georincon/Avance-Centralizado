# Servicio de publicación de Trusted Lists.
#
# El TLP es un EDITOR: genera el XML de la lista y lo devuelve como descarga del
# navegador. No lo persiste, no lo versiona y no lo sirve — su botón «Publish»
# es literalmente `href="{}"`. Por eso /TL/... devolvía 404 y seguiría
# devolviéndolo por mucho que un operador generase la lista.
#
# Este servicio es el publicador que faltaba: lee la base de datos del TLP,
# genera el XML conforme a ETSI TS 119 612, lo valida contra el XSD, lo firma
# con XAdES, le asigna un número de secuencia inmutable y lo sirve.
FROM python:3.12-slim-bookworm

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py ./
COPY *.xsd ./
# La interfaz de administracion (una SPA de un solo fichero) y el esquema de sus
# tablas van DENTRO de la imagen: el publicador tiene que ser autocontenido.
COPY static/ ./static/
COPY db/admin-schema.sql ./db/admin-schema.sql

# No corre como root: monta la clave privada con la que firma las listas.
RUN useradd --system --uid 10002 tlpub \
 && mkdir -p /var/lib/tl-publisher \
 && chown 10002 /var/lib/tl-publisher
USER 10002

ENV PYTHONUNBUFFERED=1
EXPOSE 8080
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080", "--log-level", "info"]
