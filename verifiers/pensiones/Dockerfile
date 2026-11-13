FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY src ./src
# Sin volúmenes: el portal no guarda nada en disco.
USER node
# srv-nw-nginx llama a app-pensiones:80.
EXPOSE 80
CMD ["node", "src/servidor.js"]
