FROM node:22-alpine
RUN apk add --no-cache docker-cli docker-cli-compose
WORKDIR /app
# Genera el QR de descarga de la wallet (SVG) en el servidor.
RUN npm install --no-audit --no-fund --omit=dev qrcode@1.5.4 && npm pkg set type=module
COPY server.js app.js index.html styles.css recursos.html recursos.js recursos.css login.html login.css login.js ./
EXPOSE 4173
CMD ["node", "server.js"]
