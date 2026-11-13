#!/usr/bin/env bash
#
# Deja el ecosistema en cero para repetir pruebas: borra el HISTORIAL (lo que se generó al emitir y verificar) y conserva
# la información base (esquemas, catálogos, ciudadanos de la Registraduría, licencias, estudiantes, clientes/socios/afiliados/
# trámites de los portales, onboarding, listas de confianza y claves).
#
#   ./limpiar-pruebas.sh              simulación: solo cuenta lo que borraría
#   ./limpiar-pruebas.sh --aplicar    borra (hace antes una copia en respaldos/limpieza-<fecha>/)
#   ./limpiar-pruebas.sh --aplicar --sin-respaldo
#
# Qué limpia:
#   Auditoría (srv-auditoria)   wallets, usuarios, sesiones, credenciales, actividad de wallets, emisores y verificadores.
#                               Conserva los catálogos (tipo_actividad, tipo_evento_servicio).
#   Emisores                    Registraduría y Tránsito (issued_credential), Notaría EUDI (órdenes,
#                               eventos, estados de claves y de la atestación de la wallet). Conserva cédulas, licencias,
#                               estudiantes, administradores y la secuencia notarial.
#   Wallet Provider             challenges (temporales).
#   Portales (verifiers/*)      la tabla `evento` de la base de cada portal (postgres-verif-<portal>), el respaldo historial.jsonl, y en banco/gym/eps las emisiones (y accesos del gym; viven en la tabla `almacen` de cada base), con su
#                               contador de índices intacto; en aerolínea/notaría/cancillería el contador `entregas` de
#                               cada trámite. Conserva clientes, socios, afiliados, médicos y trámites.
# Lo que NO toca: academic_credential de la UIS (por decisión expresa), onboarding, claves y PKI, listas de confianza, status lists, Keycloak, imágenes/volúmenes de Docker.
# La wallet del teléfono no se limpia desde aquí: Ajustes → Apps → wallet → Almacenamiento → Borrar datos,
# o con el teléfono por USB:  adb shell pm clear co.org.newwallet.app.dev.auditoria
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

APLICAR=0; RESPALDO=1
for a in "$@"; do case "$a" in --aplicar) APLICAR=1 ;; --sin-respaldo) RESPALDO=0 ;; -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; *) echo "Argumento desconocido: $a"; exit 1 ;; esac; done

PORTALES=(acceso aerolinea banco beca cancilleria contratacion discoteca eps gym migracion notaria pensiones)
# contenedor | usuario | base | tablas a vaciar (en una sola sentencia, con REINICIO de secuencias)
BASES=(
  "auditoria-db|auditoria|auditoria|auditoria.actividad_servicio_elemento, auditoria.actividad_servicio, auditoria.servicio, auditoria.actividad_elemento, auditoria.actividad, auditoria.consulta_dashboard, auditoria.sesion, auditoria.credencial, auditoria.dispositivo, auditoria.medio_contacto, auditoria.wallet, auditoria.usuario, auditoria.contraparte"
  "postgres-reg|pid_issuer|pid_issuer|issued_credential, revocation_notice"
  "postgres-tra|transito_issuer|transito_issuer|issued_credential, revocation_notice"
  "postgres-notaria-issuer|notaria_issuer|notaria_issuer|notaria_orden_evento, notaria_orden_emision, issued_key_status, ka_key_storage_status, wia_client_status"
  "postgres-spartans|spartans|spartans|challenges"
  "postgres-verif-acceso|verif_acceso|verif_acceso|evento"
  "postgres-verif-aerolinea|verif_aerolinea|verif_aerolinea|evento"
  "postgres-verif-banco|verif_banco|verif_banco|evento"
  "postgres-verif-beca|verif_beca|verif_beca|evento"
  "postgres-verif-cancilleria|verif_cancilleria|verif_cancilleria|evento"
  "postgres-verif-contratacion|verif_contratacion|verif_contratacion|evento"
  "postgres-verif-discoteca|verif_discoteca|verif_discoteca|evento"
  "postgres-verif-eps|verif_eps|verif_eps|evento"
  "postgres-verif-gym|verif_gym|verif_gym|evento"
  "postgres-verif-migracion|verif_migracion|verif_migracion|evento"
  "postgres-verif-notaria|verif_notaria|verif_notaria|evento"
  "postgres-verif-pensiones|verif_pensiones|verif_pensiones|evento"
)

psql_() { docker exec -i "$1" psql -U "$2" -d "$3" -v ON_ERROR_STOP=1 "${@:4}"; }

