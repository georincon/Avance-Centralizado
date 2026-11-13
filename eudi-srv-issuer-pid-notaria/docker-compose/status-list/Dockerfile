# Status list del propio repo (../status-list) + el endpoint de lectura que añade el ecosistema.
FROM python:3.14-alpine
WORKDIR /status-list
COPY status-list/ /status-list/
COPY docker-compose/status-list/endpoints/get_status_list.py /tmp/get_status_list.py
RUN cat /tmp/get_status_list.py >> app/status_list_endpoints.py && rm /tmp/get_status_list.py \
 && sed -i 's|"url": "https://issuer.eudiw.dev"|"url": "https://localhost"|' app/static/swagger.json \
 && pip install --no-cache-dir Flask && pip install --no-cache-dir -r app/requirements.txt
EXPOSE 5000
CMD ["flask", "--app", "app", "run", "--host=0.0.0.0"]
