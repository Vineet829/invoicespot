import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { logIn, logOut } from "../auth/authSlice";

const baseQuery = fetchBaseQuery({
  baseUrl:
    import.meta.env.VITE_API_URL ||
    (import.meta.env.DEV
      ? "/api/v1"
      : "https://invoice.vineetpersonal.site/api/v1"),
  credentials: "include",
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth.user?.accessToken;
    const googleToken = getState().auth.googleToken;
    if (token) {
      headers.set("authorization", `Bearer ${token}`);
    } else if (googleToken) {
      headers.set("authorization", `Bearer ${googleToken}`);
    }
    return headers;
  },
});

let refreshInFlight;

// The server answers an expired or revoked access token with an empty 403.
// A 403 with a message is a refusal the token can't fix, so it isn't retried.
const rejectedToken = (error) =>
  (error?.originalStatus ?? error?.status) === 403 && !error?.data;

const baseQueryWithRefreshToken = async (args, api, extraOptions) => {
  // A dashboard loads several resources at once. Rotate its cookie once, then
  // let every waiting request use the same new access token.
  if (refreshInFlight) await refreshInFlight;
  const accessToken = api.getState().auth.user?.accessToken;
  let response = await baseQuery(args, api, extraOptions);

  const path = typeof args === "string" ? args : args.url;
  if (rejectedToken(response?.error) && !path.startsWith("/auth/")) {
    if (accessToken && accessToken !== api.getState().auth.user?.accessToken) {
      return baseQuery(args, api, extraOptions);
    }
    if (!refreshInFlight) {
      refreshInFlight = baseQuery("/auth/new_access_token", api, extraOptions)
        .then((result) => {
          if (result?.data) {
            api.dispatch(logIn({ ...result.data }));
          } else {
            api.dispatch(logOut());
          }
          return result;
        })
        .finally(() => {
          refreshInFlight = undefined;
        });
    }
    const refreshResponse = await refreshInFlight;

    if (refreshResponse?.data) {
      response = await baseQuery(args, api, extraOptions);
    }
  }
  return response;
};

export const baseApiSlice = createApi({
  reducerPath: "api",
  baseQuery: baseQueryWithRefreshToken,
  tagTypes: [
    "User",
    "Customer",
    "Document",
    "Settings",
    "Item",
    "Notification",
    "Recurring",
  ],
  endpoints: (builder) => ({}),
});
