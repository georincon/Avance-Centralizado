FROM node:22-alpine
WORKDIR /app
# openssl: comprueba la firma y el sello de tiempo del contrato que devuelve la wallet (src/firma.js).
RUN apk add --no-cache openssl
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY src ./src
# Sin volúmenes de datos: el portal no guarda nada en disco (los certificados se montan de solo lectura).
USER node
# srv-nw-nginx llama a app-contratacion:80.
EXPOSE 80
CMD ["node", "src/servidor.js"]
