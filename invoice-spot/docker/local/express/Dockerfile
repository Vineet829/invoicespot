ARG NODE_VERSION=18-alpine
FROM node:${NODE_VERSION}


LABEL name="invoice-spot" \
      license="MIT" \
      description="An invoice image processing service"


ENV NODE_ENV=development


ARG APP_HOME=/app
WORKDIR ${APP_HOME}


RUN addgroup -g 1001 invoice && \
    adduser -D -u 1001 -G invoice invoice && \
    chown -R invoice:invoice ${APP_HOME}

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


COPY --chown=invoice:invoice . ${APP_HOME}


USER invoice


CMD ["npm", "run", "dev"]
