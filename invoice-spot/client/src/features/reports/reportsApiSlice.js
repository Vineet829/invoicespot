import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

// A report is read from documents, payments and client names, so any change
// to them refreshes it.
export const reportsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getReport: builder.query({
      query: (period) => `/report?${new URLSearchParams({ period })}`,
      providesTags: ["Document", "Customer"],
    }),
    getReportCsv: builder.mutation({
      queryFn: ({ section, period }, _api, _extraOptions, baseQuery) =>
        fetchFile(
          baseQuery,
          `/report/csv/${section}?${new URLSearchParams({ period })}`,
        ),
    }),
  }),
});

export const { useGetReportQuery, useGetReportCsvMutation } = reportsApiSlice;
