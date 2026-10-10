import { describe, expect, it } from "vitest";
import {
  SORT_OPTIONS,
  clientListSearch,
  clientFilterQuery,
  clientQuery,
  readClientList,
} from "./clientFilters";

const read = (search) => readClientList(new URLSearchParams(search));

describe("the clients list in the URL", () => {
  it("starts with every client, newest first, ten to a page", () => {
    expect(read("")).toEqual({
      q: "",
      sort: "createdAt",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("keeps a search, sort and page, and writes only what differs", () => {
    const search = "q=north&sort=balance&dir=desc&page=2&size=25";
    expect(clientListSearch(read(search)).toString()).toBe(search);
    expect(clientListSearch(read("")).toString()).toBe("");
    expect(read("sort=email").sort).toBe("createdAt");
  });

  it("asks the server for the same page, sort and search", () => {
    expect(clientQuery(read("q=north&sort=documents"))).toEqual({
      page: 1,
      size: 10,
      sort: "documents",
      dir: "desc",
      q: "north",
    });
    expect(clientQuery(read(""))).not.toHaveProperty("q");
    expect(clientFilterQuery(read("q=north&sort=name&dir=asc&page=3"))).toEqual(
      {
        sort: "name",
        dir: "asc",
        q: "north",
      },
    );
  });

  it("sorts balances and document counts largest first", () => {
    expect(SORT_OPTIONS.map((option) => option.value)).toEqual([
      "createdAt:desc",
      "createdAt:asc",
      "name:asc",
      "name:desc",
      "balance:desc",
      "balance:asc",
      "documents:desc",
      "documents:asc",
    ]);
  });
});
