import { fetchDashboardDocuments } from "./dashboardApiSlice";

jest.mock("../api/baseApiSlice", () => ({
  baseApiSlice: {
    injectEndpoints: () => ({ useGetDashboardDocumentsQuery: jest.fn() }),
  },
}));

it("loads every document page before returning overview data", async () => {
  const request = jest.fn(async (url) => {
    const page = Number(url.split("=")[1]);
    return {
      data: {
        numberOfPages: 6,
        totalDocuments: 6,
        myDocuments: [{ _id: `document-${page}`, total: page * 100 }],
      },
    };
  });
  const result = await fetchDashboardDocuments(request);
  expect(result.data.myDocuments).toHaveLength(6);
  expect(
    result.data.myDocuments.reduce((sum, document) => sum + document.total, 0),
  ).toBe(2100);
  expect(result.data.totalDocuments).toBe(6);
  expect(request).toHaveBeenCalledTimes(6);
});

it("returns a later page error instead of misleading partial totals", async () => {
  const error = { status: 503, data: { message: "Unavailable" } };
  const request = jest
    .fn()
    .mockResolvedValueOnce({
      data: { numberOfPages: 2, myDocuments: [{ _id: "one" }] },
    })
    .mockResolvedValueOnce({ error });
  expect(await fetchDashboardDocuments(request)).toEqual({ error });
});

it("does not double-count documents repeated across pages", async () => {
  const request = jest
    .fn()
    .mockResolvedValueOnce({
      data: { numberOfPages: 2, myDocuments: [{ _id: "one" }] },
    })
    .mockResolvedValueOnce({
      data: { myDocuments: [{ _id: "one" }, { _id: "two" }] },
    });
  const result = await fetchDashboardDocuments(request);
  expect(result.data.myDocuments.map((document) => document._id)).toEqual([
    "one",
    "two",
  ]);
});
