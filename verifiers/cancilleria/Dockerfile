FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY src ./src
# /datos: el protocolo (tramites.json). /secretos: la clave de firma. Los dos los
# monta compose.yml desde la carpeta de la cancillería.
RUN mkdir /datos && chown node:node /datos
USER node
# El nginx central llama a <prefijo>:80.
EXPOSE 80
CMD ["node", "src/servidor.js"]
