import { describe, expect, it } from "vitest";
import {
  attentionFooter,
  attentionRow,
  axisLabel,
  billedLink,
  bucketLabel,
  bucketRange,
  cashFlowScale,
  changeFrom,
  dateSpan,
  findPeriod,
  formatDate,
  formatMoney,
  getStatusRingRatio,
  invoiceCount,
  labelStep,
  owedColumns,
  owedWeekLink,
  paymentRows,
  percentLabel,
  periodSubtitle,
  receivedLink,
  reportLink,
  showsLabel,
  statusLink,
  trendColumns,
  trendPoints,
  wholeAmount,
} from "./dashboardModel";

describe("status ring comparisons", () => {
  it.each([
    [4, 12, 1 / 3],
    [3, 12, 0.25],
    [5, 12, 5 / 12],
    [12, 12, 1],
    [0, 12, 0],
    [0, 0, 0],
    [1, 0, 0],
    [-1, 12, 0],
    [13, 12, 0],
    [NaN, 12, 0],
    [1, Infinity, 0],
    [undefined, 12, 0],
  ])("draws count %s against total %s as %s", (count, total, ratio) => {
    expect(getStatusRingRatio(count, total)).toBe(ratio);
  });
});

it("keys each payment by its id and finds the document it was recorded against", () => {
  const payments = [
    {
      _id: "p2",
      paidBy: "Second client",
      datePaid: "2026-09-20",
      amountPaid: 10,
      document: { _id: "d2", documentNumber: "INV-2" },
    },
    {
      _id: "p1",
      paidBy: "First client",
      datePaid: "2026-08-01",
      amountPaid: 24.5,
      document: { _id: "d1", documentNumber: "INV-1" },
    },
  ];
  const { rows, documents } = paymentRows(payments);
  expect(rows.map((row) => [row.id, row.paidBy])).toEqual([
    ["p2", "Second client"],
    ["p1", "First client"],
  ]);
  expect(rows.map((row) => documents.get(row.id).documentNumber)).toEqual([
    "INV-2",
    "INV-1",
  ]);
  expect(paymentRows().rows).toEqual([]);
});

