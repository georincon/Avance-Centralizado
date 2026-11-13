#!/usr/bin/env bash
#
# Construye el almacen de claves de confianza que usa el validador para validar
# cadenas X.509 directamente contra las anclas publicadas.
#
# POR QUE EXISTE
#
# El validador tiene dos caminos para decidir si una cadena es de fiar:
#
#   1) La LoTL (DSS): descarga la LOTL, la lista de listas y las listas de
#      confianza, y extrae de ellas las anclas. En esta build el DSS NO extrae
#      ninguna: se comprobo que tampoco funciona contra la LOTL oficial del
#      testbed EUDI (0 certificados, "No configuration found for
#      VerificationContext ..."), asi que es una limitacion de la libreria
#      etsi1196x2 alpha con DSS 6.5, no de las listas de este ecosistema.
#
#   2) El almacen de claves: las anclas estan en un JKS y se validan contra el.
#      Este camino SI funciona, y es el que deja el validador operativo.
#
# Las anclas se toman de las MISMAS que publica el `srv-tl-publisher`
# (`pki/publico/ca/*.crt`), de modo que el validador y las listas de confianza
# no pueden discrepar: si se cambia una PKI, se reconstruye este almacen.
#
# USO
#
#   ./construir-jks-anclas.sh          reconstruye el almacen
#   ./construir-jks-anclas.sh --check  solo comprueba que esta al dia
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROYECTO="$(cd "$HERE/.." && pwd)"
AVANCE="$(cd "$PROYECTO/.." && pwd)"

PKI_PUBLICA="$AVANCE/srv-tl-publisher/pki/publico/ca"
DESTINO="$PROYECTO/claves/anclas-confianza.jks"
PASSWORD="changeit"
SOLO_CHECK="${1:-}"

[ -d "$PKI_PUBLICA" ] || { echo "ERROR: no existe $PKI_PUBLICA"; exit 1; }
mkdir -p "$PROYECTO/claves"

# Las anclas de confianza son las sub-CAs que aparecen en las listas. El
# firmante de las listas (`tl.crt`) NO es un ancla: firma las listas, no
# credenciales.
ANCLAS=()
for f in "$PKI_PUBLICA"/*.crt; do
  base="$(basename "$f")"
  [ "$base" = "tl.crt" ] && continue
  ANCLAS+=("$f")
done

[ "${#ANCLAS[@]}" -gt 0 ] || { echo "ERROR: no hay anclas en $PKI_PUBLICA"; exit 1; }

# Firma del contenido: si cambia, hay que reconstruir.
FIRMA="$(cat "${ANCLAS[@]}" | sha256sum | cut -d' ' -f1)"

if [ "$SOLO_CHECK" = "--check" ]; then
  if [ -f "$DESTINO" ]; then
    actual="$(keytool -list -keystore "$DESTINO" -storepass "$PASSWORD" 2>/dev/null | grep -c 'trustedCertEntry')"
    echo "almacen: $DESTINO ($actual entradas)"
  else
    echo "almacen: NO EXISTE ($DESTINO)"
  fi
  echo "firma del conjunto de anclas: $FIRMA"
  exit 0
fi

echo "==> Construyendo $DESTINO con ${#ANCLAS[@]} anclas"
rm -f "$DESTINO"
for f in "${ANCLAS[@]}"; do
  alias="$(basename "$f" .crt)"
  keytool -importcert -noprompt -trustcacerts \
    -alias "$alias" -file "$f" \
    -keystore "$DESTINO" -storetype JKS -storepass "$PASSWORD" >/dev/null
done
# Solo material PUBLICO (certificados de ancla), como el lotl-trust.jks: el
# contenedor corre con otro uid y tiene que poder leerlo.
chmod 644 "$DESTINO"

n="$(keytool -list -keystore "$DESTINO" -storepass "$PASSWORD" 2>/dev/null | grep -c 'trustedCertEntry')"
echo "    $n anclas de confianza"
echo "    firma del conjunto: $FIRMA"
echo
echo "Para que el validador lo use, en su compose:"
echo "  - TRUST_VALIDATOR_TRUST_SOURCES_KEY_STORE_LOCATION=file:/claves/anclas-confianza.jks"
echo "  - TRUST_VALIDATOR_TRUST_SOURCES_KEY_STORE_KEY_STORE_TYPE=JKS"
echo "  - TRUST_VALIDATOR_TRUST_SOURCES_KEY_STORE_PASSWORD=$PASSWORD"
