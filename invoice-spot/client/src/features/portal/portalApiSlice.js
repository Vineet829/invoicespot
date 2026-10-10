import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

// A client's portal, opened by its link with no sign-in. Documents are named
// by their number.
const documentPath = (token, number, action = "") =>
  `/public/portal/${token}/document${action}?number=${encodeURIComponent(number)}`;

// The document page shows what an answer or report left, and the portal's
// list and balances are read again.
const showDocument = async (
  { token, number },
  { dispatch, queryFulfilled },
) => {
  try {
    const { data } = await queryFulfilled;
    dispatch(
      portalApiSlice.util.updateQueryData(
        "getPortalDocument",
        { token, number },
        () => data,
      ),
    );
  } catch {
    // The page shows the error from the mutation itself.
  }
};

export const portalApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getPortal: builder.query({
      query: ({ token, page = 1 }) =>
        `/public/portal/${token}?page=${page}&size=20`,
      providesTags: ["Portal"],
    }),
    getPortalDocument: builder.query({
      query: ({ token, number }) => documentPath(token, number),
    }),
    acceptPortalDocument: builder.mutation({
      query: ({ token, number }) => ({
        url: documentPath(token, number, "/accept"),
        method: "POST",
      }),
      onQueryStarted: showDocument,
      invalidatesTags: ["Portal"],
    }),
    declinePortalDocument: builder.mutation({
      query: ({ token, number, reason }) => ({
        url: documentPath(token, number, "/decline"),
        method: "POST",
        body: { reason },
      }),
      onQueryStarted: showDocument,
      invalidatesTags: ["Portal"],
    }),
    reportPortalPayment: builder.mutation({
      query: ({ token, number, ...report }) => ({
        url: documentPath(token, number, "/payment-claims"),
        method: "POST",
        body: report,
      }),
      onQueryStarted: showDocument,
      invalidatesTags: ["Portal"],
    }),
    getPortalPdf: builder.mutation({
      queryFn: ({ token, number }, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, documentPath(token, number, "/pdf")),
    }),
    getPortalStatement: builder.mutation({
      queryFn: (token, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/public/portal/${token}/statement`),
    }),
  }),
});

export const {
  useGetPortalQuery,
  useGetPortalDocumentQuery,
  useAcceptPortalDocumentMutation,
  useDeclinePortalDocumentMutation,
  useReportPortalPaymentMutation,
  useGetPortalPdfMutation,
  useGetPortalStatementMutation,
} = portalApiSlice;
