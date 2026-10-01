import { beforeEach, expect, it } from "vitest";
import { readSavedUser, tokenRoles } from "./savedSession";

const encode = (value) =>
  btoa(JSON.stringify(value))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
const token = (claims) =>
  `${encode({ alg: "HS256" })}.${encode(claims)}.signature`;
const expired = token({ sub: "u1", roles: ["User"], exp: 1 });

beforeEach(() => localStorage.clear());

it("keeps a saved session whose token can be read, even an expired one, which a refresh renews", () => {
  localStorage.setItem("user", JSON.stringify({ accessToken: expired }));
  expect(readSavedUser(localStorage)).toEqual({ accessToken: expired });
  expect(tokenRoles(expired)).toEqual(["User"]);
});

it("drops a saved session that isn't one, so the app starts signed out", () => {
  for (const saved of [
    "{not json",
    '"a string"',
    "[]",
    '{"accessToken": "not-a-token"}',
    "{}",
  ]) {
    localStorage.setItem("user", saved);
    expect(readSavedUser(localStorage)).toBeNull();
    expect(localStorage.getItem("user")).toBeNull();
  }
  expect(readSavedUser(localStorage)).toBeNull();
});

it("reads no roles from a token that can't be read", () => {
  expect(tokenRoles("not-a-token")).toEqual([]);
  expect(tokenRoles(undefined)).toEqual([]);
  expect(tokenRoles(token({ sub: "u1" }))).toEqual([]);
});
