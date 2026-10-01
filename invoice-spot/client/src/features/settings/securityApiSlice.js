import { baseApiSlice } from "../api/baseApiSlice";

export const securityApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSessions: builder.query({
      query: () => "/account/sessions",
      providesTags: ["Session"],
    }),
    signOutSession: builder.mutation({
      query: (id) => ({ url: `/account/sessions/${id}`, method: "DELETE" }),
      invalidatesTags: ["Session", "Activity"],
    }),
    changePassword: builder.mutation({
      query: (passwords) => ({
        url: "/account/password",
        method: "POST",
        body: passwords,
      }),
      invalidatesTags: ["Session", "Activity"],
    }),
    getActivity: builder.query({
      query: ({ page, size }) =>
        `/account/activity?${new URLSearchParams({ page, size })}`,
      providesTags: ["Activity"],
    }),
    getTwoFactor: builder.query({
      query: () => "/account/two-factor",
      providesTags: ["TwoFactor"],
    }),
    setupTwoFactor: builder.mutation({
      query: (password) => ({
        url: "/account/two-factor/setup",
        method: "POST",
        body: { password },
      }),
    }),
    enableTwoFactor: builder.mutation({
      query: (code) => ({
        url: "/account/two-factor/enable",
        method: "POST",
        body: { code },
      }),
      // Turning it on or off signs out the other devices.
      invalidatesTags: ["TwoFactor", "Activity", "Session"],
    }),
    disableTwoFactor: builder.mutation({
      query: ({ password, code }) => ({
        url: "/account/two-factor/disable",
        method: "POST",
        body: { password, code },
      }),
      invalidatesTags: ["TwoFactor", "Activity", "Session"],
    }),
    renewRecoveryCodes: builder.mutation({
      query: (code) => ({
        url: "/account/two-factor/recovery-codes",
        method: "POST",
        body: { code },
      }),
      invalidatesTags: ["TwoFactor", "Activity"],
    }),
    signOutOtherSessions: builder.mutation({
      query: () => ({
        url: "/account/sessions/sign-out-others",
        method: "POST",
      }),
      invalidatesTags: ["Session", "Activity"],
    }),
  }),
});

export const {
  useChangePasswordMutation,
  useDisableTwoFactorMutation,
  useEnableTwoFactorMutation,
  useGetActivityQuery,
  useGetSessionsQuery,
  useGetTwoFactorQuery,
  useRenewRecoveryCodesMutation,
  useSetupTwoFactorMutation,
  useSignOutSessionMutation,
  useSignOutOtherSessionsMutation,
} = securityApiSlice;
