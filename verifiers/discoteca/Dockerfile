FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY src ./src
# Sin volúmenes: la discoteca no guarda nada.
USER node
# El proxy único (srv-nw-nginx) llama a app-discoteca:80.
EXPOSE 80
CMD ["node", "src/servidor.js"]
