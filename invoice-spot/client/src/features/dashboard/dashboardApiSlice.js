import { baseApiSlice } from "../api/baseApiSlice";

// The document list is paginated. An overview must include every page before
// presenting totals, and must not present incomplete totals if a page fails.
export const fetchDashboardDocuments = async (baseQuery) => {
  const first = await baseQuery("/document/all?page=1");
  if (first.error) return { error: first.error };
  const documents = [...(first.data.myDocuments || [])];
  const pageCount = Math.max(1, Number(first.data.numberOfPages) || 1);
  for (let page = 2; page <= pageCount; page += 4) {
    const results = await Promise.all(
      Array.from({ length: Math.min(4, pageCount - page + 1) }, (_, index) =>
        baseQuery(`/document/all?page=${page + index}`),
      ),
    );
    const failed = results.find((result) => result.error);
    if (failed) return { error: failed.error };
    results.forEach((result) =>
      documents.push(...(result.data.myDocuments || [])),
    );
  }
  const uniqueDocuments = [
    ...new Map(documents.map((document) => [document._id, document])).values(),
  ];
  return {
    data: {
      myDocuments: uniqueDocuments,
      totalDocuments: first.data.totalDocuments ?? uniqueDocuments.length,
    },
  };
};

export const dashboardApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getDashboardDocuments: builder.query({
      queryFn: (_arg, _api, _extraOptions, baseQuery) =>
        fetchDashboardDocuments(baseQuery),
      providesTags: ["Document"],
    }),
  }),
});

export const { useGetDashboardDocumentsQuery } = dashboardApiSlice;
