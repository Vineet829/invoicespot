import { describe, expect, it } from "vitest";
import {
  AGE_BANDS,
  attentionDocuments,
  attentionFooter,
  attentionItems,
  attentionRow,
  axisLabel,
  billedLink,
  bucketLabel,
  bucketRange,
  cashFlowScale,
  changeFrom,
  dateSpan,
  findPeriod,
  formatMoney,
  invoiceCount,
  labelStep,
  moneyOutLink,
  owedBands,
  percentLabel,
  periodSubtitle,
  quotationRows,
  quotationsLink,
  receivablesLink,
  receivedLink,
  recurringLink,
  reportLink,
  runRows,
  salesByCustomerLink,
  salesByItemLink,
  showsLabel,
  topClientRows,
  topItemRows,
  trendColumns,
  trendPoints,
  wholeAmount,
} from "./dashboardModel";

it("formats amounts safely", () => {
  expect(formatMoney(undefined)).toBe("0.00");
  expect(formatMoney(1234.5)).toBe("1,234.50");
});

describe("periods", () => {
  it("reads a period from the address, and the last 90 days otherwise", () => {
    expect(findPeriod("12m").name).toBe("Last 12 months");
    expect(findPeriod("ytd").compared).toBe("vs same period last year");
    expect(findPeriod(null).key).toBe("90d");
    expect(findPeriod("7d").key).toBe("90d");
  });

  it.each([
    ["2026-07-07", "2026-10-04", true, "Jul 7 – Oct 4, 2026"],
    ["2026-07-07", "2026-10-04", false, "Jul 7 – Oct 4"],
    ["2025-11-01", "2026-10-04", true, "Nov 1, 2025 – Oct 4, 2026"],
    ["2025-12-06", "2026-01-04", false, "Dec 6, 2025 – Jan 4, 2026"],
  ])("writes %s to %s as one span", (from, to, withYear, text) => {
    expect(dateSpan(from, to, withYear)).toBe(text);
  });

  it.each([
    [
      {
        key: "90d",
        from: "2026-07-07",
        to: "2026-10-04",
        previousFrom: "2026-04-08",
        previousTo: "2026-07-06",
      },
      "Last 90 days, Jul 7 – Oct 4, 2026 · compared with Apr 8 – Jul 6",
    ],
    [
      {
        key: "30d",
        from: "2026-09-05",
        to: "2026-10-04",
        previousFrom: "2026-08-06",
        previousTo: "2026-09-04",
      },
      "Last 30 days, Sep 5 – Oct 4, 2026 · compared with Aug 6 – Sep 4",
    ],
    [
      {
        key: "12m",
        from: "2025-11-01",
        to: "2026-10-04",
        previousFrom: "2024-11-01",
        previousTo: "2025-10-31",
      },
      "Last 12 months, Nov 1, 2025 – Oct 4, 2026 · compared with Nov 1, 2024 – Oct 31, 2025",
    ],
    [
      {
        key: "ytd",
        from: "2026-01-01",
        to: "2026-10-04",
        previousFrom: "2025-01-01",
        previousTo: "2025-10-04",
      },
      "This year, Jan 1 – Oct 4, 2026 · compared with Jan 1 – Oct 4, 2025",
    ],
  ])("names the period and the one it is compared with", (period, text) => {
    expect(periodSubtitle(period, true)).toBe(text);
  });

  it("leaves the comparison out when nothing came before the period", () => {
    expect(
      periodSubtitle(
        {
          key: "90d",
          from: "2026-07-07",
          to: "2026-10-04",
          previousFrom: "2026-04-08",
          previousTo: "2026-07-06",
        },
        false,
      ),
    ).toBe("Last 90 days, Jul 7 – Oct 4, 2026");
  });
});