echo "== Bases de datos =="
for fila in "${BASES[@]}"; do
  IFS='|' read -r c u d tablas <<<"$fila"
  docker ps --format '{{.Names}}' | grep -qx "$c" || { echo "  $c: apagado, se omite"; continue; }
  resumen=""
  IFS=',' read -ra ts <<<"$tablas"
  for t in "${ts[@]}"; do t="$(echo "$t" | xargs)"; n="$(psql_ "$c" "$u" "$d" -At -c "select count(*) from $t")"; resumen+="$t=$n  "; done
  echo "  $c/$d: $resumen"
done

echo "== Portales =="
# Los datos de los portales viven en SU base (tabla `almacen`, un documento por antiguo JSON).
cuenta() { docker exec "postgres-verif-$1" psql -U "verif_$1" -d "verif_$1" -At -c "$2" 2>/dev/null || echo '-'; }
echo "  banco emisiones: $(cuenta banco "select jsonb_array_length(contenido->'emisiones') from almacen where nombre='emisiones'")" \
     "| gym emisiones/accesos: $(cuenta gym "select jsonb_array_length(contenido->'emisiones')||'/'||jsonb_array_length(contenido->'accesos') from almacen where nombre='socios'")" \
     "| eps emisiones: $(cuenta eps "select jsonb_array_length(contenido->'emisiones') from almacen where nombre='afiliados'")"
for p in "${PORTALES[@]}"; do
  h="verifiers/$p/datos/historial.jsonl"; [ -s "$h" ] && echo "  $p: historial.jsonl con $(wc -l <"$h") registros pendientes de importar"
done

[ "$APLICAR" -eq 1 ] || { echo; echo "Simulación: no se borró nada. Para aplicar:  ./limpiar-pruebas.sh --aplicar"; exit 0; }

if [ "$RESPALDO" -eq 1 ]; then
  D="respaldos/limpieza-$(date +%F-%H%M%S)"; mkdir -p "$D/portales"
  echo "== Respaldo en $D =="
  for fila in "${BASES[@]}"; do
    IFS='|' read -r c u d _ <<<"$fila"
    docker ps --format '{{.Names}}' | grep -qx "$c" && docker exec "$c" pg_dump -U "$u" -d "$d" >"$D/$c.sql" && echo "  $c.sql"
  done
  for p in "${PORTALES[@]}"; do mkdir -p "$D/portales/$p"; cp -a "verifiers/$p/datos/." "$D/portales/$p/" 2>/dev/null || true; done
fi

echo "== Aplicando =="
# Los portales se detienen mientras se editan sus archivos (guardan estado en memoria y lo reescribirían).
for p in "${PORTALES[@]}"; do docker stop "$p-$p-1" >/dev/null 2>&1 || true; done

for fila in "${BASES[@]}"; do
  IFS='|' read -r c u d tablas <<<"$fila"
  docker ps --format '{{.Names}}' | grep -qx "$c" || continue
  psql_ "$c" "$u" "$d" -q -c "BEGIN; TRUNCATE $tablas RESTART IDENTITY; COMMIT;" && echo "  $c: vaciado"
done

sql_() { docker exec -i "postgres-verif-$1" psql -U "verif_$1" -d "verif_$1" -q -v ON_ERROR_STOP=1 -c "$2" && echo "  $1: $3"; }
sql_ banco "UPDATE almacen SET contenido = jsonb_set(contenido,'{emisiones}','[]') WHERE nombre='emisiones'" "emisiones en cero (siguiente_indice se conserva)"
sql_ gym   "UPDATE almacen SET contenido = contenido || '{\"emisiones\":[],\"accesos\":[]}' WHERE nombre='socios'" "emisiones y accesos en cero"
sql_ eps   "UPDATE almacen SET contenido = jsonb_set(contenido,'{emisiones}','[]') WHERE nombre='afiliados'" "emisiones en cero"
for p in aerolinea notaria cancilleria; do
  sql_ $p "UPDATE almacen SET contenido = jsonb_set(contenido,'{tramites}', (SELECT COALESCE(jsonb_agg(CASE WHEN t ? 'entregas' THEN jsonb_set(t,'{entregas}','0') ELSE t END),'[]') FROM jsonb_array_elements(contenido->'tramites') t)) WHERE nombre='tramites'" "entregas en cero"
done
for p in "${PORTALES[@]}"; do h="verifiers/$p/datos/historial.jsonl"; [ -f "$h" ] && : > "$h"; done

for p in "${PORTALES[@]}"; do docker start "$p-$p-1" >/dev/null 2>&1 && echo "  $p: en pie"; done
echo; echo "Listo. Falta la wallet del teléfono (ver el encabezado de este script)."
