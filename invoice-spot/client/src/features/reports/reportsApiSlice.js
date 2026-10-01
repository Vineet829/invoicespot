import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

// A report is read from documents, payments and client names, so any change
// to them refreshes it.
export const reportsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // By currency, or with view "base" in the reporting currency.
    getReport: builder.query({
      query: ({ period, view }) =>
        `/report?${new URLSearchParams({ period, ...(view && { view }) })}`,
      providesTags: ["Document", "Customer"],
    }),
    // The tax charged at each rate, by currency or in the reporting one.
    getTaxReport: builder.query({
      query: ({ period, view }) =>
        `/report/tax?${new URLSearchParams({ period, ...(view && { view }) })}`,
      providesTags: ["Document", "Customer"],
    }),
    getTdsReport: builder.query({
      query: (period) => `/report/tds?${new URLSearchParams({ period })}`,
      providesTags: ["Document", "Customer"],
    }),
    // The GST helper files for some months, as "2026-09".
    getGstReturns: builder.query({
      query: ({ from, to }) =>
        `/report/gst?${new URLSearchParams({ from, to })}`,
      providesTags: ["Document", "Customer"],
    }),
    getGstCsv: builder.mutation({
      queryFn: ({ section, from, to }, _api, _extraOptions, baseQuery) =>
        fetchFile(
          baseQuery,
          `/report/gst/${section}/csv?${new URLSearchParams({ from, to })}`,
        ),
    }),
    getReportCsv: builder.mutation({
      queryFn: ({ section, period, view }, _api, _extraOptions, baseQuery) =>
        fetchFile(
          baseQuery,
          `/report/csv/${section}?${new URLSearchParams({ period, ...(view && { view }) })}`,
        ),
    }),
  }),
});

export const {
  useGetReportQuery,
  useGetTaxReportQuery,
  useGetTdsReportQuery,
  useGetReportCsvMutation,
  useGetGstReturnsQuery,
  useGetGstCsvMutation,
} = reportsApiSlice;