describe("links to the Reports page", () => {
  it("carries the period, and a chosen currency, which Reports also has", () => {
    expect(reportLink("income", "ytd", "EUR")).toBe(
      "/reports?period=ytd&currency=EUR#income",
    );
    expect(reportLink("profit", "ytd")).toBe("/reports?period=ytd#profit");
    expect(reportLink("profit", "30d")).toBe("/reports?period=30d#profit");
    expect(reportLink("profit", "90d", "INR")).toBe(
      "/reports?period=90d&currency=INR#profit",
    );
  });

  it("names no period for a section that is as of today", () => {
    expect(reportLink("ageing", null)).toBe("/reports#ageing");
    expect(reportLink("ageing", null, "EUR")).toBe(
      "/reports?currency=EUR#ageing",
    );
  });

  it("leaves out its default 12 months", () => {
    expect(reportLink("ageing", "12m")).toBe("/reports#ageing");
    expect(reportLink("profit", "12m", "EUR")).toBe(
      "/reports?currency=EUR#profit",
    );
  });
});

describe("links to what is owed today", () => {
  it("opens what is coming in or what is overdue on the Receivables view, in a chosen currency", () => {
    expect(receivablesLink()).toBe("/receivables");
    expect(receivablesLink(undefined, "EUR")).toBe("/receivables?currency=EUR");
    expect(receivablesLink("overdue")).toBe("/receivables?mode=overdue");
    expect(receivablesLink("overdue", "EUR")).toBe(
      "/receivables?mode=overdue&currency=EUR",
    );
  });

  it("writes them as the Receivables view writes its own address", async () => {
    const { readReceivables, receivablesSearch } =
      await import("../documents/receivablesModel");
    for (const link of [
      receivablesLink(),
      receivablesLink(undefined, "EUR"),
      receivablesLink("overdue", "EUR"),
    ]) {
      const search = link.split("?")[1] || "";
      expect(
        receivablesSearch(
          readReceivables(new URLSearchParams(search)),
        ).toString(),
      ).toBe(search);
    }
  });
});

describe("what is owed by days late", () => {
  it("names the server's overdue bands in order, each in a deeper rust", () => {
    expect(
      AGE_BANDS.map(({ key, label, color }) => [key, label, color]),
    ).toEqual([
      ["days1to15", "1–15 days", "bg-owed-age-1"],
      ["days16to30", "16–30 days", "bg-owed-age-2"],
      ["days31to45", "31–45 days", "bg-owed-age-3"],
      ["over45", "Over 45 days", "bg-owed-age-4"],
    ]);
  });

  it("reads the bands by key, and none from an older server", () => {
    const current = { key: "current", amount: 10, invoices: 1 };
    const late = { key: "over45", amount: 5, invoices: 2 };
    expect(owedBands({ bands: [current, late] })).toEqual({
      current,
      over45: late,
    });
    expect(owedBands({ total: 15, notDue: 10, overdue: 5 })).toBeNull();
    expect(owedBands()).toBeNull();
  });
});

describe("links to the records behind each figure", () => {
  it.each([
    ["30d", "30d"],
    ["90d", "90d"],
    ["12m", "12m"],
    ["ytd", "this-year"],
  ])(
    "opens what was billed and received over %s as the views' %s",
    (period, list) => {
      expect(billedLink(period)).toBe(`/sales?period=${list}`);
      expect(billedLink(period, "EUR")).toBe(
        `/sales?period=${list}&currency=EUR`,
      );
      expect(receivedLink(period)).toBe(`/payments?period=${list}`);
      expect(receivedLink(period, "EUR")).toBe(
        `/payments?period=${list}&currency=EUR`,
      );
    },
  );

  it.each([
    ["30d", "30d"],
    ["90d", "90d"],
    ["12m", "12m"],
    ["ytd", "this-year"],
  ])("opens every client's sales over %s as the view's %s", (period, list) => {
    expect(salesByCustomerLink(period)).toBe(`/customers/sales?period=${list}`);
    expect(salesByCustomerLink(period, "EUR")).toBe(
      `/customers/sales?period=${list}&currency=EUR`,
    );
  });

  it("writes the Sales view's address as it writes it itself", async () => {
    const { readSales, salesSearch } = await import("../documents/salesModel");
    for (const period of ["30d", "90d", "12m", "ytd"]) {
      const search = billedLink(period, "EUR").split("?")[1];
      expect(
        salesSearch(readSales(new URLSearchParams(search))).toString(),
      ).toBe(search);
    }
  });
});

