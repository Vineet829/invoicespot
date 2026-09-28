# Invoice Spot frontend

Built with Vite; use Node 22.22+ or 24 LTS. Run the Spring Boot backend on port
8080, then run `npm start` in this directory and open http://localhost:3001.
Development API requests use `/api/v1` and the development server proxies them
to the local backend. The development proxy makes refresh cookies compatible
with local HTTP; production cookie settings are unaffected.

The Guest buttons on login and registration use the dedicated demo account in
that backend's database. The account must be active and email verified.

To use a different API, set `VITE_API_URL` (including `/api/v1`) in
`.env.local` and restart the development server. Production builds retain the
hosted API default unless this variable is supplied at build time.

`npm run build` writes the production build to `dist/`, and `npm run preview`
serves it on port 3001 with the same proxy (build with `VITE_API_URL=/api/v1`
to use the local backend). `npm test` runs Vitest; `npm run lint` runs ESLint.
