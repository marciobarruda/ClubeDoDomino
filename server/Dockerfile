FROM node:20-alpine

# Definir diretório de trabalho
WORKDIR /usr/src/app

# Copiar arquivos de dependências
COPY package*.json ./

# Instalar dependências de produção
RUN npm ci --only=production

# Copiar o código fonte
COPY . .

# Expor a porta que a aplicação escuta
EXPOSE 3000

# Comando para iniciar o servidor
CMD [ "node", "server.js" ]
