ARG NODE_VERSION=18-alpine
FROM node:${NODE_VERSION}


LABEL invoice.name="invoice-spot" \
      invoice.license="MIT" 
      


ENV NODE_ENV=production


ARG APP_HOME=/app
WORKDIR ${APP_HOME}




RUN apk add --no-cache \
    udev \
    ttf-freefont \
    chromium \
    fontconfig


ENV CHROMIUM_PATH=/usr/bin/chromium-browser \
    CHROME_BIN=/usr/bin/chromium-browser \
    PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true


COPY package*.json ./


RUN npm install


COPY . ${APP_HOME}




CMD ["npm", "run", "start"]
