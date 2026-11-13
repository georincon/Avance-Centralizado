#!/usr/bin/env bash
#
# Siembra y publica las Trusted Lists del ecosistema.
#
# POR QUÉ EXISTE
#
# El TLP se alimenta desde su interfaz web, y para entrar hay que autenticarse
# presentando un PID en `mso_mdoc` desde la wallet. O sea: sin haber completado
# toda la cadena de emisión no hay Trusted List, y sin Trusted List no hay
# confianza que validar. Un huevo y una gallina.
#
# Este script lo rompe dando de alta directamente lo que YA existe de verdad en
# el despliegue: las sub-CAs de la PKI. No inventa datos de prueba — lee
# `pki/publico/ca/*.crt`, que son los certificados con los que este ecosistema
# firma. El TLP sigue siendo el editor: un operador puede entrar después y
# modificar cualquier entrada desde su interfaz.
#
# USO
#
#   ./publicar-listas.sh              # siembra si hace falta y publica
#   ./publicar-listas.sh --solo-publicar
#   ./publicar-listas.sh --resembrar  # BORRA el estado y vuelve a sembrar
#
# OJO con --resembrar: borra `published_lists`, así que los números de secuencia
# vuelven a empezar en 1. Las listas ya servidas quedan huérfanas. En un
# despliegue de verdad eso no se hace: un consumidor que vio la secuencia 9 y
# luego ve la 1 tiene motivos para pensar que le están sirviendo algo viejo.

set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE_DIR="$PWD/docker-compose"
cd "$COMPOSE_DIR"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso() { printf '    %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

SEMBRAR=1; PUBLICAR=1; REHACER=""
case "${1:-}" in
    "")               ;;
    --solo-publicar)  SEMBRAR=0 ;;
    --resembrar)      REHACER="--rehacer" ;;
    *)                fail "opción desconocida: $1" ;;
esac

docker compose ps --status running --format '{{.Service}}' | grep -qx tl-publisher \
    || fail "el servicio tl-publisher no está corriendo.
       docker compose up -d tl-publisher"

# ── 1. Sembrar ───────────────────────────────────────────────────────────────
if [ "$SEMBRAR" = "1" ]; then
    info "Sembrando los anclas de confianza desde la PKI"
    docker compose exec -T tl-publisher python sembrar.py $REHACER
fi

# ── 2. Publicar ──────────────────────────────────────────────────────────────
info "Publicando"
docker compose exec -T tl-publisher python - <<'PY'
import bd, publicacion
with bd.conexion() as con:
    for e in bd.esquemas(con):
        try:
            r = publicacion.publicar(con, e, motivo="publicacion manual")
            print("    %-7s secuencia %-3s %s servicio(s)  %s bytes"
                  % (r["scheme"], r["sequence_number"], r["services"], r["bytes"]))
        except Exception as ex:
            con.rollback()
            print("    %-7s FALLO: %s: %s" % (e["scheme_slug"], type(ex).__name__, ex))
            raise SystemExit(1)
PY

# ── 3. Comprobar que de verdad se sirven ─────────────────────────────────────
DOMINIO="$( sed -n 's/^PUBLIC_DOMAIN=//p' .env | head -1)"
echo
info "Verificación sobre https://$DOMINIO"
for esquema in WP PID QEAA PUBEAA RPREG; do
    codigo="$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMINIO/TL/$esquema/current.xml" || echo 000)"
    [ "$codigo" = "200" ] || fail "/TL/$esquema/current.xml devolvió $codigo"
    paso "/TL/$esquema/current.xml  200"
done
codigo="$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMINIO/LOTL/current.xml" || echo 000)"
[ "$codigo" = "200" ] || fail "/LOTL/current.xml devolvió $codigo"
paso "/LOTL/current.xml         200"

echo
paso "Listado con detalle:  curl -s https://$DOMINIO/tl-api/v1/lists | jq"
