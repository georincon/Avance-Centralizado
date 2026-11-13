FROM node:22-alpine
WORKDIR /app
COPY backend/package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY backend/src ./src
COPY frontend ./frontend
USER node
EXPOSE 8110
CMD ["node", "src/servidor.js"]
