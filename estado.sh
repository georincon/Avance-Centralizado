#!/usr/bin/env bash
#
# Que hay en pie ahora mismo: los stacks del compose raiz y el de la UIS.
#
#   ./estado.sh
#
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

BOLD=$'\033[1m'; FIN=$'\033[0m'

mostrar() {
  local titulo="$1" compose="$2"
  local hay=0
  while IFS=$'\t' read -r servicio estado; do
    [ -z "${servicio:-}" ] && continue
    if [ $hay -eq 0 ]; then
      printf "\n%s%s%s\n" "$BOLD" "$titulo" "$FIN"
      hay=1
    fi
    printf "  %-26s %s\n" "$servicio" "$estado"
  done < <(docker compose -f "$compose" ps --format '{{.Service}}\t{{.Status}}' 2>/dev/null)
  # Con `set -e`, un `[ ... ] && ...` que no se cumple devuelve 1 y ABORTA el
  # script: la funcion tiene que terminar con 0 o aqui se cortaba todo.
  if [ $hay -eq 0 ]; then
    printf "\n%s%s%s\n  (nada en pie)\n" "$BOLD" "$titulo" "$FIN"
  fi
  return 0
}

printf "\n%sEcosistema EUDI de Avance%s  ·  %s\n" "$BOLD" "$FIN" "https://civica-desarrollo.avance.org.co"

mostrar "Ecosistema (compose raiz)" "docker-compose.yaml"
mostrar "Issuer UIS (compose propio)" "eudi-srv-issuer-pid-uis/docker-compose/docker-compose.yaml"

# Los portales de ejemplo de verifiers/: la app y los tres contenedores de su verifier.
printf "\n%sPortales de ejemplo (verifiers/)%s\n" "$BOLD" "$FIN"
for n in discoteca banco contratacion eps notaria aerolinea beca acceso pensiones cancilleria migracion gym; do
  app="$(docker inspect -f '{{.State.Running}}' "$n-$n-1" 2>/dev/null || echo false)"
  ver="$(docker ps --filter "name=^verifier-$n-" --filter status=running -q | wc -l)"
  if [ "$app" = "true" ] && [ "$ver" -ge 3 ]; then marca="en pie"
  elif [ "$app" = "true" ] || [ "$ver" -gt 0 ]; then marca="INCOMPLETO (app: $app, verifier: $ver/3)"
  else marca="(apagado)"; fi
  printf "  %-14s %s\n" "$n" "$marca"
done

# Servicios con compose propio (no estan en el compose raiz): se muestran los contenedores y su estado.
printf "\n%sNotaria EUDI, Andes SCD y verifier de firma%s\n" "$BOLD" "$FIN"
for c in notaria-issuer-frontend notaria-issuer-backend notaria-issuer-oidc status-list-notaria haproxy-notaria postgres-notaria-issuer \
         andes-scd verifier-firma-haproxy-1 verifier-firma-verifier-api-1 verifier-firma-verifier-web-1; do
  est="$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo "(no existe)")"
  [ "$est" = "running" ] && est="en pie"
  printf "  %-34s %s\n" "$c" "$est"
done

# La base de datos propia de cada portal (historial de emisiones y verificaciones): un Postgres por proyecto.
printf "\n%sBases de datos de los portales de verificación%s\n" "$BOLD" "$FIN"
for par in acceso:5461 aerolinea:5462 banco:5463 beca:5464 cancilleria:5465 contratacion:5466 discoteca:5467 eps:5468 gym:5469 migracion:5470 notaria:5471 pensiones:5472; do
  n="${par%%:*}"; pt="${par##*:}"
  est="$(docker inspect -f '{{.State.Health.Status}}' "postgres-verif-$n" 2>/dev/null || echo "(no existe)")"
  [ "$est" = "healthy" ] && est="en pie"
  printf "  %-14s %-10s 127.0.0.1:%s  verif_%s\n" "$n" "$est" "$pt" "$n"
done

printf "\n%sPuertos publicados en el host%s\n" "$BOLD" "$FIN"
docker ps --format '  {{.Names}}: {{.Ports}}' | grep -E '0\.0\.0\.0|127\.0\.0\.1' || echo "  (ninguno)"

printf "\n%sComprobacion de salud por el dominio%s\n" "$BOLD" "$FIN"
for ruta in "/" \
            "/wallet-provider/.well-known/oauth-protected-resource" \
            "/pid-registraduria/.well-known/openid-credential-issuer" \
            "/uis-issuer/.well-known/openid-credential-issuer" \
            "/pid-transito/.well-known/openid-credential-issuer" \
            "/trust-validator/actuator/health" \
            "/tl-publisher/tl-api/v1/lists" \
            "/discoteca/" "/banco/" "/contratacion/" "/eps/" "/notaria/" "/aerolinea/" "/beca/" "/acceso/" "/pensiones/" "/cancilleria/" "/migracion/" "/gym/" \
            "/notaria-issuer/api/.well-known/openid-credential-issuer" "/andes-scd/health"; do
  codigo="$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10 "https://civica-desarrollo.avance.org.co$ruta")"
  case "$codigo" in
    200) marca="ok " ;;
    502) marca="(apagado)" ;;
    *)   marca="??" ;;
  esac
  printf "  %-3s %-12s %s\n" "$codigo" "$marca" "$ruta"
done
printf "\n"
