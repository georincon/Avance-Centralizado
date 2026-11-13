#!/usr/bin/env bash
# Deja el Keycloak de la UIS (keycloak-uis) con el login SOLO por ticket (issuer_state) para los clientes de wallet:
#  1. Crea (si falta) el flujo `issuer-state-browser` con un único paso obligatorio: el autenticador por ticket.
#     Sin formulario de usuario y contraseña: la cédula no es un secreto; la identidad la prueba quien crea el ticket
#     (presentación del PID o código de activación del registro de la UIS).
#  2. Asigna ese flujo a los clientes de wallet (eudiw, eudiw-abca, wallet-dev).
#  3. Apaga el auto-registro de usuarios del realm.
# Idempotente. El import del realm no aplica authenticationFlowBindingOverrides: se ejecuta tras cada recreación.
set -euo pipefail
K="docker exec keycloak-uis /opt/keycloak/bin/kcadm.sh"
R=uis-issuer-realm
$K config credentials --server http://localhost:8080/idp-uis --realm master --user admin \
  --password "$(docker exec keycloak-uis printenv KC_BOOTSTRAP_ADMIN_PASSWORD)" >/dev/null
J='import json,sys'
if ! $K get authentication/flows -r $R --fields alias | grep -q '"issuer-state-browser"'; then
  $K create authentication/flows -r $R -s alias=issuer-state-browser -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s 'description=Login SOLO con issuer_state (OpenID4VCI): sin ticket válido se niega el acceso'
  $K create authentication/flows/issuer-state-browser/executions/execution -r $R -s provider=issuer-state-authenticator
fi
FID=$($K get authentication/flows -r $R --fields id,alias | python3 -c "$J;print([f['id'] for f in json.load(sys.stdin) if f['alias']=='issuer-state-browser'][0])")
$K get authentication/flows/issuer-state-browser/executions -r $R | python3 -c "$J
for e in json.load(sys.stdin):
    if e.get('providerId')=='issuer-state-authenticator' and e['requirement']!='REQUIRED': print(e['id'])" | while read -r id; do
  $K update authentication/flows/issuer-state-browser/executions -r $R -b "{\"id\":\"$id\",\"requirement\":\"REQUIRED\"}"
done
for C in eudiw eudiw-abca wallet-dev; do
  CID=$($K get clients -r $R -q clientId=$C --fields id | python3 -c "$J;print(json.load(sys.stdin)[0]['id'])")
  $K update clients/$CID -r $R -s "authenticationFlowBindingOverrides.browser=$FID"
  echo "$C -> flujo issuer-state-browser ($FID)"
done
$K update realms/$R -s registrationAllowed=false
echo "registrationAllowed=false"

# 4) El perfil de usuario heredado exige `nationality`: la UIS no la guarda, así que no es obligatoria para sus estudiantes.
$K get users/profile -r $R | python3 -c "$J
d=json.load(sys.stdin)
for a in d['attributes']:
    if a['name']=='nationality': a.pop('required',None)
print(json.dumps(d))" | docker exec -i keycloak-uis /opt/keycloak/bin/kcadm.sh update users/profile -r $R -f - >/dev/null
echo "nationality ya no es obligatoria"
