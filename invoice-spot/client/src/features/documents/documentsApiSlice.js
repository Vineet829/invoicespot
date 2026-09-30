import { baseApiSlice } from "../api/baseApiSlice";

export const documentsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getAllMyDocs: builder.query({
      query: (page = 1) => `/document/all?page=${page}`,
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
    // The PDF needs the sign-in token, so it comes back as a blob and is
    // handed over as an object URL, which the caller revokes.
    getDocumentPdf: builder.mutation({
      queryFn: async (id, _api, _extraOptions, baseQuery) => {
        const result = await baseQuery({
          url: `/document/${id}/pdf`,
          responseHandler: (response) =>
            response.ok ? response.blob() : response.json(),
        });
        if (result.error) return { error: result.error };
        return { data: URL.createObjectURL(result.data) };
      },
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
