import { beforeEach, expect, it, vi } from "vitest";
import { fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { baseApiSlice } from "./baseApiSlice";
import { logIn, logOut } from "../auth/authSlice";

vi.mock("@reduxjs/toolkit/query/react", () => ({
  fetchBaseQuery: vi.fn(() => vi.fn()),
  createApi: (options) => options,
}));

const request = fetchBaseQuery.mock.results[0].value;
let api;

beforeEach(() => {
  request.mockReset();
  let user = { accessToken: "expired" };
  api = {
    getState: () => ({ auth: { user } }),
    dispatch: vi.fn((action) => {
      if (action.type === logIn.type) user = action.payload;
      if (action.type === logOut.type) user = null;
    }),
  };
});

it.each([
  { status: 403, data: null },
  { status: "PARSING_ERROR", originalStatus: 403 },
  { status: "PARSING_ERROR", originalStatus: 403, data: "" },
])("refreshes and retries a rejected token: %j", async (error) => {
  request
    .mockResolvedValueOnce({ error })
    .mockResolvedValueOnce({ data: { accessToken: "renewed" } })
    .mockResolvedValueOnce({ data: { customers: [] } });

  const result = await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(result).toEqual({ data: { customers: [] } });
  expect(api.getState().auth.user.accessToken).toBe("renewed");
  expect(request.mock.calls.map(([path]) => path)).toEqual([
    "/customer/all",
    "/auth/new_access_token",
    "/customer/all",
  ]);
});

it("shares one cookie rotation between concurrent dashboard requests", async () => {
  request.mockImplementation(async (path) => {
    if (path === "/auth/new_access_token") {
      return { data: { accessToken: "renewed" } };
    }
    return api.getState().auth.user.accessToken === "expired"
      ? { error: { status: 403 } }
      : { data: { resource: path } };
  });

  const results = await Promise.all([
    baseApiSlice.baseQuery("/customer/all", api, {}),
    baseApiSlice.baseQuery("/document/all", api, {}),
  ]);

  expect(results).toEqual([
    { data: { resource: "/customer/all" } },
    { data: { resource: "/document/all" } },
  ]);
  expect(
    request.mock.calls.filter(([path]) => path === "/auth/new_access_token"),
  ).toHaveLength(1);
  expect(api.dispatch).toHaveBeenCalledTimes(1);
});

it("clears the session when the refresh cookie is rejected", async () => {
  request
    .mockResolvedValueOnce({ error: { status: 403 } })
    .mockResolvedValueOnce({ error: { status: 401 } });

  const result = await baseApiSlice.baseQuery("/customer/all", api, {});

  expect(result.error.status).toBe(403);
  expect(api.getState().auth.user).toBeNull();
  expect(request).toHaveBeenCalledTimes(2);
});

it("returns an authentication error without retrying through another session", async () => {
  request.mockResolvedValue({
    error: { status: 403, data: { message: "Login failed" } },
  });

  await baseApiSlice.baseQuery({ url: "/auth/login", method: "POST" }, api, {});

  expect(request).toHaveBeenCalledTimes(1);
  expect(api.dispatch).not.toHaveBeenCalled();
});

it("returns a refusal with a message as it is, without a refresh", async () => {
  const refusal = {
    status: 403,
    data: {
      success: false,
      message: "You are not authorized to view this document. It's not yours",
    },
  };
  request.mockResolvedValue({ error: refusal });

  const result = await baseApiSlice.baseQuery(
    "/document/someone-elses",
    api,
    {},
  );

  expect(result).toEqual({ error: refusal });
  expect(request).toHaveBeenCalledTimes(1);
  expect(api.dispatch).not.toHaveBeenCalled();
  expect(api.getState().auth.user.accessToken).toBe("expired");
});