it.each([
  ["30d", "30d"],
  ["90d", "90d"],
  ["12m", "12m"],
  ["ytd", "this-year"],
])(
  "opens the expenses behind money out over %s as the list's %s, in the overview's currency",
  (period, list) => {
    expect(moneyOutLink(period, "USD")).toBe(
      `/expenses?period=${list}&currency=USD`,
    );
  },
);

describe("the top clients", () => {
  it("measures each bar against the largest and opens a linked client", () => {
    expect(
      topClientRows([
        {
          clientId: "c-1",
          client: "Quill",
          billed: 200,
          owed: 0,
          documents: 2,
        },
        {
          clientId: null,
          client: "Verdant",
          billed: 50,
          owed: 10,
          documents: 1,
        },
        {
          clientId: "c-3",
          client: "Atlas",
          billed: -20,
          owed: 5,
          documents: 1,
        },
      ]),
    ).toEqual([
      {
        clientId: "c-1",
        client: "Quill",
        billed: 200,
        owed: 0,
        documents: 2,
        bar: 1,
        to: "/single-customer/c-1",
      },
      {
        clientId: null,
        client: "Verdant",
        billed: 50,
        owed: 10,
        documents: 1,
        bar: 0.25,
        to: null,
      },
      {
        clientId: "c-3",
        client: "Atlas",
        billed: -20,
        owed: 5,
        documents: 1,
        bar: 0,
        to: "/single-customer/c-3",
      },
    ]);
  });

  it("has no rows when no one was billed", () => {
    expect(topClientRows([])).toEqual([]);
    expect(topClientRows()).toEqual([]);
  });
});

describe("the top items", () => {
  it("measures each bar against the largest, shares out the income, and opens each item's sales", () => {
    const rows = topItemRows(
      [
        { item: "Motion graphics", quantity: 28, amount: 400, documents: 6 },
        { item: " Logo ", quantity: 3, amount: 100, documents: 3 },
        { item: "Stickers", quantity: 500, amount: 2, documents: 1 },
      ],
      1000,
      "90d",
    );
    expect(
      rows.map(({ name, bar, share, to }) => [name, bar, share, to]),
    ).toEqual([
      [
        "Motion graphics",
        1,
        "40%",
        "/items/sales/item?name=Motion+graphics&period=90d",
      ],
      ["Logo", 0.25, "10%", "/items/sales/item?name=Logo&period=90d"],
      ["Stickers", 0.005, "<1%", "/items/sales/item?name=Stickers&period=90d"],
    ]);
  });

  it("names lines without a name other lines, which open their own page", () => {
    expect(
      topItemRows(
        [
          { item: null, quantity: 1, amount: 50 },
          { item: "", quantity: 1, amount: 40 },
          { item: "Other lines", quantity: 1, amount: 30 },
        ],
        200,
        "ytd",
        "EUR",
      ).map(({ name, to }) => [name, to]),
    ).toEqual(
      Array(3).fill([
        "Other lines",
        "/items/sales/item?name=Other+lines&period=this-year&currency=EUR",
      ]),
    );
  });

  it("shares out nothing without income, and draws no bar below zero", () => {
    expect(
      topItemRows([{ item: "Refund", quantity: -1, amount: -80 }], 0).map(
        ({ bar, share }) => [bar, share],
      ),
    ).toEqual([[0, null]]);
    expect(topItemRows([], 0)).toEqual([]);
    expect(topItemRows()).toEqual([]);
  });

  it.each([
    ["30d", "30d"],
    ["90d", "90d"],
    ["12m", "12m"],
    ["ytd", "this-year"],
  ])("opens every item's sales over %s as the view's %s", (period, list) => {
    expect(salesByItemLink(period)).toBe(`/items/sales?period=${list}`);
    expect(salesByItemLink(period, "EUR")).toBe(
      `/items/sales?period=${list}&currency=EUR`,
    );
  });
});

