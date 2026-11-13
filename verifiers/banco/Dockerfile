FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY src ./src
# /datos guarda clientes.json (montado desde ./datos en compose.yml).
RUN mkdir /datos && chown node:node /datos
USER node
# El nginx central llama a <prefijo>:80. Docker deja a un usuario sin privilegios
# abrir puertos bajos dentro del contenedor.
EXPOSE 80
CMD ["node", "src/servidor.js"]
