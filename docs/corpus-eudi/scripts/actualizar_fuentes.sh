#!/usr/bin/env bash
# Actualiza los repositorios clonados (solo lectura) y reconstruye el índice.
# Uso: bash scripts/actualizar_fuentes.sh
set -u
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
for d in "$RAIZ"/referencia/*/ "$RAIZ/fuentes/arf-repo/" "$RAIZ/fuentes/standards-repo/"; do
  [ -d "$d/.git" ] || continue
  before=$(git -C "$d" rev-parse --short HEAD)
  git -C "$d" pull -q --ff-only 2>&1 | tail -1
  after=$(git -C "$d" rev-parse --short HEAD)
  printf '%-58s %s -> %s\n' "$(basename "$d")" "$before" "$after"
done
echo "Reconstruyendo índice..."
python3 -I "$RAIZ/scripts/construir_indice.py" | tail -3
echo "Anota las versiones nuevas en CORPUS.md y revisa el CHANGELOG del ARF (fuentes/arf-repo/CHANGELOG.md)."
