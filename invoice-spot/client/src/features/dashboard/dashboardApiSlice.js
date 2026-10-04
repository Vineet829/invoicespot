import { baseApiSlice } from "../api/baseApiSlice";

// The overview's figures come from the server in one request: money for one
// currency over one period, beside the period before, and counts and totals
// for every document. Profit counts expenses, and what needs attention
// includes recurring invoices, so changes to either refresh it too.
export const dashboardApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getOverview: builder.query({
      query: ({ currency, period }) =>
        `/report/overview?${new URLSearchParams({
          ...(currency && { currency }),
          period,
        })}`,
      providesTags: ["Document", "Customer", "Expense", "Recurring"],
    }),
  }),
});

export const { useGetOverviewQuery } = dashboardApiSlice;
