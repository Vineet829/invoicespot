import { baseApiSlice } from "../api/baseApiSlice";

export const itemsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // Every item at once: a catalog is short, and the editor suggests from it.
    getItems: builder.query({
      query: () => "/item?size=500",
      providesTags: ["Item"],
    }),
    // What each item sold in a period and a currency, or the default one,
    // from the lines of what was billed, a page at a time. See
    // itemSalesQuery.
    getItemSales: builder.query({
      query: (params = {}) => `/item/sales?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    // One item's sales: its totals, who bought it, and its latest lines.
    getItemSalesDetail: builder.query({
      query: (params) => `/item/sales/detail?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    createItem: builder.mutation({
      query: (item) => ({ url: "/item", method: "POST", body: item }),
      invalidatesTags: ["Item"],
    }),
    updateItem: builder.mutation({
      query: ({ id, ...item }) => ({
        url: `/item/${id}`,
        method: "PATCH",
        body: item,
      }),
      invalidatesTags: ["Item"],
    }),
    deleteItem: builder.mutation({
      query: (id) => ({ url: `/item/${id}`, method: "DELETE" }),
      invalidatesTags: ["Item"],
    }),
  }),
});

export const {
  useGetItemsQuery,
  useGetItemSalesQuery,
  useGetItemSalesDetailQuery,
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation,
} = itemsApiSlice;
