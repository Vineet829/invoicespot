// Where the API lives. Requests go through baseApiSlice, but signing in with
// Google leaves the app for an address on the API, so both read it from here.
// Unless the build names another (VITE_API_URL, including /api/v1), it is on
// the app's own origin: the development server and the Docker image's nginx
// pass /api/v1 on to the backend.
export const API_BASE = import.meta.env.VITE_API_URL || "/api/v1";
