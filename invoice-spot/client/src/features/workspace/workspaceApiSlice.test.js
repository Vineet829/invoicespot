import { fetchWorkspaceRows } from "./workspaceApiSlice";

jest.mock("../api/baseApiSlice", () => ({
  baseApiSlice: { injectEndpoints: () => ({}) },
}));

it.each([
  ["/customer/all", "myCustomers", "page"],
  ["/user/all", "users", "pageNumber"],
])(
  "loads every page using the existing %s query parameter",
  async (path, key, pageKey) => {
    const request = jest.fn(async (url) => {
      const page = Number(url.split("=")[1]);
      return {
        data: { numberOfPages: 7, count: 7, [key]: [{ _id: `row-${page}` }] },
      };
    });
    const result = await fetchWorkspaceRows(request, path, key, pageKey);
    expect(result.data[key]).toHaveLength(7);
    expect(request.mock.calls.map(([url]) => url)).toEqual(
      Array.from({ length: 7 }, (_, i) => `${path}?${pageKey}=${i + 1}`),
    );
    expect(result.data.count).toBe(7);
  },
);

it("does not show a partial customer list when a later page fails", async () => {
  const error = { status: 503, data: { message: "Unavailable" } };
  const request = jest
    .fn()
    .mockResolvedValueOnce({
      data: { numberOfPages: 2, myCustomers: [{ _id: "one" }] },
    })
    .mockResolvedValueOnce({ error });
  expect(
    await fetchWorkspaceRows(request, "/customer/all", "myCustomers"),
  ).toEqual({ error });
});

it("removes duplicates when server pages overlap and handles an empty account", async () => {
  const request = jest
    .fn()
    .mockResolvedValueOnce({
      data: { numberOfPages: 2, users: [{ _id: "one" }] },
    })
    .mockResolvedValueOnce({
      data: { users: [{ _id: "one" }, { _id: "two" }] },
    });
  expect(
    (await fetchWorkspaceRows(request, "/user/all", "users", "pageNumber")).data
      .users,
  ).toEqual([{ _id: "one" }, { _id: "two" }]);
  expect(
    await fetchWorkspaceRows(
      async () => ({ data: { numberOfPages: 0, users: [] } }),
      "/user/all",
      "users",
      "pageNumber",
    ),
  ).toEqual({ data: { numberOfPages: 0, users: [] } });
});
