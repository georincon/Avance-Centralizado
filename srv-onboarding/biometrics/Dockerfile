FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
 # rapidocr arrastra opencv-python (con GUI, exige libxcb/libGL): se deja solo la variante headless.
 && pip uninstall -y opencv-python \
 && pip install --no-cache-dir --force-reinstall --no-deps opencv-python-headless
COPY download_models.sh app.py ./
# Modelos ONNX (Apache 2.0) descargados en el build: funciona desde un clon limpio.
RUN chmod +x download_models.sh && ./download_models.sh && chown -R app /app
USER app
EXPOSE 8100
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8100"]
