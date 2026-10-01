// Where the API lives. Requests go through baseApiSlice, but signing in with
// Google leaves the app for an address on the API, so both read it from here.
// In development the dev server forwards /api/v1 to the local backend.
export const API_BASE =
  import.meta.env.VITE_API_URL ||
  (import.meta.env.DEV
    ? "/api/v1"
    : "https://invoice.vineetpersonal.site/api/v1");
