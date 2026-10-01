import { baseApiSlice } from "../api/baseApiSlice";
import { logOut } from "./authSlice";

export const authApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    registerUser: builder.mutation({
      query: (userData) => ({
        url: "/auth/register",
        method: "POST",
        body: userData,
      }),
    }),
    loginUser: builder.mutation({
      query: (credentials) => ({
        url: "/auth/login",
        method: "POST",
        body: credentials,
      }),
    }),
    completeTwoFactor: builder.mutation({
      query: ({ challenge, code }) => ({
        url: "/auth/login/two-factor",
        method: "POST",
        body: { challenge, code },
      }),
    }),

    logoutUser: builder.mutation({
      query: () => ({
        url: "/auth/logout",
        method: "POST",
      }),

      async onQueryStarted(arg, { dispatch, queryFulfilled }) {
        try {
          dispatch(logOut());
          dispatch(baseApiSlice.util.resetApiState());
        } catch (err) {
          console.log(err);
        }
      },
    }),
    resendVerifyEmail: builder.mutation({
      query: (userEmail) => ({
        url: "/auth/resend_email_token",
        method: "POST",
        body: userEmail,
      }),
    }),
    passwordResetRequest: builder.mutation({
      query: (formData) => ({
        url: "/auth/reset_password_request",
        method: "POST",
        body: formData,
      }),
    }),
    resetPassword: builder.mutation({
      query: (formData) => ({
        url: "/auth/reset_password",
        method: "POST",
        body: formData,
      }),
    }),
    // The sign-in providers the server is set up for, such as Google.
    getSignInProviders: builder.query({
      query: () => "/auth/providers",
    }),
    // Swaps the session's refresh cookie for an access token, as a reload
    // does; Google sign-in ends with a session and nothing else.
    refreshSession: builder.mutation({
      query: () => "/auth/new_access_token",
    }),
  }),
});

export const {
  useRegisterUserMutation,
  useLoginUserMutation,
  useLogoutUserMutation,
  useResendVerifyEmailMutation,
  usePasswordResetRequestMutation,
  useResetPasswordMutation,
  useCompleteTwoFactorMutation,
  useGetSignInProvidersQuery,
  useRefreshSessionMutation,
} = authApiSlice;
