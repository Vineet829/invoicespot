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

it("keeps who was signed in, but not a token an older version saved", () => {
  localStorage.setItem(
    "user",
    JSON.stringify({
      username: "asha",
      firstName: "Asha",
      accessToken: expired,
    }),
  );
  expect(readSavedUser(localStorage)).toEqual({
    username: "asha",
    firstName: "Asha",
  });
  expect(JSON.parse(localStorage.getItem("user"))).toEqual({
    username: "asha",
    firstName: "Asha",
  });
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
