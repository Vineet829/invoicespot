# Invoice Spot frontend

Run the Spring Boot backend on port 8080, then run `PORT=3001 npm start` in this
directory. Development API requests use `/api/v1` and the development server
proxies them to the local backend. The development proxy makes refresh cookies
compatible with local HTTP; production cookie settings are unaffected.

The Guest buttons on login and registration use the dedicated demo account in
that backend's database. The account must be active and email verified.

To use a different API, set `REACT_APP_API_URL` (including `/api/v1`) in
`.env.local` and restart the development server. Production builds retain the
hosted API default unless this variable is supplied at build time.
