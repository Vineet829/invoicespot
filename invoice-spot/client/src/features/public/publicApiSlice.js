import { baseApiSlice } from "../api/baseApiSlice";
import { fetchPdf } from "../api/fetchPdf";

// A document shared by link. No sign-in: the link itself is the key.
export const publicApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSharedDocument: builder.query({
      query: (token) => `/public/share/${token}`,
    }),
    acceptSharedDocument: builder.mutation({
      query: (token) => ({
        url: `/public/share/${token}/accept`,
        method: "POST",
      }),
      async onQueryStarted(token, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(
            publicApiSlice.util.updateQueryData(
              "getSharedDocument",
              token,
              () => data,
            ),
          );
        } catch {
          // The page shows the error from the mutation itself.
        }
      },
    }),
    getSharedPdf: builder.mutation({
      queryFn: (token, _api, _extraOptions, baseQuery) =>
        fetchPdf(baseQuery, `/public/share/${token}/pdf`),
    }),
  }),
});

export const {
  useGetSharedDocumentQuery,
  useAcceptSharedDocumentMutation,
  useGetSharedPdfMutation,
} = publicApiSlice;
