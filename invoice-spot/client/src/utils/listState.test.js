import { describe, expect, it } from "vitest";
import {
  nextSort,
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "./listState";

const FIELDS = {
  createdAt: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  name: { first: "asc", asc: "Name A–Z", desc: "Name Z–A" },
};
const read = (search) =>
  readSortAndPage(new URLSearchParams(search), FIELDS, "createdAt");
const write = (state) =>
  writeSortAndPage(
    new URLSearchParams(),
    state,
    FIELDS,
    "createdAt",
  ).toString();

describe("sort and page in the URL", () => {
  it("starts with the default sort, ten to a page", () => {
    expect(read("")).toEqual({
      sort: "createdAt",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("falls back to the defaults for unknown or malformed values", () => {
    expect(read("sort=password&dir=up&page=-2&size=7")).toEqual(read(""));
    expect(read("sort=toString").sort).toBe("createdAt");
    expect(read("page=1.5").page).toBe(1);
  });

  it("sorts a field in its natural direction when the URL names none", () => {
    expect(read("sort=name")).toMatchObject({ sort: "name", dir: "asc" });
  });

  it("writes only what differs from the defaults", () => {
    expect(write(read(""))).toBe("");
    expect(write(read("sort=createdAt&dir=asc&page=2&size=50"))).toBe(
      "sort=createdAt&dir=asc&page=2&size=50",
    );
  });
});

describe("sorting", () => {
  it("starts a new column in its natural direction, then reverses it", () => {
    expect(nextSort(FIELDS, read(""), "name")).toEqual({
      sort: "name",
      dir: "asc",
    });
    expect(nextSort(FIELDS, read("sort=name&dir=asc"), "name")).toEqual({
      sort: "name",
      dir: "desc",
    });
  });

  it("offers both directions of every field, the natural one first", () => {
    expect(sortOptions(FIELDS)).toEqual([
      { value: "createdAt:desc", label: "Newest first" },
      { value: "createdAt:asc", label: "Oldest first" },
      { value: "name:asc", label: "Name A–Z" },
      { value: "name:desc", label: "Name Z–A" },
    ]);
  });
});
