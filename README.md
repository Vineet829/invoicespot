# Invoice Spot

Invoice Spot is an invoicing app for freelancers and small businesses,
featuring GST invoices, UPI QR codes, payment tracking, and client portals.
It is available at
[invoice.confidental.online](https://invoice.confidental.online).

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Engineering](#engineering)
- [Getting Started](#getting-started)
- [Tests](#tests)
- [Configuration](#configuration)
- [Known Limitations](#known-limitations)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Invoices and Quotations**: Create invoices, quotations, receipts, and
  credit notes with drafts and automatic numbering. Issued documents keep
  their client, lines, and amounts, so mistakes are fixed with a void or a
  credit note.
- **GST and Tax**: Set a tax rate on each line, or turn on India GST mode for
  CGST, SGST, and IGST, HSN/SAC codes, and GSTR-1 and GSTR-3B helper files.
  Record TDS withheld by your clients.
- **Getting Paid**: Show UPI QR codes and bank details on your invoices.
  Clients can report a payment for you to confirm. Send receipts and payment
  reminders.
- **Clients**: See balances by currency, send statements, import clients
  from a CSV file, and give clients their own portal.
- **Sharing**: Download PDFs in three templates, or send documents by email,
  share link, or WhatsApp.
- **Recurring Invoices**: Repeat an invoice weekly, monthly, quarterly, or
  yearly.
- **Expenses**: Record your expenses and see your profit and loss.
- **Reports**: See income, receivables by age, sales, tax, and payments.
  Download them as CSV files, or export everything as a ZIP file.
- **Teams**: Work together in a business with owner, admin, member, and viewer
  roles, and invite people by email.
- **Overview**: Start from a dashboard whose figures open the records behind
  them.
- **Account Security**: Use two-factor sign-in or Google sign-in, see and sign
  out your devices, and review your security activity. Sign-ins and other
  sensitive requests are rate limited.

## Architecture

- **Client**: A React 18 single-page app built with Vite, Redux Toolkit, React
  Router, and Tailwind CSS. In Docker, nginx serves it and passes `/api`
  requests to the backend on the same origin.
- **Backend**: A Java 21 and Spring Boot 3.5 API on PostgreSQL 16, with Flyway
  migrations. It renders PDFs, sends email over SMTP, and runs a daily job for
  recurring invoices, reminders, and overdue alerts.
- **Data**: Clients, documents, payments, and expenses belong to a business.
  The API checks the user's membership and role on every request, and tests
  make sure no business can reach another's data.

## Engineering

- **Testing**: The backend has over 900 tests, over 600 of which run against
  a real PostgreSQL database through Testcontainers. The client has over 650
  tests with Vitest.
- **Money**: Amounts are stored as exact decimals, and totals are calculated
  on the server.
- **Concurrency**: Concurrent edits and the daily job can't undo each other's
  changes to documents and their payments, recurring invoices, clients, or
  settings, because those records are locked while they change.
- **Scaling**: The app is built to run on several servers. The daily job runs
  on one of them at a time, rate limits are shared through PostgreSQL, and
  uploaded logos go to a folder the servers must share (`UPLOAD_DIR`).
- **Encryption**: Two-factor secrets and share links are encrypted with an
  app-wide key.

## Getting Started

The commands below are for macOS and Linux. On Windows, use WSL.

### Prerequisites

- Docker
- Java 21, Maven, and Node.js 24 (24.15 or newer) for local development

### Run with Docker

```bash
cp .env.example .env    # then replace every change-me
docker compose --profile mail up -d --build
```

The first build takes a few minutes. Then open <http://localhost:8088>. Emails
go to Mailpit at <http://localhost:8027>. To stop the app, run
`docker compose --profile mail down`, and add `-v` to delete the data as well.

### Run Locally

Create a sign-in key with `openssl rand -base64 48` and an encryption key
with `openssl rand -base64 32`, and keep them. A new encryption key can't
read the two-factor secrets and share links sealed with the old one.

```bash
# Terminal 1: PostgreSQL and Mailpit, then the API on port 8080
docker compose -f docker/spring/local.yml up -d
export JWT_ACCESS_SECRET_KEY="paste-your-sign-in-key-here"
export APP_ENCRYPTION_KEY="paste-your-encryption-key-here"
export CLIENT_URL=http://localhost:3001    # the client's address, for links
mvn spring-boot:run
```

```bash
# Terminal 2: the client on port 3001
cd invoice-spot/client
npm install
npm start
```

Open <http://localhost:3001>. Emails go to Mailpit at <http://localhost:8026>.

### First Login

1. Sign up, then open the verification email in Mailpit and follow the link.
2. Sign in.
3. To make yourself an admin, run this SQL once and sign in again. Admins can
   then promote other accounts in the app.

```sql
INSERT INTO user_roles (user_pkid, role)
SELECT pkid, 'Admin' FROM users WHERE lower(email) = lower('you@example.com');
```

If it prints `INSERT 0 0`, no account has that email. To open the database,
run `docker exec -it invoicespot_postgres psql -U invoicespot -d invoicespot`
locally, or `docker compose exec db psql -U invoicespot -d invoicespot` with
the Docker stack.

## Tests

```bash
mvn test                  # backend, needs Docker for PostgreSQL

cd invoice-spot/client    # after npm install
npm run lint
npx vitest run
```

## Configuration

All settings are environment variables. `.env.example` explains the main
ones, and `src/main/resources/application.yml` holds the defaults. For
production:

- **Keys**: Set `JWT_ACCESS_SECRET_KEY` and `APP_ENCRYPTION_KEY`, and keep the
  encryption key safe. A new key can't read the two-factor secrets and share
  links sealed with the old one.
- **HTTPS**: Put a TLS reverse proxy such as Caddy or nginx in front of port
  8088, point `CLIENT_URL` and `SERVER_URL` at its https address, and set
  `COOKIE_SECURE=true`.
- **Email**: Set `EMAIL_HOST`, `EMAIL_PORT`, `SENDER_EMAIL`, and the
  `SPRING_MAIL_*` login lines.
- **Google Sign-In**: Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, and
  register the callback address given in `.env.example`.

## Known Limitations

- There is no payment gateway. Clients pay by UPI, bank transfer, or a payment
  link you add, and you record the payment or confirm the one they report.
- GST e-invoicing (IRN), e-way bills, cess, SEZ supplies, and the composition
  scheme aren't supported. The GSTR-1 and GSTR-3B files are helpers to check
  before filing.
- Exchange rates are entered by hand. There is no rate feed.

## Project Structure

- `src/`: The Spring Boot backend.
- `invoice-spot/client/`: The React client.
- `docker/spring/local.yml`: PostgreSQL and Mailpit for local development.

## Contributing

Bug reports and questions are welcome as
[issues](https://github.com/Vineet829/invoicespot/issues).

## License

[MIT](LICENSE) © 2023-2026 Vineet Singh. The bundled Noto Sans fonts are under
the [SIL Open Font License](src/main/resources/fonts/OFL.txt).
