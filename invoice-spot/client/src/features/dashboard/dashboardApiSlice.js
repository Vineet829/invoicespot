import { baseApiSlice } from "../api/baseApiSlice";

// The overview's figures come from the server in one request: money for one
// currency, and counts and totals for every document.
export const dashboardApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getOverview: builder.query({
      query: (currency) =>
        currency
          ? `/report/overview?${new URLSearchParams({ currency })}`
          : "/report/overview",
      providesTags: ["Document", "Customer"],
    }),
  }),
});

export const { useGetOverviewQuery } = dashboardApiSlice;