describe("what is coming up", () => {
  it("names each schedule's next run and template, and opens the invoice it repeats", () => {
    expect(
      runRows([
        {
          scheduleId: "s-2",
          templateId: "t-2",
          documentNumber: null,
          client: "Bluefin",
          date: "2027-01-01",
        },
        {
          scheduleId: "s-1",
          templateId: "t-1",
          documentNumber: "INV-1012",
          client: "Harbor",
          date: "2026-10-12",
        },
        {
          scheduleId: "s-1",
          templateId: "t-1",
          documentNumber: "INV-1012",
          client: "Harbor",
          date: "2026-11-12",
        },
      ]).map(({ day, number, to }) => [day, number, to]),
    ).toEqual([
      ["Oct 12", "INV-1012", "/document/t-1"],
      ["Jan 1", "Draft invoice", "/document/t-2"],
    ]);
    expect(runRows()).toEqual([]);
    expect(recurringLink).toBe("/recurring");
  });

  it("leaves a schedule's invoice to what needs attention when it opens it already", () => {
    const opened = attentionDocuments([
      { kind: "overdue", documentId: "t-1" },
      { kind: "recurring", documentId: "t-2" },
    ]);
    expect([...opened]).toEqual(["t-1"]);
    expect(
      runRows(
        [
          { scheduleId: "s-1", templateId: "t-1", date: "2026-10-12" },
          { scheduleId: "s-2", templateId: "t-2", date: "2026-10-20" },
        ],
        opened,
      ).map(({ to }) => to),
    ).toEqual([null, "/document/t-2"]);
    expect([...attentionDocuments()]).toEqual([]);
  });

  it("opens each quotation waiting for an answer, named with its last valid day, or as having none", () => {
    expect(
      quotationRows([
        { documentId: "q-1", validUntil: "2026-10-09", days: 5 },
        { documentId: "q-2", validUntil: null, days: null },
      ]).map(({ to, until }) => [to, until]),
    ).toEqual([
      ["/document/q-1", "valid until Oct 9"],
      ["/document/q-2", "No expiry"],
    ]);
    expect(quotationRows()).toEqual([]);
  });

  it("opens each quotation that expired without an answer, named with its last valid day", () => {
    expect(
      quotationRows([
        {
          documentId: "q-3",
          documentNumber: "QUO-0205",
          validUntil: "2026-08-24",
        },
      ]),
    ).toEqual([
      {
        documentId: "q-3",
        documentNumber: "QUO-0205",
        validUntil: "2026-08-24",
        until: "valid until Aug 24",
        to: "/document/q-3",
      },
    ]);
  });

  it("opens the quotations in a chosen currency", () => {
    expect(quotationsLink()).toBe("/documents?type=quotation");
    expect(quotationsLink("EUR")).toBe(
      "/documents?type=quotation&currency=EUR",
    );
  });
});

it("counts invoices", () => {
  expect(invoiceCount(1)).toBe("1 invoice");
  expect(invoiceCount(0)).toBe("0 invoices");
  expect(invoiceCount(1200)).toBe("1,200 invoices");
});

