import { baseApiSlice } from "../api/baseApiSlice";

export const auditApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getAuditLog: builder.query({
      query: ({ q, type, page, size }) =>
        `/admin/audit?${new URLSearchParams({
          page,
          size,
          ...(q && { q }),
          ...(type && { type }),
        })}`,
      providesTags: ["Activity"],
    }),
  }),
});

export const { useGetAuditLogQuery } = auditApiSlice;
