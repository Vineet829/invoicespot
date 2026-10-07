import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FiChevronLeft, FiChevronRight, FiDownload } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  useGetPortalQuery,
  useGetPortalStatementMutation,
} from "../portalApiSlice";
import { saveFile } from "../../documents/useDocumentPdf";
import {
  Amount,
  Badge,
  DataTable,
  StatusBadge,
  dateInput,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  pagerButton,
  panelNote,
  secondaryButton,
  textLink,
} from "../../../components/workspace/pastel";
import { Party, card } from "../../public/components/PublicDocument";
import { Notice, frame } from "../../public/components/PublicPage";

// Where a document stands, in the words the share page uses.
const Standing = ({ entry }) => {
  if (entry.state === "void") return <Badge tone="unpaid">Void</Badge>;
  if (entry.documentType === "Quotation") {
    if (entry.acceptedAt) return <Badge tone="paid">Accepted</Badge>;
    if (entry.declinedAt) return <Badge tone="unpaid">Declined</Badge>;
    if (entry.expired) return <Badge tone="partial">Expired</Badge>;
    return <Badge>Open</Badge>;
  }
  if (entry.documentType === "Credit Note") return <Badge>Credit</Badge>;
  return (
    <span className="flex flex-wrap gap-1.5">
      <StatusBadge status={entry.status} />
      {entry.overdue && <Badge tone="unpaid">Overdue</Badge>}
    </span>
  );
};

// What the client owes in one currency, as their statement shows it.
const Balance = ({ balance }) => (
  <div className="rounded-2xl border border-white/90 bg-white/60 p-5">
    <p className={panelNote}>Due in {balance.currency}</p>
    <p className="mt-1 text-[26px] text-spot-ink">
      <Amount value={balance.due} currency={balance.currency} />
    </p>
    <dl className="m-0 mt-3 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-[13px] text-spot-body">
      {[
        ["Invoiced", balance.invoiced],
        ["Paid", balance.paid],
        ["Credited", balance.credited],
        ["Refunded to you", balance.refunded],
        ["Overdue", balance.overdue],
      ]
        .filter(([label, value]) => label === "Invoiced" || Number(value) > 0)
        .map(([label, value]) => (
          <div key={label} className="contents">
            <dt>{label}</dt>
            <dd className="m-0 text-right">
              <Amount value={value} currency={balance.currency} hideCode />
            </dd>
          </div>
        ))}
    </dl>
  </div>
);

// A client's portal: every document the business issued them, what they owe
// in each currency, and their statement. The link is the key: no sign-in.
const ClientPortalPage = () => {
  const { token } = useParams();
  const [page, setPage] = useState(1);
  const query = useGetPortalQuery({ token, page });
  const [getStatement, { isLoading: downloading }] =
    useGetPortalStatementMutation();
  const portal = query.data;
  useTitle(
    portal
      ? `${portal.client} at ${portal.business.name}`
      : "Your documents — Invoice Spot",
  );
  const download = async () => {
    try {
      saveFile(
        await getStatement(token).unwrap(),
        `Statement-${dateInput()}.pdf`,
      );
    } catch (error) {
      toast.error(errorMessage(error, "The statement could not be prepared."));
    }
  };
  const columns = [
    {
      label: "Document",
      type: "main",
      render: (entry) => (
        <Link
          className={textLink}
          to={`/c/${token}/doc?number=${encodeURIComponent(entry.documentNumber)}`}
        >
          {entry.documentNumber}
        </Link>
      ),
    },
    { label: "Type", render: (entry) => entry.documentType },
    { label: "Issued", render: (entry) => dateLabel(entry.issueDate) },
    {
      label: "Due",
      render: (entry) => (entry.dueDate ? dateLabel(entry.dueDate) : "—"),
    },
    {
      label: "Total",
      type: "amount",
      render: (entry) => (
        <Amount value={entry.total} currency={entry.currency} />
      ),
    },
    {
      label: "Balance",
      type: "amount",
      render: (entry) =>
        entry.balance === undefined ? (
          "—"
        ) : (
          <Amount value={entry.balance} currency={entry.currency} />
        ),
    },
    { label: "Status", render: (entry) => <Standing entry={entry} /> },
  ];
  return (
    <div className={frame}>
      {query.isLoading ? (
        <p className={panelNote} role="status">
          Opening your documents…
        </p>
      ) : query.isError ? (
        query.error?.status === 404 ? (
          <Notice
            title="This link doesn’t work anymore"
            text="It may have been turned off or replaced. Ask the business that sent it for a new link."
          />
        ) : (
          <Notice
            title="We couldn’t open your documents"
            text={errorMessage(query.error)}
            retry={query.refetch}
          />
        )
      ) : (
        <main className={card} id="main">
          <header className="flex items-start justify-between gap-6 border-b border-[#23273512] pb-7 max-[700px]:flex-col">
            <Party party={portal.business} logo />
            <button
              type="button"
              className={`${secondaryButton} max-[700px]:w-full`}
              onClick={download}
              disabled={downloading}
              aria-busy={downloading}
            >
              <FiDownload aria-hidden="true" />
              {downloading ? "Preparing…" : "Download statement"}
            </button>
          </header>
          <div className="mt-7">
            <p className="text-[13px] text-spot-muted">Your documents</p>
            <h1 className="font-display text-[40px] font-extralight leading-[1.1] tracking-[-0.05em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[31px] [&>span]:text-spot-brand">
              {portal.client}
              {!portal.client?.endsWith(".") && <span>.</span>}
            </h1>
          </div>
          {portal.balances.length > 0 && (
            <section
              className="mt-7 grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4"
              aria-label="What you owe"
            >
              {portal.balances.map((balance) => (
                <Balance key={balance.currency} balance={balance} />
              ))}
            </section>
          )}
          <section className="mt-9" aria-label="Documents">
            {portal.documents.length === 0 ? (
              <p className={panelNote}>There are no documents here yet.</p>
            ) : (
              <DataTable
                columns={columns}
                rows={portal.documents}
                rowKey={(entry) => entry.documentNumber}
                label="Your documents"
                busy={query.isFetching}
              />
            )}
            {portal.pages > 1 && (
              <nav
                className="mt-4 flex items-center justify-end gap-2 text-[13px] text-spot-body"
                aria-label="Pages"
              >
                <button
                  type="button"
                  className={pagerButton}
                  onClick={() => setPage(page - 1)}
                  disabled={page <= 1}
                  aria-label="Previous page"
                >
                  <FiChevronLeft aria-hidden="true" />
                </button>
                Page {portal.page} of {portal.pages}
                <button
                  type="button"
                  className={pagerButton}
                  onClick={() => setPage(page + 1)}
                  disabled={page >= portal.pages}
                  aria-label="Next page"
                >
                  <FiChevronRight aria-hidden="true" />
                </button>
              </nav>
            )}
          </section>
        </main>
      )}
      <p className="text-[13px] text-spot-muted">
        Shared with <span className="text-spot-text">Invoice Spot</span>
      </p>
    </div>
  );
};

export default ClientPortalPage;
