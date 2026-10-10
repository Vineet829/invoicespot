import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

const fileForm = (file) => {
  const form = new FormData();
  form.append("file", file);
  return form;
};

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
    // Every client billed in a period, in a currency or the default one,
    // with what they paid and still owe, a page at a time, and the totals.
    // See customerSalesQuery.
    getCustomerSales: builder.query({
      query: (params = {}) => `/customer/sales?${new URLSearchParams(params)}`,
      providesTags: ["Customer", "Document"],
    }),
    // What a client was billed, has paid and still owes, per currency.
    getCustomerSummary: builder.query({
      query: (custId) => `/customer/${custId}/summary`,
      providesTags: ["Customer", "Document"],
    }),
    // The same file goes to both: the server reads it for the preview and
    // again when adding, so the preview shows exactly what is added.
    previewClientImport: builder.mutation({
      query: (file) => ({
        url: "/customer/import/preview",
        method: "POST",
        body: fileForm(file),
      }),
    }),
    importClients: builder.mutation({
      query: (file) => ({
        url: "/customer/import",
        method: "POST",
        body: fileForm(file),
      }),
      invalidatesTags: ["Customer"],
    }),
    getCustomersCsv: builder.mutation({
      queryFn: (filters, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/customer/csv?${new URLSearchParams(filters)}`),
    }),
    getStatementPdf: builder.mutation({
      queryFn: (custId, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/customer/${custId}/statement`),
    }),
    getStatementEmailDraft: builder.query({
      query: (custId) => `/customer/${custId}/statement/email`,
      keepUnusedDataFor: 0,
    }),
    // The client's portal link: its status, including the link itself when
    // the server kept it sealed.
    getPortalStatus: builder.query({
      query: (custId) => `/customer/${custId}/portal`,
      providesTags: ["Customer"],
    }),
    createPortalLink: builder.mutation({
      query: (custId) => ({
        url: `/customer/${custId}/portal`,
        method: "POST",
      }),
      invalidatesTags: ["Customer"],
    }),
    getPortalEmailDraft: builder.query({
      query: (custId) => `/customer/${custId}/portal/email`,
      keepUnusedDataFor: 0,
    }),
    emailPortal: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/customer/${id}/portal/email`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Customer"],
    }),
    sharePortalOnWhatsApp: builder.mutation({
      query: (custId) => ({
        url: `/customer/${custId}/portal/whatsapp`,
        method: "POST",
      }),
      invalidatesTags: ["Customer"],
    }),
    revokePortalLink: builder.mutation({
      query: (custId) => ({
        url: `/customer/${custId}/portal`,
        method: "DELETE",
      }),
      invalidatesTags: ["Customer"],
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
  useGetCustomersCsvMutation,
  useImportClientsMutation,
  usePreviewClientImportMutation,
  useGetSingleCustomerQuery,
  useGetCustomerSalesQuery,
  useGetCustomerSummaryQuery,
  useGetStatementPdfMutation,
  useGetStatementEmailDraftQuery,
  useEmailStatementMutation,
  useCreateCustomerMutation,
  useUpdateCustomerInfoMutation,
  useDeleteCustomerMutation,
  useGetPortalStatusQuery,
  useCreatePortalLinkMutation,
  useRevokePortalLinkMutation,
  useGetPortalEmailDraftQuery,
  useEmailPortalMutation,
  useSharePortalOnWhatsAppMutation,
} = customersApiSlice;