describe("changes against the period before", () => {
  it.each([
    [68848.08, 53344.02, { direction: "up", percent: 29.1 }],
    [44562.29, 59864.82, { direction: "down", percent: 25.6 }],
    [100, 100, { direction: "flat", percent: 0 }],
    [100.01, 100, { direction: "flat", percent: 0 }],
    [0, 250, { direction: "down", percent: 100 }],
    [500, -1000, { direction: "up", percent: 150 }],
    [-2000, -1000, { direction: "down", percent: 100 }],
  ])("compares %s with %s", (current, previous, change) => {
    expect(changeFrom(current, previous)).toEqual(change);
  });

  it.each([
    [100, 0],
    [0, 0],
    [100, undefined],
    [NaN, 100],
  ])(
    "has nothing to compare %s with when the earlier figure is %s",
    (current, previous) => {
      expect(changeFrom(current, previous)).toBeNull();
    },
  );

  it("writes a change to one decimal", () => {
    expect(percentLabel(29.1)).toBe("29.1%");
    expect(percentLabel(100)).toBe("100.0%");
    expect(percentLabel(12345.6)).toBe("12,345.6%");
  });
});

describe("tile trends", () => {
  it("draws a line from zero up to a tenth above the highest value", () => {
    expect(trendPoints([0, 50, 100])).toEqual([
      { x: 0, y: 100 },
      { x: 50, y: expect.closeTo(54.55, 2) },
      { x: 100, y: expect.closeTo(9.09, 2) },
    ]);
  });

  it("keeps a line below zero inside the box, and draws flat or lone values", () => {
    const points = trendPoints([-50, 100]);
    expect(points[0].y).toBe(100);
    expect(points[1].y).toBeCloseTo(6.25, 2);
    expect(trendPoints([0, 0]).map((point) => point.y)).toEqual([100, 100]);
    expect(trendPoints([40])).toEqual([{ x: 50, y: expect.closeTo(9.09, 2) }]);
  });

  it("stands profit on zero and hangs a loss below it", () => {
    expect(trendColumns([0, 50, 100])).toEqual({
      zero: 100,
      columns: [
        { value: 0, top: 100, height: 0 },
        { value: 50, top: 50, height: 50 },
        { value: 100, top: 0, height: 100 },
      ],
    });
    expect(trendColumns([300, -100])).toEqual({
      zero: 75,
      columns: [
        { value: 300, top: 0, height: 75 },
        { value: -100, top: 75, height: 25 },
      ],
    });
    expect(trendColumns([0, 0]).columns.map((column) => column.height)).toEqual(
      [0, 0],
    );
  });
});

