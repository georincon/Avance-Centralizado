# docker-compose — Spartans Wallet Provider (punto de entrada)

El Compose real vive en `../docker/docker-compose.yaml` (no se modifica). Este script lo levanta
respetando la red compartida del ecosistema.

```bash
./up.sh      # levanta el stack (crea la red compartida si falta)
./down.sh    # detiene (./down.sh -v borra también los datos)
```

- Proyecto Docker: `spartans`.
- **No publica puertos en el host**: el servicio se alcanza por la red compartida `avance-eudi`
  a través del proxy único `Avance/nw-nginx`.
- URL pública: `https://civica-desarrollo.avance.org.co/wallet-provider`
- Requisito previo: `../scripts/generar-pki-spartans.sh` (keystore de firma y claves de la lista de estado).
