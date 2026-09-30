import { baseApiSlice } from "../api/baseApiSlice";
import { fetchPdf } from "../api/fetchPdf";

export const customersApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // One page of clients, with how many documents each has and what it
    // still owes, so a document or payment change refreshes it too; see
    // clientQuery for the search and sort.
    getAllUserCustomers: builder.query({
      query: (params = {}) => `/customer/all?${new URLSearchParams(params)}`,
      providesTags: ["Customer", "Document"],
    }),
    createCustomer: builder.mutation({
      query: (customerInfo) => ({
        url: "/customer/create",
        method: "POST",
        body: customerInfo,
      }),
      invalidatesTags: ["Customer"],
    }),
    getSingleCustomer: builder.query({
      query: (custId) => `/customer/${custId}`,
      providesTags: ["Customer"],
    }),
    // What a client was billed, has paid and still owes, per currency.
    getCustomerSummary: builder.query({
      query: (custId) => `/customer/${custId}/summary`,
      providesTags: ["Customer", "Document"],
    }),
    getStatementPdf: builder.mutation({
      queryFn: (custId, _api, _extraOptions, baseQuery) =>
        fetchPdf(baseQuery, `/customer/${custId}/statement`),
    }),
    getStatementEmailDraft: builder.query({
      query: (custId) => `/customer/${custId}/statement/email`,
      keepUnusedDataFor: 0,
    }),
    emailStatement: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/customer/${id}/statement/email`,
        method: "POST",
        body: email,
      }),
    }),

    updateCustomerInfo: builder.mutation({
      query: ({ id, ...otherFields }) => ({
        url: `/customer/${id}`,
        method: "PATCH",
        body: otherFields,
      }),
      invalidatesTags: ["Customer"],
    }),
    deleteCustomer: builder.mutation({
      query: (id) => ({
        url: `/customer/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Customer"],
    }),
  }),
});

export const {
  useGetAllUserCustomersQuery,
  useGetSingleCustomerQuery,
  useGetCustomerSummaryQuery,
  useGetStatementPdfMutation,
  useGetStatementEmailDraftQuery,
  useEmailStatementMutation,
  useCreateCustomerMutation,
  useUpdateCustomerInfoMutation,
  useDeleteCustomerMutation,
} = customersApiSlice;
