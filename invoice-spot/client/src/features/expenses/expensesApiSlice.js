import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

export const expensesApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // One page of expenses; see expenseQuery for the search and filters.
    getExpenses: builder.query({
      query: (params = {}) => `/expense?${new URLSearchParams(params)}`,
      providesTags: ["Expense"],
    }),
    // The categories the business has used, to pick one again.
    getExpenseCategories: builder.query({
      query: () => "/expense/categories",
      providesTags: ["Expense"],
    }),
    createExpense: builder.mutation({
      query: (expense) => ({
        url: "/expense",
        method: "POST",
        body: expense,
      }),
      invalidatesTags: ["Expense"],
    }),
    updateExpense: builder.mutation({
      query: ({ id, ...changes }) => ({
        url: `/expense/${id}`,
        method: "PATCH",
        body: changes,
      }),
      invalidatesTags: ["Expense"],
    }),
    deleteExpense: builder.mutation({
      query: (id) => ({
        url: `/expense/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Expense"],
    }),
    getExpensesCsv: builder.mutation({
      queryFn: (filters, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/expense/csv?${new URLSearchParams(filters)}`),
    }),
  }),
});

export const {
  useGetExpensesQuery,
  useGetExpenseCategoriesQuery,
  useCreateExpenseMutation,
  useUpdateExpenseMutation,
  useDeleteExpenseMutation,
  useGetExpensesCsvMutation,
} = expensesApiSlice;
