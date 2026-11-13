#!/usr/bin/env bash
#
# Levanta TODO el ecosistema EUDI de Avance de una vez.
#
#   ./up.sh            levanta el ecosistema entero (los 10 stacks, UIS incluido)
#   ./up.sh --sin-uis  levanta solo los 9 stacks del compose raiz (sin el issuer UIS)
#   ./up.sh --sin-portales   no levanta los portales de ejemplo de verifiers/
#   ./up.sh --build    reconstruye las imagenes antes de levantar
#   ./up.sh --help     esta ayuda
#
# Es idempotente: se puede ejecutar tantas veces como haga falta. Si un stack ya
# esta en pie, lo deja como estaba.
#
# QUE SE LEVANTA
#
# El compose raiz reune nueve stacks (identidad, wallet, onboarding, confianza,
# verificador, proxy y panel). El décimo, el issuer de la UIS, vive en su propio
# proyecto: no esta en el compose raiz porque es el que mas recursos consume (su
# Keycloak, sus dos Postgres y su issuer) y el que se para cuando se trabaja con
# el de la Registraduria.
#
# Este script levanta LOS DOS: primero el compose raiz y despues el stack de la
# UIS con su propio compose. Con --sin-uis se queda solo con el compose raiz.
#
# Tambien levanta la Notaria EUDI (eudi-srv-issuer-pid-notaria) y Andes SCD (firma remota),
# cada uno con su propio compose; si nunca se crearon (sin secretos / sin su .env) se avisa.
#
# Despues levanta los portales de ejemplo de verifiers/ (discoteca, banco, contratacion,
# EPS, notaria, aerolinea, inscripcion y becas, control de acceso, pensiones, cancilleria,
# migracion, gym y el verifier de firma), cada uno con su
# verifier: lo hace verifiers/levantar-portales.sh. Un portal que nunca se creo no se
# crea aqui (su levantar.sh lo hace la primera vez). Con --sin-portales se omiten.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# Las redes. No pertenecen a ningun proyecto: `docker compose down` no las
# borra. Cada stack tiene la suya, para que no compartan red (el issuer de la
# Registraduria no tiene por que poder alcanzar el Postgres de la UIS), y la
# compartida `avance-eudi` es por donde el proxy unico llega a todos.
#
# Son externas en los composes: las gestiona este script, no Compose. Asi el
# compose raiz y el de cada stack usan las mismas sin pelearse con las
# etiquetas de proyecto.
REDES=(
  avance-eudi                  # la compartida
  newwallet_default            # app-new-wallet
  nw-nginx_default             # srv-nw-nginx
  onboarding_default           # srv-onboarding
  registraduria_default        # eudi-srv-issuer-pid-registraduria
  transito_default             # eudi-srv-issuer-pid-transito
  spartans_default             # eudi-srv-wallet-provider
  srv-tl-publisher_default      # srv-tl-publisher
)
REDES_UIS=(
  uis-issuer_default           # eudi-srv-issuer-pid-uis
)

# Los argumentos. Los que son de este script se separa de los de Compose.
CON_UIS=1
CON_PORTALES=1
BUILD=()
AYUDA=0
ARGUMENTOS_COMPOSE=()

while [ $# -gt 0 ]; do
  case "$1" in
    --sin-uis|--no-uis) CON_UIS=0 ;;
    --sin-portales)      CON_PORTALES=0 ;;
    --build)             BUILD=(--build) ;;
    -h|--help)           AYUDA=1 ;;
    *)                   ARGUMENTOS_COMPOSE+=("$1") ;;
  esac
  shift
done

if [ "$AYUDA" -eq 1 ]; then
  sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 0
fi

crear_red() {
  local red="$1"
  if ! docker network inspect "$red" >/dev/null 2>&1; then
    echo ">> Creando la red '$red'..."
    docker network create "$red" >/dev/null
  fi
}

for red in "${REDES[@]}"; do crear_red "$red"; done
if [ "$CON_UIS" -eq 1 ]; then
  for red in "${REDES_UIS[@]}"; do crear_red "$red"; done
fi

# COORDINACION DE LOS TRES EMISORES: solo un Keycloak (Registraduria, Transito o UIS) puede estar en pie a la vez (comparten
# dominio y cookies). Se conserva el que ya estuviera en pie; si no hay ninguno, el de la Registraduria. Al final de este script
# se bajan los otros dos (solo sus Keycloak); el resto de servicios de cada emisor se queda arriba esperando su turno.
KEYCLOAKS=(keycloak-reg keycloak-tra keycloak-uis)
KC_ELEGIDO=keycloak-reg
for kc in "${KEYCLOAKS[@]}"; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$kc" 2>/dev/null)" = "true" ]; then KC_ELEGIDO="$kc"; break; fi
done

# El ecosistema. El compose raiz incluye cada stack con su propio fichero.
echo ">> Levantando el ecosistema..."
docker compose up -d "${BUILD[@]}" "${ARGUMENTOS_COMPOSE[@]}"

