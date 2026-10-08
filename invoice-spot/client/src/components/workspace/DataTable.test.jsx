import { afterEach, beforeEach, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { DataTable } from "./Workspace";

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

it("lays an even-gaps table on one grid of columns as wide as their entries, an email keeping room", async () => {
  await draw({ evenGaps: true });
  const table = container.querySelector("table");
  expect(table.style.gridTemplateColumns).toBe(
    "auto auto minmax(224px,auto) auto auto auto",
  );
  // The grid holds while the panel is wide enough for a table; narrower,
  // the rows are still cards.
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

it("right-aligns a count and its heading, as an amount is", async () => {
  await draw({ evenGaps: true });
  const [, , , documents, balance] = container.querySelectorAll("th");
  expect(documents.className).toContain("text-right");
  expect(documents.className).toContain("tabled:justify-end");
  expect(balance.className).toContain("tabled:justify-end");
  const count = container.querySelector('[data-label="Documents"]');
  expect(count.className).toContain("text-right");
  expect(count.className).toContain("tabular-nums");
  expect(count.textContent).toBe("4");
});

it("gives a column the track it asks for", async () => {
  await act(async () =>
    root.render(
      <DataTable
        columns={[{ ...columns[1], track: "minmax(240px,auto)" }, columns[4]]}
        rows={rows}
        label="Clients"
        evenGaps
      />,
    ),
  );
  expect(container.querySelector("table").style.gridTemplateColumns).toBe(
    "minmax(240px,auto) auto",
  );
});

it("grows a large table's text where it has the room", async () => {
  await draw({ evenGaps: true, large: true });
  const cells = Object.fromEntries(
    [...container.querySelectorAll("tbody td")].map((cell) => [
      cell.dataset.label,
      cell.className,
    ]),
  );
  expect(cells.Name).toContain("roomy:text-[17px]");
  expect(cells.Email).toContain("roomy:text-[15px]");
  expect(cells.Documents).toContain("roomy:text-[15px]");
  expect(cells.Balance).toContain("roomy:text-[20px]");
  expect(cells.Delete).not.toContain("roomy:");
  for (const heading of container.querySelectorAll("th"))
    expect(heading.className).toContain("roomy:text-[14px]");
});

it("leaves other tables as they were", async () => {
  await draw();
  const table = container.querySelector("table");
  expect(table.style.gridTemplateColumns).toBe("");
  expect(table.className).not.toContain("tabled:");
  expect(container.innerHTML).not.toContain("roomy:");
  expect(container.querySelector("tbody tr").className).not.toContain(
    "tabled:",
  );
});