describe("the cash flow chart", () => {
  const part = (moneyIn, moneyOut = 0) => ({ moneyIn, moneyOut });

  it("puts round steps above and below zero, money in taking most of the height", () => {
    const scale = cashFlowScale([part(0), part(4332.9), part(17256.86)]);
    expect(scale.ticks).toEqual([20000, 15000, 10000, 5000, 0, -5000]);
    expect(scale.at(20000)).toBe(0);
    expect(scale.at(0)).toBe(80);
    expect(scale.at(-5000)).toBe(100);
  });

  it("gives money out as many steps as it needs, and money in at least one", () => {
    expect(cashFlowScale([part(12000, 3000)]).ticks).toEqual([
      15000, 10000, 5000, 0, -5000,
    ]);
    expect(cashFlowScale([part(1000, 9000)]).ticks).toEqual([
      2500, 0, -2500, -5000, -7500, -10000,
    ]);
    expect(cashFlowScale([part(0, 40)]).ticks).toEqual([
      10, 0, -10, -20, -30, -40,
    ]);
  });

  it("keeps small amounts in whole steps", () => {
    expect(cashFlowScale([part(3)]).ticks).toEqual([3, 2, 1, 0, -1]);
  });

  it("has no scale when nothing came in or went out", () => {
    expect(cashFlowScale([part(0), part(0)])).toBeNull();
    expect(cashFlowScale([])).toBeNull();
  });

  it("writes axis figures in whole amounts, shortened from a hundred thousand", () => {
    const ticks = [20000, 0, -5000];
    expect(ticks.map((tick) => axisLabel(tick, ticks))).toEqual([
      "20,000",
      "0",
      "−5,000",
    ]);
    expect(axisLabel(250000, [250000, -50000])).toBe("250K");
    expect(axisLabel(-50000, [250000, -50000])).toBe("−50K");
    expect(axisLabel(200000, [200000, -100000], "INR")).toBe("2L");
  });

  it("rounds amounts to whole units", () => {
    expect(wholeAmount(6326.03)).toBe("6,326");
    expect(wholeAmount(999.5)).toBe("1,000");
    expect(wholeAmount(123456, "INR")).toBe("1,23,456");
  });

  it.each([
    [{ from: "2026-09-05", to: "2026-09-05" }, "day", "Sep 5", "Sep 5"],
    [{ from: "2026-07-07", to: "2026-07-13" }, "week", "Jul 7", "Jul 7–13"],
    [
      { from: "2026-07-28", to: "2026-08-03" },
      "week",
      "Jul 28",
      "Jul 28–Aug 3",
    ],
    [
      { from: "2025-12-29", to: "2026-01-04" },
      "week",
      "Dec 29",
      "Dec 29, 2025 – Jan 4, 2026",
    ],
    [{ from: "2025-11-01", to: "2025-11-30" }, "month", "Nov", "November 2025"],
    [{ from: "2026-02-01", to: "2026-02-28" }, "month", "Feb", "February 2026"],
    [{ from: "2026-10-01", to: "2026-10-04" }, "month", "Oct", "Oct 1–4, 2026"],
  ])("names %j by its %s", (bucket, step, label, range) => {
    expect(bucketLabel(bucket, step)).toBe(label);
    expect(bucketRange(bucket, step)).toBe(range);
  });

  it("names fewer columns as they get narrower, always keeping the latest", () => {
    expect(labelStep(13, 800, 46)).toBe(1);
    expect(labelStep(13, 270, 46)).toBe(3);
    expect(labelStep(30, 813, 46)).toBe(2);
    expect(labelStep(12, 270, 36)).toBe(2);
    expect(labelStep(30, 0, 46)).toBe(30);
    const named = Array.from({ length: 13 }, (_, index) => index).filter(
      (index) => showsLabel(index, 13, 3),
    );
    expect(named).toEqual([0, 3, 6, 9, 12]);
  });
});

