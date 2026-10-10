import { baseApiSlice } from "../api/baseApiSlice";

export const notificationsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // The latest notifications and how many are unread.
    getNotifications: builder.query({
      query: () => "/notification",
      providesTags: ["Notification"],
    }),
    // One notification, or all of them without an id.
    markNotificationsRead: builder.mutation({
      query: (id) => ({
        url: "/notification/read",
        method: "POST",
        body: id ? { id } : {},
      }),
      invalidatesTags: ["Notification"],
    }),
  }),
});

export const { useGetNotificationsQuery, useMarkNotificationsReadMutation } =
  notificationsApiSlice;
