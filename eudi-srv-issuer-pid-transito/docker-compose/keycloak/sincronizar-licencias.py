#!/usr/bin/env python3
"""Crea/actualiza en Keycloak (pid-transito-realm) a los titulares de la tabla `licencias`.
Idempotente. username = número de cédula; SIN contraseña (se borra cualquiera que hubiera): la identidad se prueba con un ticket
(onboarding aprobado o verificación de datos y selfie en /transito/solicitar), nunca con una clave.
Uso: python3 sincronizar-licencias.py
"""
import json, subprocess
R = "pid-transito-realm"
KC = ["docker", "exec", "-i", "keycloak-tra", "/opt/keycloak/bin/kcadm.sh"]

def kc(*a, inp=None):
    return subprocess.run(KC + list(a), input=inp, capture_output=True, text=True, check=True).stdout

def sql(q):
    u = subprocess.run(["docker","exec","postgres-tra","printenv","POSTGRES_USER"],capture_output=True,text=True,check=True).stdout.strip()
    d = subprocess.run(["docker","exec","postgres-tra","printenv","POSTGRES_DB"],capture_output=True,text=True,check=True).stdout.strip()
    return subprocess.run(["docker","exec","postgres-tra","psql","-U",u,"-d",d,"-At","-F","\t","-c",q],capture_output=True,text=True,check=True).stdout.strip().splitlines()

pw = subprocess.run(["docker","exec","keycloak-tra","printenv","KC_BOOTSTRAP_ADMIN_PASSWORD"],capture_output=True,text=True,check=True).stdout.strip()
kc("config","credentials","--server","http://localhost:8080/idp-tra","--realm","master","--user","admin","--password",pw)

def find(username):
    r = json.loads(kc("get","users","-r",R,"-q",f"username={username}","-q","exact=true"))
    return r[0] if r else None

for row in sql("select documento_numero,nombres,apellidos,nacionalidad from licencias order by 1"):
    doc, nom, ape, nat = row.split("\t")
    body = {"username": doc, "enabled": True, "firstName": nom, "lastName": ape,
            "attributes": {"nationality": [nat.strip()]}}
    u = find(doc)
    if u:
        kc("update", f"users/{u['id']}", "-r", R, "-f", "-", inp=json.dumps({**body, "attributes": {**(u.get("attributes") or {}), **body["attributes"]}}))
        uid, accion = u["id"], "actualizado"
    else:
        kc("create", "users", "-r", R, "-f", "-", inp=json.dumps(body)); uid, accion = find(doc)["id"], "creado"
    for c in json.loads(kc("get", f"users/{uid}/credentials", "-r", R)):
        kc("delete", f"users/{uid}/credentials/{c['id']}", "-r", R)
    print(f"{doc}: {accion} (sin contraseña)")
