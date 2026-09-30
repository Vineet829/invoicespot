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
    }),
    getDocumentPdf: builder.mutation({
      queryFn: (id, _api, _extraOptions, baseQuery) =>
        fetchPdf(baseQuery, `/document/${id}/pdf`),
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
  useDeleteDocMutation,
  useCreateDocMutation,
  useGetSingleDocQuery,
  useGetAllMyDocsQuery,
  useUpdateDocMutation,
} = documentsApiSlice;
