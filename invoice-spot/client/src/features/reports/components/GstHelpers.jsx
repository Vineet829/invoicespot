import { useState } from "react";
import { FiDownload } from "react-icons/fi";
import useFileDownload from "../../../hooks/useFileDownload";
import {
  Amount,
  DataTable,
  Field,
  Panel,
  errorMessage,
  money,
} from "../../../components/workspace/Workspace";
import { panelNote, rowAction } from "../../../components/workspace/pastel";
import {
  useGetGstCsvMutation,
  useGetGstReturnsQuery,
} from "../reportsApiSlice";
import { currentMonth, gstFileName, previousMonth } from "../reportModel";

// One file for the months shown, named as the server names it.
const GstCsvButton = ({ section, label, from, to }) => {
  const [download, busy] = useFileDownload(useGetGstCsvMutation);
  return (
    <button
      type="button"
      className={rowAction}
      onClick={() =>
        download({ section, from, to }, gstFileName(section, from, to))
      }
      disabled={busy}
      aria-label={`Download ${label} as CSV`}
    >
      <FiDownload aria-hidden="true" /> CSV
    </button>
  );
};

// A tax that doesn't apply to a GSTR-3B line is left blank.
const rupeeColumn = (label, key) => ({
  label,
  type: "amount",
  render: (row) =>
    row[key] == null ? "—" : <Amount value={row[key]} currency="INR" />,
});

const plural = (count, one, many) =>
  `${count.toLocaleString("en")} ${count === 1 ? one : many}`;

// Helper files for the GST returns, for the months chosen: each GSTR-1 table
// in the columns of the GST offline tool, and the GSTR-3B figures, from the
// documents issued in GST mode. They are a starting point, not a return.
const GstHelpers = () => {
  const [from, setFrom] = useState(previousMonth);
  const [to, setTo] = useState(previousMonth);
  // Moving one month past the other takes the other along, so the range
  // stays the right way round.
  const choose = (first, last, lastChosen = false) => {
    const backwards = first && last && first > last;
    setFrom(backwards && lastChosen ? last : first);
    setTo(backwards && !lastChosen ? first : last);
  };
  const query = useGetGstReturnsQuery({ from, to }, { skip: !from || !to });
  const data = query.data;
  const latest = currentMonth();
  return (
    <Panel
      title="GST filing helpers"
      description="Your GSTR-1 tables and GSTR-3B figures, in rupees, from the invoices, receipts and credit notes you issued in GST mode."
    >
      <p
        className="mb-5 rounded-2xl border border-spot-violet/15 bg-white/60 px-4 py-3 text-[13.5px] leading-[1.6] text-spot-body"
        role="note"
      >
        Prepared from your issued documents. Check against your books and the
        GST portal before filing.
      </p>
      <div className="mb-5 grid max-w-[440px] grid-cols-2 gap-4">
        <Field
          label="From"
          type="month"
          max={latest}
          required
          value={from}
          onChange={(event) => choose(event.target.value, to)}
        />
        <Field
          label="To"
          type="month"
          max={latest}
          required
          value={to}
          onChange={(event) => choose(from, event.target.value, true)}
        />
      </div>
      {query.isError ? (
        <p className="text-[13.5px] text-overdue-strong" role="alert">
          {errorMessage(query.error, "These months can't be shown.")}
        </p>
      ) : (
        data && (
          <div
            className={`transition-opacity ${query.isFetching ? "opacity-60" : ""}`}
            aria-busy={query.isFetching || undefined}
          >
            <p className="mb-4 text-[14px] text-spot-body">
              {data.documents
                ? `${plural(data.documents, "document", "documents")} issued in GST mode in these months.`
                : "No documents issued in GST mode in these months."}
            </p>
            <DataTable
              evenGaps
              large
              label="GSTR-1 files"
              narrow
              rows={data.files}
              rowKey={(row) => row.key}
              columns={[
                { label: "GSTR-1", type: "main", render: (row) => row.label },
                {
                  label: "Rows",
                  type: "number",
                  render: (row) => row.rows.toLocaleString("en"),
                },
                {
                  label: "File",
                  type: "action",
                  render: (row) => (
                    <GstCsvButton
                      section={row.key}
                      label={row.label}
                      from={data.from}
                      to={data.to}
                    />
                  ),
                },
              ]}
            />
            <div className="mb-3 mt-7 flex flex-wrap items-center justify-between gap-3">
              <h3 className="m-0 text-[16px] font-normal text-spot-ink">
                GSTR-3B outward supplies
              </h3>
              <GstCsvButton
                section="gstr3b"
                label="GSTR-3B outward supplies"
                from={data.from}
                to={data.to}
              />
            </div>
            <DataTable
              evenGaps
              large
              label="GSTR-3B outward supplies, INR"
              rows={data.gstr3b}
              rowKey={(row, index) => `${row.table}-${index}`}
              columns={[
                { label: "Table", render: (row) => row.table || "—" },
                {
                  label: "Nature of supplies",
                  type: "main",
                  render: (row) => row.description,
                },
                rupeeColumn("Taxable value", "taxable"),
                rupeeColumn("IGST", "igst"),
                rupeeColumn("CGST", "cgst"),
                rupeeColumn("SGST/UTGST", "sgst"),
              ]}
            />
            <ul
              className={`m-0 mt-4 flex list-none flex-col gap-1.5 p-0 ${panelNote}`}
            >
              <li>
                Invoices worth more than {money(data.b2clLimit, "INR")} to
                unregistered buyers in other states go in B2C large.
              </li>
              {data.missingHsn > 0 && (
                <li>
                  {plural(data.missingHsn, "line has", "lines have")} no HSN/SAC
                  code; the HSN files list{" "}
                  {data.missingHsn === 1 ? "it" : "them"} as “(missing
                  HSN/SAC)”.
                </li>
              )}
              {data.skipped > 0 && (
                <li>
                  {plural(data.skipped, "document", "documents")} issued outside
                  GST mode {data.skipped === 1 ? "is" : "are"} left out.
                </li>
              )}
              {data.unconverted > 0 && (
                <li>
                  {plural(data.unconverted, "document", "documents")} in another
                  currency without a rate to rupees{" "}
                  {data.unconverted === 1 ? "is" : "are"} left out.
                </li>
              )}
            </ul>
          </div>
        )
      )}
    </Panel>
  );
};

export default GstHelpers;
