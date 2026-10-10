import { baseApiSlice } from "../api/baseApiSlice";

// The overview's figures come from the server in one request: money for one
// currency over one period, beside the period before. Profit counts
// expenses, what needs attention includes recurring invoices, and the
// business's time zone and currency, from its settings, decide the
// overview's days and money, so changes to any of these refresh it too.
export const dashboardApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getOverview: builder.query({
      query: ({ currency, period }) =>
        `/report/overview?${new URLSearchParams({
          ...(currency && { currency }),
          period,
        })}`,
      providesTags: [
        "Document",
        "Customer",
        "Expense",
        "Recurring",
        "Settings",
      ],
    }),
  }),
});

export const { useGetOverviewQuery } = dashboardApiSlice;
