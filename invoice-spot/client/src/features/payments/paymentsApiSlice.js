import { baseApiSlice } from "../api/baseApiSlice";

// Money in and out on issued documents: payments on invoices, receipts and
// refunds, a page at a time, with what each currency adds up to. Each row
// names its client as the document has it, so only changes to documents,
// and to their payments and refunds, refresh it.
export const paymentsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // See paymentQuery for the search and filters.
    getPayments: builder.query({
      query: (params = {}) => `/payments?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    // What came in over a period, in a currency or the default one: the
    // total against the period before, by method and by the clients who
    // paid the most. See paymentSummaryQuery.
    getPaymentsSummary: builder.query({
      query: (params = {}) => {
        const search = new URLSearchParams(params).toString();
        return `/payments/summary${search ? `?${search}` : ""}`;
      },
      providesTags: ["Document"],
    }),
  }),
});

export const { useGetPaymentsQuery, useGetPaymentsSummaryQuery } =
  paymentsApiSlice;
