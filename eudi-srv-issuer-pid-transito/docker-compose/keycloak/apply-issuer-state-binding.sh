#!/usr/bin/env bash
# Deja el Keycloak en ejecución (keycloak-tra) con el login SOLO por ticket (issuer_state) para los clientes de wallet:
#  1. El flujo `issuer-state-browser` queda con un único paso obligatorio (el autenticador por ticket): sin formulario
#     de usuario y contraseña de respaldo. La cédula no es un secreto; la identidad la prueba quien crea el ticket.
#  2. Ese flujo se asigna a todos los clientes de wallet (newwallet-app, wallet-dev, eudiw, eudiw-abca).
#  3. Se apaga el auto-registro de usuarios del realm.
# Idempotente. El import del realm no aplica authenticationFlowBindingOverrides, por eso se ejecuta tras cada recreación.
set -euo pipefail
K="docker exec keycloak-tra /opt/keycloak/bin/kcadm.sh"
R=pid-transito-realm
$K config credentials --server http://localhost:8080/idp-tra --realm master --user admin \
  --password "$(docker exec keycloak-tra printenv KC_BOOTSTRAP_ADMIN_PASSWORD)" >/dev/null
J='import json,sys'
FID=$($K get authentication/flows -r $R --fields id,alias | python3 -c "$J;print([f['id'] for f in json.load(sys.stdin) if f['alias']=='issuer-state-browser'][0])")

# 1) un solo paso obligatorio: el autenticador por ticket
$K get authentication/flows/issuer-state-browser/executions -r $R | python3 -c "$J
for e in json.load(sys.stdin):
    if e.get('providerId')=='issuer-state-authenticator': print('KEEP', e['id'], e['requirement'])
    else: print('DROP', e['id'], e.get('flowId',''))" | while read -r acc id extra; do
  if [ "$acc" = DROP ]; then
    $K delete authentication/executions/$id -r $R || true
    [ -n "${extra:-}" ] && $K delete authentication/flows/$extra -r $R 2>/dev/null || true
  elif [ "$extra" != REQUIRED ]; then
    $K update authentication/flows/issuer-state-browser/executions -r $R -b "{\"id\":\"$id\",\"requirement\":\"REQUIRED\"}"
  fi
done

# 2) asignar el flujo a los clientes de wallet
for C in newwallet-app wallet-dev eudiw eudiw-abca; do
  CID=$($K get clients -r $R -q clientId=$C --fields id | python3 -c "$J;print(json.load(sys.stdin)[0]['id'])")
  $K update clients/$CID -r $R -s "authenticationFlowBindingOverrides.browser=$FID"
  echo "$C -> flujo issuer-state-browser ($FID)"
done

# 3) sin auto-registro
$K update realms/$R -s registrationAllowed=false
echo "registrationAllowed=false"
