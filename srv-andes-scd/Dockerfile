# Simulador de un prestador de servicios de firma remota (estilo Andes SCD): API CSC v2, servidor
# OAuth2, emision de certificados de firma con la sub-CA QTSP del ecosistema y sello de tiempo RFC 3161.
FROM python:3.12-slim-bookworm

# `openssl ts` firma las respuestas del sello de tiempo (RFC 3161).
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# No corre como root: monta la clave privada de la sub-CA QTSP. El uid lo fija el compose.
ENV PYTHONUNBUFFERED=1
EXPOSE 80
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "80", "--log-level", "info", "--proxy-headers", "--forwarded-allow-ips", "*"]
