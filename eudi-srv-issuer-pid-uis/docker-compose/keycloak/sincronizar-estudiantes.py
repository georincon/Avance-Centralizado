#!/usr/bin/env python3
"""Crea/actualiza en Keycloak (uis-issuer-realm) a todos los estudiantes de `datos_estudiantes`.
Idempotente. username = número de identificación (cédula/documento); SIN contraseña (se borra cualquiera que hubiera):
la identidad se prueba con el PID o con un código de activación, nunca con una clave. Usuarios que no son estudiantes
(p. ej. `tneal`) no se tocan.
Uso: python3 sincronizar-estudiantes.py
"""
import json, subprocess
R = "uis-issuer-realm"
KC = ["docker", "exec", "-i", "keycloak-uis", "/opt/keycloak/bin/kcadm.sh"]

def kc(*a, inp=None):
    return subprocess.run(KC + list(a), input=inp, capture_output=True, text=True, check=True).stdout

def sql(q):
    return subprocess.run(["docker","exec","postgres-uis-academic","psql","-U","uis_academic","-d","uis_academic","-At","-F","\t","-c",q],
                          capture_output=True, text=True, check=True).stdout.strip().splitlines()

pw = subprocess.run(["docker","exec","keycloak-uis","printenv","KC_BOOTSTRAP_ADMIN_PASSWORD"],capture_output=True,text=True,check=True).stdout.strip()
kc("config","credentials","--server","http://localhost:8080/idp-uis","--realm","master","--user","admin","--password",pw)

def find(username):
    r = json.loads(kc("get","users","-r",R,"-q",f"username={username}","-q","exact=true"))
    return r[0] if r else None

for row in sql("select numero_identificacion,nombres,apellidos,usuario_autenticacion from datos_estudiantes order by 1"):
    doc, nom, ape, viejo = row.split("\t")
    u = find(doc)
    if u is None and viejo != doc and find(viejo):          # usuario antiguo (jperez...) -> se renombra a la cédula
        u = find(viejo); accion = "renombrado"
    else:
        accion = "actualizado" if u else "creado"
    body = {"username": doc, "enabled": True, "firstName": nom, "lastName": ape}
    if u:
        kc("update", f"users/{u['id']}", "-r", R, "-f", "-", inp=json.dumps({**body, "attributes": u.get("attributes") or {}}))
        uid = u["id"]
    else:
        kc("create", "users", "-r", R, "-f", "-", inp=json.dumps(body)); uid = find(doc)["id"]
    for c in json.loads(kc("get", f"users/{uid}/credentials", "-r", R)):
        kc("delete", f"users/{uid}/credentials/{c['id']}", "-r", R)
    print(f"{doc}: {accion} (sin contraseña)")