it("formats amounts and dates safely", () => {
  expect(formatMoney(undefined)).toBe("0.00");
  expect(formatMoney(1234.5)).toBe("1,234.50");
  expect(formatDate("invalid")).toBe("—");
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

describe("links to the records behind each figure", () => {
  it.each([
    ["30d", "30d"],
    ["90d", "90d"],
    ["12m", "12m"],
    ["ytd", "this-year"],
  ])(
    "opens what was billed and received over %s as the lists' %s",
    (period, list) => {
      expect(billedLink(period)).toBe(`/documents?type=billed&period=${list}`);
      expect(billedLink(period, "EUR")).toBe(
        `/documents?type=billed&period=${list}&currency=EUR`,
      );
      expect(receivedLink(period)).toBe(`/payments?period=${list}`);
      expect(receivedLink(period, "EUR")).toBe(
        `/payments?period=${list}&currency=EUR`,
      );
    },
  );

  it("opens the invoices owed in each week, soonest due first, and the overdue ones", () => {
    expect(
      ["overdue", "thisWeek", "nextWeek", "weekAfter", "later"].map((key) =>
        owedWeekLink(key),
      ),
    ).toEqual([
      "/documents?status=overdue",
      "/documents?status=outstanding&due=this-week&sort=dueDate&dir=asc",
      "/documents?status=outstanding&due=next-week&sort=dueDate&dir=asc",
      "/documents?status=outstanding&due=week-after&sort=dueDate&dir=asc",
      "/documents?status=outstanding&due=later&sort=dueDate&dir=asc",
    ]);
    expect(owedWeekLink("overdue", "EUR")).toBe(
      "/documents?status=overdue&currency=EUR",
    );
    expect(owedWeekLink("nextWeek", "EUR")).toBe(
      "/documents?status=outstanding&due=next-week&currency=EUR&sort=dueDate&dir=asc",
    );
  });

  it("opens each payment status, which counts every currency", () => {
    expect(
      ["paid", "partial", "unpaid", "overdue"].map((status) =>
        statusLink(status),
      ),
    ).toEqual([
      "/documents?status=paid",
      "/documents?status=partial",
      "/documents?status=unpaid",
      "/documents?status=overdue",
    ]);
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

describe("what is owed, by due date", () => {
  const weeks = [
    {
      key: "overdue",
      from: null,
      to: "2026-10-03",
      amount: 6326.03,
      invoices: 3,
    },
    {
      key: "thisWeek",
      from: "2026-10-04",
      to: "2026-10-10",
      amount: 2664.09,
      invoices: 1,
    },
    {
      key: "nextWeek",
      from: "2026-10-11",
      to: "2026-10-17",
      amount: 13353.37,
      invoices: 4,
    },
    {
      key: "weekAfter",
      from: "2026-10-18",
      to: "2026-10-24",
      amount: 1942.3,
      invoices: 2,
    },
    { key: "later", from: "2026-10-25", to: null, amount: 0, invoices: 0 },
  ];

  it("names each week, against the tallest, and leaves Later out while nothing is due then", () => {
    const columns = owedColumns(weeks);
    expect(
      columns.map((column) => [column.name, column.when, column.spoken]),
    ).toEqual([
      ["Overdue", "Past due", "Overdue"],
      ["This week", "Oct 4–10", "This week, due Oct 4–10"],
      ["Next week", "Oct 11–17", "Next week, due Oct 11–17"],
      ["Week after", "Oct 18–24", "Week after, due Oct 18–24"],
    ]);
    expect(columns.map((column) => column.share)).toEqual([
      6326.03 / 13353.37,
      2664.09 / 13353.37,
      1,
      1942.3 / 13353.37,
    ]);
  });

  it("shows Later once something is due then, or has no due date", () => {
    const later = owedColumns([
      ...weeks.slice(0, 4),
      { ...weeks[4], amount: 500, invoices: 1 },
    ]).at(-1);
    expect([later.name, later.when, later.spoken]).toEqual([
      "Later",
      "From Oct 25",
      "Later, due from Oct 25 or with no due date",
    ]);
  });

  it("draws no columns when nothing is owed", () => {
    const empty = weeks.map((week) => ({ ...week, amount: 0, invoices: 0 }));
    expect(owedColumns(empty).map((column) => column.share)).toEqual([
      0, 0, 0, 0,
    ]);
    expect(owedColumns()).toEqual([]);
  });

  it("rounds amounts to whole units for the columns", () => {
    expect(wholeAmount(6326.03)).toBe("6,326");
    expect(wholeAmount(999.5)).toBe("1,000");
    expect(wholeAmount(123456, "INR")).toBe("1,23,456");
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
      item("quotation", 5, "2026-10-09"),
      "Quotation",
      "Awaiting an answer",
      "Valid until Oct 9",
    ],
    [
      item("recurring", 3, "2026-10-07"),
      "Recurring",
      "Runs in 3 days",
      "Next run Oct 7",
    ],
    [
      item("recurring", 1, "2026-10-05"),
      "Recurring",
      "Runs tomorrow",
      "Next run Oct 5",
    ],
    [
      item("recurring", 0, "2026-10-04"),
      "Recurring",
      "Runs today",
      "Next run Oct 4",
    ],
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

  it.each(["overdue", "dueSoon", "recurring"])(
    "gives only the day for %s when the number of days is missing",
    (kind) => {
      const row = attentionRow(item(kind, null, "2026-10-07"));
      expect(row.state).toBe("");
      expect(row.when).toMatch(/Oct 7$/);
      expect(attentionRow(item(kind, undefined, "2026-10-07")).state).toBe("");
    },
  );

  it("opens each item's document, and the recurring list for a schedule", () => {
    expect(attentionRow(item("overdue", 3, "2026-10-01")).to).toBe(
      "/document/d1",
    );
    expect(attentionRow(item("recurring", 3, "2026-10-07")).to).toBe(
      "/recurring",
    );
  });

  it("names the kinds there is nothing of", () => {
    const counts = {
      paymentReports: 1,
      overdue: 1,
      dueSoon: 1,
      quotations: 1,
      recurring: 1,
      drafts: 1,
    };
    const items = [
      "paymentReport",
      "overdue",
      "dueSoon",
      "quotation",
      "recurring",
      "draft",
    ].map((kind) => item(kind, 1, "2026-10-05"));
    const only = (...kinds) =>
      items.filter((entry) => kinds.includes(entry.kind));
    expect(
      attentionFooter(
        { ...counts, paymentReports: 0, recurring: 0, drafts: 0 },
        only("overdue", "dueSoon", "quotation"),
      ),
    ).toBe(
      "No payment reports to confirm, drafts to issue or recurring invoices due this week.",
    );
    expect(
      attentionFooter(
        { ...counts, quotations: 0 },
        items.filter((entry) => entry.kind !== "quotation"),
      ),
    ).toBe("No quotations expiring this week.");
    expect(
      attentionFooter(
        { ...counts, paymentReports: 0, overdue: 0 },
        only("dueSoon", "quotation", "recurring", "draft"),
      ),
    ).toBe("No payment reports to confirm or overdue invoices.");
    expect(attentionFooter(counts, items)).toBeNull();
  });

  it("names what the list leaves out", () => {
    const overdue = Array.from({ length: 6 }, () =>
      item("overdue", 9, "2026-09-25"),
    );
    expect(
      attentionFooter(
        {
          paymentReports: 0,
          overdue: 9,
          dueSoon: 4,
          quotations: 2,
          recurring: 1,
          drafts: 7,
        },
        overdue,
      ),
    ).toBe(
      "No payment reports to confirm. Also 3 more overdue invoices, 4 due this week, 2 quotations, 1 recurring invoice and 7 drafts.",
    );
    expect(
      attentionFooter(
        {
          paymentReports: 2,
          overdue: 1,
          dueSoon: 3,
          quotations: 1,
          recurring: 1,
          drafts: 1,
        },
        [
          item("paymentReport", null, "2026-10-01"),
          item("paymentReport", null, "2026-10-02"),
          item("overdue", 2, "2026-10-02"),
          item("dueSoon", 1, "2026-10-05"),
          item("dueSoon", 2, "2026-10-06"),
          item("quotation", 3, "2026-10-07"),
        ],
      ),
    ).toBe("Also 1 more due this week, 1 recurring invoice and 1 draft.");
  });
});
