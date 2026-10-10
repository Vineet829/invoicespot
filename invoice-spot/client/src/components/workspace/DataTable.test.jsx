import { afterEach, beforeEach, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { Badge, DataTable } from "./Workspace";
import { rowAction } from "./pastel";

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
beforeEach(() => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const columns = [
  { label: "No.", type: "index", render: (_row, index) => index + 1 },
  { label: "Name", type: "main", render: (row) => row.name },
  { label: "Email", type: "email", render: (row) => row.email },
  { label: "Documents", type: "number", render: (row) => row.documents },
  { label: "Balance", type: "amount", render: (row) => row.balance },
  { label: "Delete", type: "action", render: () => "Delete" },
];
const rows = [
  {
    _id: "a",
    name: "Verdant Farms",
    email: "office@verdantfarms.example",
    documents: 4,
    balance: "14,671.81",
  },
];
const draw = (props) =>
  act(async () =>
    root.render(
      <DataTable columns={columns} rows={rows} label="Clients" {...props} />,
    ),
  );
const cells = () =>
  Object.fromEntries(
    [...container.querySelectorAll("tbody td")].map((cell) => [
      cell.dataset.label,
      cell.className,
    ]),
  );

it("lays every table on one grid of columns as wide as their entries, the gaps between them even", async () => {
  await draw();
  const table = container.querySelector("table");
  expect(table.style.gridTemplateColumns).toBe("auto auto auto auto auto auto");
  // The grid holds while the panel is wide enough for a table; narrower,
  // the rows are cards.
  expect(table.className).toContain("tabled:grid");
  expect(table.className).toContain("tabled:justify-between");
  expect(table.className).toContain("stacked:block");
  // The rows, not the cells, carry the borders and the hover, which the
  // gaps would break.
  const row = container.querySelector("tbody tr");
  expect(row.className).toContain("tabled:grid-cols-subgrid");
  expect(row.className).toContain("tabled:hover:bg-[#5b47a00a]");
  expect(row.className).toContain("tabled:[&:hover>*]:bg-transparent");
});

it("sets every cell of a row, and every heading, on one baseline, the row 46px or taller", async () => {
  await draw();
  const row = container.querySelector("tbody tr");
  expect(row.className).toContain("tabled:items-baseline");
  expect(row.className).toContain("tabled:content-center");
  expect(row.className).toContain("tabled:min-h-[46px]");
  expect(row.className).toContain("tabled:h-auto");
  const headRow = container.querySelector("thead tr");
  expect(headRow.className).toContain("tabled:items-baseline");
  for (const heading of container.querySelectorAll("th")) {
    expect(heading.className).toContain("tabled:block");
    expect(heading.className).toContain("tabled:leading-7");
  }
});

it("right-aligns every figure and its heading: row numbers, counts and amounts", async () => {
  await draw();
  const [index, , , documents, balance] = container.querySelectorAll("th");
  for (const heading of [index, documents, balance])
    expect(heading.className).toContain("text-right");
  for (const label of ["No.", "Documents", "Balance"])
    expect(cells()[label]).toContain("text-right");
  expect(cells().Documents).toContain("tabular-nums");
  expect(cells()["No."]).toContain("tabular-nums");
  const count = container.querySelector('[data-label="Documents"]');
  expect(count.textContent).toBe("4");
});

it("centres an action column's heading and its buttons on one line", async () => {
  await draw();
  const headings = [...container.querySelectorAll("th")];
  expect(headings.at(-1).textContent).toBe("Delete");
  expect(headings.at(-1).className).toContain("text-center");
  expect(cells().Delete).toContain("tabled:text-center");
});

it("draws each kind of column at one size in every table, larger from 1536px wide", async () => {
  await draw();
  expect(cells().Name).toContain("text-[15px]");
  expect(cells().Name).toContain("min-[1536px]:text-[17px]");
  expect(cells().Email).toContain("min-[1536px]:text-[15px]");
  expect(cells().Documents).toContain("min-[1536px]:text-[15px]");
  expect(cells()["No."]).toContain("min-[1536px]:text-[15px]");
  expect(cells().Balance).toContain("text-[17px]");
  expect(cells().Balance).toContain("min-[1536px]:text-[20px]");
  for (const heading of container.querySelectorAll("th"))
    expect(heading.className).toContain("min-[1536px]:text-[14px]");
  // Nothing grows with the panel's size.
  expect(container.innerHTML).not.toMatch(/roomy:|fill-grow/);
});

it("lets text wrap but keeps figures, addresses, actions and nowrap columns whole", async () => {
  await act(async () =>
    root.render(
      <DataTable
        columns={[
          ...columns,
          { label: "Status", nowrap: true, render: () => "Active" },
        ]}
        rows={rows}
        label="Clients"
      />,
    ),
  );
  for (const label of ["Name"])
    expect(cells()[label]).toContain("tabled:min-w-0");
  for (const label of [
    "No.",
    "Email",
    "Documents",
    "Balance",
    "Delete",
    "Status",
  ])
    expect(cells()[label]).toContain("tabled:min-w-max");
  // An address keeps to one line while the list is a table.
  expect(cells().Email).toContain("tabled:whitespace-nowrap");
});

it("gives a column the track it asks for", async () => {
  await act(async () =>
    root.render(
      <DataTable
        columns={[{ ...columns[1], track: "minmax(240px,auto)" }, columns[4]]}
        rows={rows}
        label="Clients"
      />,
    ),
  );
  expect(container.querySelector("table").style.gridTemplateColumns).toBe(
    "minmax(240px,auto) auto",
  );
});

it("keeps a narrow table a table in narrower panels, and a wide one only in wider ones", async () => {
  const wrapper = () => container.querySelector("table").parentElement;
  await draw();
  expect(wrapper().className).toContain("[container-name:table]");
  await draw({ narrow: true });
  expect(wrapper().className).toContain("[container-name:narrow-table]");
  await draw({ wide: true });
  expect(wrapper().className).toContain("[container-name:broad-table]");
});

it("fills its panel with rows of at least 46px, and is as tall as all of them, so none is ever cut off", async () => {
  await draw({ fillRows: 25 });
  const wrapper = container.querySelector("table").parentElement;
  expect(wrapper.style.getPropertyValue("--fill-rows")).toBe("25");
  expect(wrapper.className).toContain(
    "min-[1360px]:min-h-[calc(35px+var(--fill-rows)*46px)]",
  );
  expect(wrapper.className).toContain(
    "[--fill-row:clamp(46px,calc((100cqh_-_35px)_/_var(--fill-rows)),88px)]",
  );
  expect(container.querySelector("tbody tr").className).toContain(
    "tabled:min-h-[var(--fill-row,46px)]",
  );
});

it("puts a row action's label and a badge's text, not their icon or dot, on the baseline", async () => {
  expect(rowAction).toContain("items-baseline");
  expect(rowAction).toContain("[&>svg]:self-center");
  await act(async () => root.render(<Badge tone="paid">Paid</Badge>));
  const badge = container.firstElementChild;
  expect(badge.className).toContain("items-baseline");
  expect(badge.querySelector("[aria-hidden=true]").className).toContain(
    "self-center",
  );
});
