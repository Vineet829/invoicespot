import { baseApiSlice } from "../api/baseApiSlice";

// Read every server page so the existing 5 / 10 / 25 / All table controls
// can operate on the complete list, using the same paginated endpoints.
export const fetchWorkspaceRows = async (
  baseQuery,
  path,
  rowsKey,
  pageKey = "page",
) => {
  const first = await baseQuery(`${path}?${pageKey}=1`);
  if (first.error) return first;
  const rows = [...(first.data[rowsKey] || [])];
  const pages = Math.max(1, Number(first.data.numberOfPages) || 1);
  for (let page = 2; page <= pages; page += 4) {
    const results = await Promise.all(
      Array.from({ length: Math.min(4, pages - page + 1) }, (_, offset) =>
        baseQuery(`${path}?${pageKey}=${page + offset}`),
      ),
    );
    const failure = results.find((result) => result.error);
    if (failure) return failure;
    results.forEach((result) => rows.push(...(result.data[rowsKey] || [])));
  }
  return {
    data: {
      ...first.data,
      [rowsKey]: [...new Map(rows.map((row) => [row._id, row])).values()],
    },
  };
};

export const workspaceApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getWorkspaceCustomers: builder.query({
      queryFn: (_arg, _api, _options, baseQuery) =>
        fetchWorkspaceRows(baseQuery, "/customer/all", "myCustomers"),
      providesTags: ["Customer"],
    }),
    uploadProfileLogo: builder.mutation({
      query: (body) => ({
        url: "/upload",
        method: "PATCH",
        body,
        responseHandler: "text",
      }),
    }),
  }),
});

export const { useGetWorkspaceCustomersQuery, useUploadProfileLogoMutation } =
  workspaceApiSlice;
