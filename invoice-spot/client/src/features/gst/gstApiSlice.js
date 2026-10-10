import { baseApiSlice } from "../api/baseApiSlice";

// The GST state codes, from the server so both sides use one list.
export const gstApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getGstStates: builder.query({
      query: () => "/gst/states",
      keepUnusedDataFor: 3600,
    }),
  }),
});

export const { useGetGstStatesQuery } = gstApiSlice;
