import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

export const settingsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSettings: builder.query({
      query: () => "/settings",
      providesTags: ["Settings"],
    }),
    updateSettings: builder.mutation({
      query: (settings) => ({
        url: "/settings",
        method: "PUT",
        body: settings,
      }),
      invalidatesTags: ["Settings"],
    }),
    getDataExport: builder.mutation({
      queryFn: (_arg, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, "/export/all"),
    }),
  }),
});

export const {
  useGetDataExportMutation,
  useGetSettingsQuery,
  useUpdateSettingsMutation,
} = settingsApiSlice;
