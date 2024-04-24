ARG NODE_VERSION=18-alpine
FROM node:${NODE_VERSION}

# Set metadata for the image
LABEL invoice.name="invoice-spot" \
      invoice.license="MIT" 
      

# Set the Node environment to development by default
ENV NODE_ENV=production

# Define the application's directory within the container
ARG APP_HOME=/app
WORKDIR ${APP_HOME}

# Create a user 'invoice' without specifying explicit numeric IDs, and set ownership
RUN addgroup -g 1001 invoice && \
    adduser -D -u 1001 -G invoice invoice && \
    chown -R invoice:invoice ${APP_HOME}
# Install necessary packages for Chromium
RUN apk add --no-cache \
    udev \
    ttf-freefont \
    chromium \
    fontconfig

# Set environment variables for Chromium path and Puppeteer configuration
ENV CHROMIUM_PATH=/usr/bin/chromium-browser \
    CHROME_BIN=/usr/bin/chromium-browser \
    PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true

# Copy package.json and package-lock.json (or npm's equivalent) to leverage Docker cache
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the application source code and assign ownership to the 'invoice' user
COPY --chown=invoice:invoice . ${APP_HOME}

# Switch to the 'invoice' user to run the application
USER invoice

# Define the command to run the application
CMD ["npm", "run", "start"]
