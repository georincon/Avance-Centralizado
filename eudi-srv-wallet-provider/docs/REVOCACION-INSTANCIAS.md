# Revocacion de instancias de wallet (endpoint interno)

ARF `WURevocation_07`: para revocar una Wallet Instance, el proveedor pone `revoked` en los indices de las listas de
estado de los WIA emitidos a esa instancia (`WURevocation_07a`: los de las KA del modulo de claves). El proveedor no
guarda a que instancia pertenece cada indice (lo sabe el emisor), por eso el endpoint recibe los indices.

```
POST /internal/v1/wallet-instances/revoke
X-API-Key: <WALLETPROVIDER_REVOCATION_APIKEY>
{"entries":[{"statusList":"<URI publica de la lista>","index":123}, ...]}   # 1 a 200 entradas
-> 200 {"revoked":N,"failed":N}
```

| Codigo | Motivo |
|---|---|
| 200 | Procesado; cada entrada fallida suma a `failed` y no detiene las demas |
| 400 | JSON invalido, `entries` vacio o con mas de 200, `statusList` en blanco o `index` negativo |
| 401 | `X-API-Key` ausente o distinta (comparacion en tiempo constante) |
| 503 | `WALLETPROVIDER_REVOCATION_APIKEY` no configurada: endpoint deshabilitado |
| 404 | La peticion trae `X-Forwarded-*`/`Forwarded` (llego por el proxy publico) |

- Cada entrada se revoca con `POST <TOKENSTATUSLISTSERVICE_SERVICEURL con /take -> /set>` (`idx`, `uri`, `status=1`);
  la URI publica se recorta desde `/token_status_list/`. Es idempotente (revocar dos veces no es error).
- Solo para la red docker: el nginx central enruta `/wallet-provider/...` hacia este servicio quitando el prefijo, asi que
  por si solo no bloquea `/internal/`. La barrera es la clave y el rechazo (404) de peticiones con cabeceras de proxy.
  Llamar directo: `http://spartans-wallet-provider:8080/internal/v1/wallet-instances/revoke`.
- Irreversible: el bit de una lista de estado no vuelve a 0.
