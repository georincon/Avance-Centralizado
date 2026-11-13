# Servicio de publicación de la PKI.
#
# Sirve lo que los certificados prometen en sus extensiones AIA y CRLDP, y
# firma las respuestas OCSP. Sin él esas URLs son promesas rotas: un validador
# que intente comprobar una revocación recibe un 404 y, si es estricto,
# rechaza el certificado.
FROM python:3.12-slim-bookworm

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py emision.py .

# No corre como root: monta claves privadas de sub-CA.
RUN useradd --system --uid 10001 pki
USER 10001

ENV PYTHONUNBUFFERED=1
EXPOSE 8080
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080", "--log-level", "info"]
