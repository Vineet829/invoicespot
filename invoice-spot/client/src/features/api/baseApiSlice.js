import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { logIn, logOut } from "../auth/authSlice";
import { businessChanged } from "../business/businessSlice";
import { API_BASE } from "./apiBase";

const baseQuery = fetchBaseQuery({
  baseUrl: API_BASE,
  credentials: "include",
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth.user?.accessToken;
    if (token) headers.set("authorization", `Bearer ${token}`);
    const businessId = getState().business?.activeId;
    if (businessId) headers.set("x-business-id", businessId);
    return headers;
  },
});

// A 409 naming the session's business means another tab switched it, or the
// user is no longer in any. The app then starts over in that business.
const followBusiness = (response, api) => {
  const moved =
    response?.error?.status === 409 &&
    response.meta?.response?.headers?.get("X-Active-Business");
  if (moved) api.dispatch(businessChanged(moved === "none" ? null : moved));
  return response;
};

// A refresh can swap the session's cookie. keepalive lets the new cookie
// reach the browser even when the page reloads or closes before the answer
// does; a browser left holding the cookie the server replaced would, on a
// later refresh, sign out every device of the account.
export const refreshRequest = {
  url: "/auth/new_access_token",
  keepalive: true,
};

let refreshInFlight;

// Only the server refusing the session ends it. A refresh lost to the
// network, or answered by a server that is restarting, leaves the user
// signed in, with whatever they were typing, to try again.
const sessionRefused = (error) =>
  [401, 403].includes(error?.originalStatus ?? error?.status);

// The server answers an expired or revoked access token with an empty 403.
// A 403 with a message is a refusal the token can't fix, so it isn't retried.
const rejectedToken = (error) =>
  (error?.originalStatus ?? error?.status) === 403 && !error?.data;

const baseQueryWithRefreshToken = async (args, api, extraOptions) =>
  followBusiness(await refreshingQuery(args, api, extraOptions), api);

const refreshingQuery = async (args, api, extraOptions) => {
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
      refreshInFlight = baseQuery(refreshRequest, api, extraOptions)
        .then((result) => {
          if (result?.data) {
            api.dispatch(logIn({ ...result.data }));
          } else if (sessionRefused(result?.error)) {
            api.dispatch(logOut());
            // What the session loaded goes with it, so whoever signs in next
            // in this tab never sees it.
            api.dispatch(baseApiSlice.util.resetApiState());
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
    "Session",
    "TwoFactor",
    "Activity",
    "Business",
    "Team",
    "Invitation",
    "Portal",
    "Expense",
  ],
  endpoints: (builder) => ({}),
});
