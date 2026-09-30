import { baseApiSlice } from "../api/baseApiSlice";

export const itemsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // Every item at once: a catalog is short, and the editor suggests from it.
    getItems: builder.query({
      query: () => "/item?size=500",
      providesTags: ["Item"],
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
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation,
} = itemsApiSlice;