describe("what needs attention", () => {
  const item = (kind, days, date, extra) => ({
    kind,
    documentId: "d1",
    documentNumber: "INV-1",
    client: "Client",
    amount: 10,
    date,
    days,
    ...extra,
  });

  it.each([
    [
      item("overdue", 54, "2026-08-11"),
      "Overdue",
      "54 days late",
      "Due Aug 11",
    ],
    [item("overdue", 1, "2026-10-03"), "Overdue", "1 day late", "Due Oct 3"],
    [
      item("dueSoon", 2, "2026-10-06"),
      "Due soon",
      "Due in 2 days",
      "Due Oct 6",
    ],
    [item("dueSoon", 1, "2026-10-05"), "Due soon", "Due tomorrow", "Due Oct 5"],
    [item("dueSoon", 0, "2026-10-04"), "Due soon", "Due today", "Due Oct 4"],
    [
      item("paymentReport", null, "2026-10-02"),
      "To confirm",
      "Payment reported",
      "Paid on Oct 2",
    ],
    [
      item("draft", null, "2026-10-01", { documentNumber: null }),
      "To issue",
      "Draft",
      "Created Oct 1",
    ],
  ])("words %j", (entry, label, state, when) => {
    expect(attentionRow(entry)).toMatchObject({ label, state, when });
  });

  it.each(["overdue", "dueSoon"])(
    "gives only the day for %s when the number of days is missing",
    (kind) => {
      const row = attentionRow(item(kind, null, "2026-10-07"));
      expect(row.state).toBe("");
      expect(row.when).toMatch(/Oct 7$/);
      expect(attentionRow(item(kind, undefined, "2026-10-07")).state).toBe("");
    },
  );

  it("opens each item's document, and never its client", () => {
    for (const kind of ["paymentReport", "overdue", "dueSoon", "draft"]) {
      const row = attentionRow(item(kind, 3, "2026-10-01", { clientId: "c1" }));
      expect(row.to).toBe("/document/d1");
      expect(row).not.toHaveProperty("clientTo");
    }
  });

  it("lists invoices and drafts, leaving quotations and recurring invoices to Coming up", () => {
    const items = [
      item("paymentReport", null, "2026-10-02", { documentId: "r" }),
      item("overdue", 3, "2026-10-01", { documentId: "o" }),
      item("quotation", 5, "2026-10-09", { documentId: "q" }),
      item("dueSoon", 1, "2026-10-05", { documentId: "s" }),
      item("recurring", 3, "2026-10-07", { documentId: "t" }),
      item("draft", null, "2026-10-01", { documentId: "d" }),
    ];
    expect(attentionItems({ items }).map((entry) => entry.kind)).toEqual([
      "paymentReport",
      "overdue",
      "dueSoon",
      "draft",
    ]);
    expect([...attentionDocuments(items)]).toEqual(["r", "o", "s", "d"]);
    expect(attentionItems()).toEqual([]);
    expect(attentionItems({})).toEqual([]);
  });

  it("names the kinds there is nothing of", () => {
    const counts = { paymentReports: 1, overdue: 1, dueSoon: 1, drafts: 1 };
    const items = ["paymentReport", "overdue", "dueSoon", "draft"].map((kind) =>
      item(kind, 1, "2026-10-05"),
    );
    const only = (...kinds) =>
      items.filter((entry) => kinds.includes(entry.kind));
    expect(
      attentionFooter(
        { ...counts, paymentReports: 0, drafts: 0 },
        only("overdue", "dueSoon"),
      ),
    ).toBe("No payment reports to confirm or drafts to issue.");
    expect(
      attentionFooter(
        { ...counts, paymentReports: 0, overdue: 0 },
        only("dueSoon", "draft"),
      ),
    ).toBe("No payment reports to confirm or overdue invoices.");
    expect(
      attentionFooter(
        { ...counts, dueSoon: 0 },
        only("paymentReport", "overdue", "draft"),
      ),
    ).toBe("No invoices due this week.");
    expect(attentionFooter(counts, items)).toBeNull();
  });

  it("names what the list leaves out", () => {
    const overdue = Array.from({ length: 6 }, () =>
      item("overdue", 9, "2026-09-25"),
    );
    expect(
      attentionFooter(
        { paymentReports: 0, overdue: 9, dueSoon: 4, drafts: 7 },
        overdue,
      ),
    ).toBe(
      "No payment reports to confirm. Also 3 more overdue invoices, 4 due this week and 7 drafts.",
    );
    expect(
      attentionFooter(
        { paymentReports: 2, overdue: 1, dueSoon: 3, drafts: 1 },
        [
          item("paymentReport", null, "2026-10-01"),
          item("paymentReport", null, "2026-10-02"),
          item("overdue", 2, "2026-10-02"),
          item("dueSoon", 1, "2026-10-05"),
          item("dueSoon", 2, "2026-10-06"),
        ],
      ),
    ).toBe("Also 1 more due this week and 1 draft.");
  });

  it("names nothing it no longer lists, though an older server counts it", () => {
    expect(
      attentionFooter(
        {
          paymentReports: 0,
          overdue: 1,
          dueSoon: 1,
          quotations: 0,
          recurring: 2,
          drafts: 0,
        },
        [item("overdue", 2, "2026-10-02"), item("dueSoon", 1, "2026-10-05")],
      ),
    ).toBe("No payment reports to confirm or drafts to issue.");
  });
});
