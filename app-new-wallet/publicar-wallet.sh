#!/usr/bin/env bash
# Compila la wallet (variante dev/debug, la del ecosistema) y comprueba que el panel de control ya la ofrece.
# El QR del panel apunta siempre a /descargas/wallet.apk, que sirve el APK más reciente de app/build/outputs/apk:
# no hay nada que "actualizar" en el QR; basta con compilar bien. Este script compila con los parámetros del ecosistema.
#
#   ./app-new-wallet/publicar-wallet.sh                  # compila y verifica contra https://localhost
#   DOMINIO=https://otro.dominio ./app-new-wallet/publicar-wallet.sh
#   INSTALAR=1 ./app-new-wallet/publicar-wallet.sh       # además instala en el celular conectado por adb
set -euo pipefail
cd "$(dirname "$0")"
D="${DOMINIO:-https://civica-desarrollo.avance.org.co}"
PANEL="${PANEL:-https://localhost}"

# Un APK viejo o de otra variante en la carpeta de compilación podría ganarle al nuevo por fecha: se borran antes.
rm -rf app/build/outputs/apk

# Centro de auditoría centralizado: la wallet reporta cada actividad a $D/srv-auditoria con esta clave (la del .env de la raíz).
CLAVE_AUDITORIA="${AUDITORIA_CLAVE_WALLET:-$(grep -s '^AUDITORIA_CLAVE_WALLET=' ../.env | cut -d= -f2-)}"

./gradlew :app:assembleDevDebug \
  -PappIdSuffix=.dev.auditoria -PappNameSuffix="" \
  -PbackendUrl="$D/onboarding" \
  -Pnw.walletprovider.url="$D/wallet-provider" \
  -Pnw.issuer.url="$D/pid-registraduria" \
  -Pnw.tl.wp="$D/tl-publisher/TL/WP/current.json" \
  -Pnw.tl.pid="$D/tl-publisher/TL/PID/current.json" \
  -Pnw.tl.pubeaa="$D/tl-publisher/TL/PUBEAA/current.json" \
  -Pnw.tl.wrpac="$D/tl-publisher/TL/RPREG/current.json" \
  -Pnw.rqes.url="$D/andes-scd" \
  -Pnw.rqes.client.id=wallet-client \
  -Pnw.rqes.client.secret=andes-secreto-de-pruebas \
  -Pnw.banco.url="$D/banco" \
  -Pnw.auditoria.url="$D/srv-auditoria" \
  -Pnw.auditoria.clave="$CLAVE_AUDITORIA"

APK=app/build/outputs/apk/dev/debug/app-dev-debug.apk
LOCAL=$(sha256sum "$APK" | cut -d' ' -f1)
echo "APK compilado: $APK ($(du -h "$APK" | cut -f1)) sha256=$LOCAL"

# El panel monta el repositorio y calcula la huella al pedirla: debe coincidir con la del archivo recién compilado.
PANEL_SHA=$(curl -sk "$PANEL/descargas/apk.json" | python3 -c 'import sys,json;print(json.load(sys.stdin)["sha256"])')
if [ "$PANEL_SHA" = "$LOCAL" ]; then
  echo "✔ El panel ($PANEL/descargas/qr.svg) ya ofrece esta versión."
else
  echo "✘ El panel ofrece otra versión ($PANEL_SHA). Revise que el panel esté arriba y que monte este repositorio." >&2
  exit 1
fi

if [ "${INSTALAR:-0}" = 1 ]; then adb install -r "$APK"; fi
echo "En el panel, recargue la página para ver la fecha, el tamaño y la huella nuevos."
