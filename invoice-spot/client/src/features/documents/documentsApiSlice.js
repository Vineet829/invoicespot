import { baseApiSlice } from "../api/baseApiSlice";
import { fetchPdf } from "../api/fetchPdf";

export const documentsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // One page of documents; see documentQuery for the search and filters.
    getAllMyDocs: builder.query({
      query: (params = {}) => `/document/all?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    getSingleDoc: builder.query({
      query: (id) => `/document/${id}`,
      providesTags: ["Document"],
    }),
    duplicateDoc: builder.mutation({
      query: (id) => ({ url: `/document/${id}/duplicate`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    // A quotation becomes an invoice once; each then names the other.
    convertDoc: builder.mutation({
      query: (id) => ({ url: `/document/${id}/convert`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    // A document's share link. The server keeps only a hash of it, so the
    // link itself comes back once, from createShareLink.
    getShareStatus: builder.query({
      query: (id) => `/document/${id}/share`,
      providesTags: ["Document"],
    }),
    createShareLink: builder.mutation({
      query: (id) => ({ url: `/document/${id}/share`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    revokeShareLink: builder.mutation({
      query: (id) => ({ url: `/document/${id}/share`, method: "DELETE" }),
      invalidatesTags: ["Document"],
    }),
    // What happened to a document, newest first.
    getDocumentActivity: builder.query({
      query: (id) => `/document/${id}/activity`,
      providesTags: ["Document"],
    }),
    createDoc: builder.mutation({
      query: (formData) => ({
        url: "/document/create",
        method: "POST",
        body: formData,
      }),
      invalidatesTags: ["Document"],
    }),
    updateDoc: builder.mutation({
      query: ({ id, ...rest }) => ({
        url: `/document/${id}`,
        method: "PATCH",
        body: rest,
      }),
      invalidatesTags: ["Document"],
    }),
    deleteDoc: builder.mutation({
      query: (id) => ({
        url: `/document/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Document"],
    }),
    getEmailDraft: builder.query({
      query: (id) => `/document/${id}/email`,
      keepUnusedDataFor: 0,
    }),
    emailDocument: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/document/${id}/email`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    getDocumentPdf: builder.mutation({
      queryFn: (id, _api, _extraOptions, baseQuery) =>
        fetchPdf(baseQuery, `/document/${id}/pdf`),
    }),
    getReminderDraft: builder.query({
      query: (id) => `/document/${id}/remind`,
      keepUnusedDataFor: 0,
    }),
    sendReminder: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/document/${id}/remind`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    getReceiptPdf: builder.mutation({
      queryFn: ({ id, paymentId }, _api, _extraOptions, baseQuery) =>
        fetchPdf(baseQuery, `/document/${id}/payment/${paymentId}/receipt`),
    }),
    getReceiptEmailDraft: builder.query({
      query: ({ id, paymentId }) =>
        `/document/${id}/payment/${paymentId}/receipt/email`,
      keepUnusedDataFor: 0,
    }),
    emailReceipt: builder.mutation({
      query: ({ id, paymentId, ...email }) => ({
        url: `/document/${id}/payment/${paymentId}/receipt/email`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    updatePayment: builder.mutation({
      query: ({ id, paymentId, ...payment }) => ({
        url: `/document/${id}/payment/${paymentId}`,
        method: "PATCH",
        body: payment,
      }),
      invalidatesTags: ["Document"],
    }),
    deletePayment: builder.mutation({
      query: ({ id, paymentId }) => ({
        url: `/document/${id}/payment/${paymentId}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Document"],
    }),
    createPayment: builder.mutation({
      query: ({ id, ...rest }) => ({
        url: `/document/${id}/payment`,
        method: "POST",
        body: rest,
      }),
      invalidatesTags: ["Document"],
    }),
  }),
});

export const {
  useCreatePaymentMutation,
  useDeletePaymentMutation,
  useUpdatePaymentMutation,
  useEmailDocumentMutation,
  useGetEmailDraftQuery,
  useGetDocumentPdfMutation,
  useGetReceiptPdfMutation,
  useGetReminderDraftQuery,
  useSendReminderMutation,
  useGetReceiptEmailDraftQuery,
  useEmailReceiptMutation,
  useDeleteDocMutation,
  useCreateDocMutation,
  useGetSingleDocQuery,
  useGetDocumentActivityQuery,
  useGetShareStatusQuery,
  useCreateShareLinkMutation,
  useRevokeShareLinkMutation,
  useDuplicateDocMutation,
  useConvertDocMutation,
  useGetAllMyDocsQuery,
  useUpdateDocMutation,
} = documentsApiSlice;
