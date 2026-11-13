#!/usr/bin/env python3
"""Migra/ajusta en Keycloak (pid-registraduria-realm) a todos los ciudadanos de la tabla `cedulas`.
Idempotente: crea el usuario si falta, fija datos y deja al usuario SIN contraseña.
Uso: python3 sincronizar-ciudadanos.py
"""
import json, subprocess, tempfile, os
R = "pid-registraduria-realm"
KC = ["docker", "exec", "-i", "keycloak-reg", "/opt/keycloak/bin/kcadm.sh"]

def kc(*a, inp=None):
    return subprocess.run(KC + list(a), input=inp, capture_output=True, text=True, check=True).stdout

def sql(q):
    return subprocess.run(["docker","exec","postgres-reg","psql","-U","pid_issuer","-d","pid_issuer","-At","-F","\t","-c",q],
                          capture_output=True, text=True, check=True).stdout.strip().splitlines()

pw = subprocess.run(["docker","exec","keycloak-reg","printenv","KC_BOOTSTRAP_ADMIN_PASSWORD"],capture_output=True,text=True,check=True).stdout.strip()
kc("config","credentials","--server","http://localhost:8080/idp-reg","--realm","master","--user","admin","--password",pw)

for row in sql("select documento_numero,nombres,apellidos,fecha_nacimiento,lugar_nacimiento_ciudad,coalesce(lugar_nacimiento_departamento,''),nacionalidad,sexo from cedulas order by 1"):
    doc, nom, ape, nac, ciudad, depto, nat, sexo = row.split("\t")
    g = "1" if sexo == "M" else "2"
    attrs = {"birthdate":[nac],"nationality":[nat.strip()],"birth_given_name":[nom],"birth_family_name":[ape],
             "birth_place":[ciudad],"birth_city":[ciudad],"birth_country":["Colombia"],"gender":[g],"gender_as_string":[g]}
    if depto: attrs["birth_state"] = [depto]
    found = json.loads(kc("get","users","-r",R,"-q",f"username={doc}","-q","exact=true"))
    if found:
        uid = found[0]["id"]; old = found[0].get("attributes") or {}
        body = {"username":doc,"enabled":True,"firstName":nom,"lastName":ape,"attributes":{**old,**attrs}}
        kc("update",f"users/{uid}","-r",R,"-f","-",inp=json.dumps(body)); accion="actualizado"
    else:
        body = {"username":doc,"enabled":True,"firstName":nom,"lastName":ape,"attributes":attrs}
        kc("create","users","-r",R,"-f","-",inp=json.dumps(body))
        uid = json.loads(kc("get","users","-r",R,"-q",f"username={doc}","-q","exact=true"))[0]["id"]; accion="creado"
    # SIN contraseña: la cédula no es un secreto. La identidad se prueba con el ticket del onboarding (issuer_state).
    for c in json.loads(kc("get",f"users/{uid}/credentials","-r",R)):
        kc("delete",f"users/{uid}/credentials/{c['id']}","-r",R)
    print(f"{doc}: {accion} (sin contraseña)")