# Keycloak de cada emisor (Registraduria, Transito y UIS): los datos (realm, usuarios) viven en su volumen y sobreviven.
# Aun asi se reaplica, de forma idempotente, el login SOLO por ticket (flujo issuer-state-browser en los clientes de wallet,
# sin auto-registro) y se sincronizan los titulares de la base de cada emisor (datos, SIN contrasena), por si el volumen se
# recreo o hay titulares nuevos. Solo se hace con los que estan en pie.
# Formato: contenedor | carpeta del emisor | script de sincronizacion
sincronizar_keycloak() {
  local contenedor="$1" carpeta="$2" sync="$3"
  docker ps --format '{{.Names}}' | grep -qx "$contenedor" || return 0
  echo ">> Esperando a $contenedor..."
  for _ in $(seq 1 60); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$contenedor" 2>/dev/null)" = "healthy" ] && break
    sleep 5
  done
  "$carpeta/docker-compose/keycloak/apply-issuer-state-binding.sh" \
    || echo "   ($contenedor: no se pudo asignar el flujo de login por ticket)"
  python3 "$carpeta/docker-compose/keycloak/$sync" \
    || echo "   ($contenedor: no se pudieron sincronizar los titulares)"
}
sincronizar_keycloak keycloak-reg eudi-srv-issuer-pid-registraduria sincronizar-ciudadanos.py
sincronizar_keycloak keycloak-tra eudi-srv-issuer-pid-transito sincronizar-licencias.py

# El stack de la UIS, con su propio compose: no esta en el compose raiz.
if [ "$CON_UIS" -eq 1 ]; then
  echo ">> Levantando el issuer de la UIS..."
  docker compose -f eudi-srv-issuer-pid-uis/docker-compose/docker-compose.yaml \
    up -d "${BUILD[@]}" "${ARGUMENTOS_COMPOSE[@]}"
fi

if [ "$CON_UIS" -eq 1 ]; then
  sincronizar_keycloak keycloak-uis eudi-srv-issuer-pid-uis sincronizar-estudiantes.py
  # El verificador de la cedula digital de la UIS: el login con PID del emisor lo necesita siempre.
  echo ">> Levantando verifier-uis..."
  verifiers/_comun/compose.sh uis up -d >/dev/null 2>&1 || echo "   (verifier-uis no subio: ./verifiers/_comun/compose.sh uis logs)"
fi

# Ya sincronizados, queda un solo Keycloak de emisor en pie (el elegido); los otros esperan su turno ("Activar consola").
for kc in "${KEYCLOAKS[@]}"; do
  if [ "$kc" != "$KC_ELEGIDO" ] && [ "$(docker inspect -f '{{.State.Running}}' "$kc" 2>/dev/null)" = "true" ]; then
    docker stop "$kc" >/dev/null && echo ">> $kc detenido (solo $KC_ELEGIDO queda en pie; los demas servicios de ese emisor siguen arriba)"
  fi
done

# Servicios con compose propio que no estan en el compose raiz: la Notaria EUDI (pila Python, emisor de la fe de vida
# en /notaria-issuer) y Andes SCD (prestador de firma remota). Sin esto, tras un reinicio quedaban caidos (502 en el borde).
# Solo se levantan si ya se crearon (secretos / .env): su primera creacion es manual (ver su README o PLAN.md).
echo ">> Levantando la Notaria EUDI y Andes SCD..."
if [ -f eudi-srv-issuer-pid-notaria/secretos/ds.key ]; then
  docker compose -f eudi-srv-issuer-pid-notaria/docker-compose/docker-compose.yaml \
    up -d "${BUILD[@]}" >/dev/null 2>&1 && echo "   notaria-issuer: en pie" \
    || echo "   (notaria-issuer no subio: docker compose -f eudi-srv-issuer-pid-notaria/docker-compose/docker-compose.yaml logs)"
else
  echo "   notaria-issuer: sin crear (eudi-srv-issuer-pid-notaria/PLAN.md)"
fi
if [ -f srv-andes-scd/.env ] && [ -d srv-tl-publisher/pki/subca/qtsp/private ]; then
  UID_GID="$(id -u):$(id -g)" docker compose -p andes-scd --env-file srv-andes-scd/.env -f srv-andes-scd/compose.yml \
    up -d "${BUILD[@]}" >/dev/null 2>&1 && echo "   andes-scd: en pie" \
    || echo "   (andes-scd no subio: srv-andes-scd/levantar.sh)"
else
  echo "   andes-scd: sin crear (srv-andes-scd/levantar.sh)"
fi

# Los portales de ejemplo, cada uno con su verifier. Van despues: necesitan la red
# compartida y, para verificar, los emisores y las listas de estado.
if [ "$CON_PORTALES" -eq 1 ]; then
  echo ">> Levantando los portales de ejemplo..."
  verifiers/levantar-portales.sh "${BUILD[@]}" || echo "   (algun portal no subio: ver arriba)"
fi

# Que se vea.
echo
docker compose ps --format "table {{.Service}}\t{{.Status}}"
if [ "$CON_UIS" -eq 1 ]; then
  echo
  docker compose -f eudi-srv-issuer-pid-uis/docker-compose/docker-compose.yaml \
    ps --format "table {{.Service}}\t{{.Status}}"
fi

echo
echo "Ecosistema disponible en https://civica-desarrollo.avance.org.co"
echo "El panel de control, en la raiz del dominio."
if [ "$CON_PORTALES" -eq 1 ]; then
  echo "Portales: /discoteca /banco /contratacion /eps /notaria /aerolinea /beca /acceso /pensiones /cancilleria /migracion /gym"
fi
if [ "$CON_UIS" -eq 0 ]; then
  echo "El issuer de la UIS queda fuera; para levantarlo:"
  echo "  docker compose -f eudi-srv-issuer-pid-uis/docker-compose/docker-compose.yaml up -d"
fi
