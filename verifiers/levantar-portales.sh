#!/usr/bin/env bash
#
# Levanta (o deja en pie) los portales de ejemplo con su verifier: discoteca, banco, contratación, EPS, notaría,
# aerolínea, inscripción y becas y control de acceso. Lo usa ../up.sh; también se puede lanzar solo.
#
#   ./levantar-portales.sh                 todos
#   ./levantar-portales.sh eps notaria     solo esos
#   ./levantar-portales.sh --build         reconstruye las imágenes de los portales
#
# Cada portal son DOS proyectos de Compose: su verifier (_comun, instancia <nombre>) y la app (<nombre>/compose.yml). El
# verifier va primero. Es idempotente: lo que ya está en pie se queda como está.
#
# Un portal que nunca se creó (sin su .env o sin la instancia del verifier) NO se crea aquí: eso lo hace su levantar.sh la
# primera vez (genera claves, certificado de registro y lo da de alta en el tl-publisher), y no debe ocurrir sin que alguien lo vea.
#
# Dependencias que se resuelven aquí:
#   - la red compartida avance-eudi (la crea ../up.sh);
#   - beca necesita la lista de estado de la UIS (status-list-uis): se levanta SOLA, sin el Keycloak de la UIS, que chocaría
#     con el de la Registraduría;
#   - el PID necesita status-list-reg (stack de la Registraduría): solo se avisa si falta.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

TODOS=(discoteca banco contratacion eps notaria aerolinea beca acceso pensiones cancilleria migracion gym)
BUILD=()
PORTALES=()
for a in "$@"; do
  case "$a" in
    --build) BUILD=(--build) ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) PORTALES+=("$a") ;;
  esac
done
[ ${#PORTALES[@]} -gt 0 ] || PORTALES=("${TODOS[@]}")

# Los portales con datos propios (eps, notaría, aerolínea) corren con el usuario de quien los levantó.
export UID_GID="$(id -u):$(id -g)"

docker network inspect avance-eudi >/dev/null 2>&1 || { echo "Falta la red avance-eudi: levanta antes el ecosistema (../up.sh)."; exit 1; }

en_pie() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]; }

fallos=0
for n in "${PORTALES[@]}"; do
  if [ ! -f "_comun/instancias/$n/.env" ] || [ ! -f "$n/.env" ]; then
    echo "  $n: sin crear. La primera vez: verifiers/$n/levantar.sh"
    continue
  fi
  if [ "$n" = "beca" ] && ! en_pie status-list-uis; then
    ( cd ../eudi-srv-issuer-pid-uis/docker-compose && docker compose -p uis-issuer up -d --no-deps status-list-uis >/dev/null 2>&1 ) \
      && echo "  beca: status-list-uis levantada sola" || echo "  beca: no se pudo levantar status-list-uis (verificar la credencial académica fallará)"
  fi
  if ./_comun/compose.sh "$n" up -d "${BUILD[@]}" >/dev/null 2>&1 \
     && ( cd "$n" && docker compose -p "$n" --env-file .env up -d "${BUILD[@]}" >/dev/null 2>&1 ); then
    echo "  $n: en pie  → /$n/"
  else
    echo "  $n: FALLÓ (revisa: ./_comun/compose.sh $n logs, y docker compose -p $n logs)"
    fallos=$((fallos + 1))
  fi
done

# Instancias que son SOLO verifier (sin portal): el verificador de la cedula digital de la UIS. El emisor de la UIS lo usa
# para el ingreso con PID; sin el, su login dice "El servicio de verificacion de la cedula digital no esta disponible".
# Tambien el verifier de firma (Andes SCD identifica al firmante con su PID por aqui).
if [ ${#PORTALES[@]} -eq ${#TODOS[@]} ]; then
  for solo in uis firma; do
    [ -f "_comun/instancias/$solo/.env" ] || continue
    if ./_comun/compose.sh "$solo" up -d >/dev/null 2>&1; then echo "  $solo (solo verifier): en pie  → /verifier-$solo/"
    else echo "  $solo (solo verifier): FALLO (revisa: ./_comun/compose.sh $solo logs)"; fallos=$((fallos + 1)); fi
  done
fi

en_pie status-list-reg || echo "  Aviso: status-list-reg está abajo (stack de la Registraduría): verificar el PID fallará."
exit $((fallos > 0 ? 1 : 0))
