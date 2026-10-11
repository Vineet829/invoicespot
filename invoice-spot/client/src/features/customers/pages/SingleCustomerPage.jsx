import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  FiDownload,
  FiEdit2,
  FiEye,
  FiFileText,
  FiLink,
  FiMail,
  FiPlus,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import {
  PAGE_SIZES,
  nextSort,
  readSortAndPage,
  writeSortAndPage,
} from "../../../utils/listState";
import {
  useGetCustomerSummaryQuery,
  useGetSingleCustomerQuery,
  useGetStatementPdfMutation,
} from "../customersApiSlice";
import { useGetAllMyDocsQuery } from "../../documents/documentsApiSlice";
import { SORT_FIELDS } from "../../documents/documentFilters";
import { saveFile } from "../../documents/useDocumentPdf";
import {
  DocumentStatusBadge,
  documentName,
} from "../../documents/documentState";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import { RefusalNote, useBusiness } from "../../business/permissions";
import EmailStatementDialog from "../components/EmailStatementDialog";
import PortalDialog from "../components/PortalDialog";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  InfoGrid,
  DataTable,
  Pagination,
  serverPagination,
  EmptyState,
  LoadingState,
  ErrorState,
  Badge,
  Amount,
  DueDate,
  dateInput,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  panelNote,
  rowAction,
  secondaryButton,
} from "../../../components/workspace/pastel";

const NO_DOCUMENTS = [];

// The client's documents keep their sort and page in the URL.
const readDocuments = (params) =>
  readSortAndPage(params, SORT_FIELDS, "createdAt");
const writeDocuments = (state) =>
  writeSortAndPage(new URLSearchParams(), state, SORT_FIELDS, "createdAt");

// The overview's card colours; overdue is only coloured when something is.
const TOTALS = [
  {
    key: "billed",
    label: "Billed",
    tone: "bg-card-blue text-card-blue-ink",
    note: "Invoices and receipts",
  },
  // Payments and receipts, as the overview, payments and reports call
  // money in "Received".
  {
    key: "paid",
    label: "Received",
    tone: "bg-card-lilac text-card-lilac-ink",
    note: "Payments and receipts",
  },
  {
    key: "outstanding",
    label: "Outstanding",
    tone: "bg-card-peach text-card-peach-ink",
    note: "Still owed on invoices",
  },
  {
    key: "overdue",
    label: "Overdue",
    tone: (value) =>
      Number(value) > 0
        ? "bg-overdue text-overdue-ink"
        : "bg-white/70 text-spot-ink",
    note: "Past the due date",
  },
];

const DEMO_EMAIL_NOTE = "Emailing is turned off for the shared demo account.";

// Named like the server's copy: the account number, or the name, and today.
const statementFileName = (customer) =>
  `Statement-${(customer.accountNo || customer.name || "client").replace(/[^A-Za-z0-9-]+/g, "-")}-${dateInput()}.pdf`;

const StatementActions = ({ customer, demo, refusal }) => {
  const [getStatement] = useGetStatementPdfMutation();
  const [downloading, setDownloading] = useState(false);
  const [emailing, setEmailing] = useState(false);
  const [sharing, setSharing] = useState(false);
  const download = async () => {
    setDownloading(true);
    try {
      const url = await getStatement(customer._id).unwrap();
      saveFile(url, statementFileName(customer));
    } catch (error) {
      toast.error(errorMessage(error, "The statement could not be prepared."));
    } finally {
      setDownloading(false);
    }
  };
  return (
    <div className="flex flex-wrap gap-2.5 max-[700px]:w-full max-[700px]:[&>button]:flex-1">
      <button
        type="button"
        className={secondaryButton}
        onClick={download}
        disabled={downloading}
        aria-busy={downloading}
      >
        <FiDownload aria-hidden="true" />
        {downloading ? "Preparing…" : "Download statement"}
      </button>
      <button
        type="button"
        className={secondaryButton}
        onClick={() => setEmailing(true)}
        disabled={demo || Boolean(refusal)}
        title={refusal || (demo ? DEMO_EMAIL_NOTE : undefined)}
        aria-describedby={
          refusal
            ? "statement-refusal"
            : demo
              ? "statement-demo-note"
              : undefined
        }
      >
        <FiMail aria-hidden="true" /> Email statement
      </button>
      <button
        type="button"
        className={secondaryButton}
        onClick={() => setSharing(true)}
      >
        <FiLink aria-hidden="true" /> Client portal
      </button>
      <RefusalNote id="statement-refusal" reason={refusal} />
      {demo && (
        <span id="statement-demo-note" className="sr-only">
          {DEMO_EMAIL_NOTE}
        </span>
      )}
      <EmailStatementDialog
        customerId={customer._id}
        open={emailing}
        onClose={() => setEmailing(false)}
      />
      <PortalDialog
        customer={customer}
        open={sharing}
        onClose={() => setSharing(false)}
        demoNote={demo ? DEMO_EMAIL_NOTE : undefined}
        refusal={refusal}
      />
    </div>
  );
};

const AccountTotals = ({ totals }) => (
  <div className="flex flex-col gap-3.5">
    {totals.map((row) => (
      <dl
        key={row.currency}
        className="m-0 grid grid-cols-4 gap-3.5 max-[1100px]:grid-cols-2 max-[480px]:grid-cols-1"
      >
        {TOTALS.map(({ key, label, tone, note }) => (
          <div
            key={key}
            className={`min-w-0 rounded-[22px] border border-white/90 px-5 py-4 ${typeof tone === "function" ? tone(row[key]) : tone}`}
          >
            <dt className="text-[13px] opacity-80">{label}</dt>
            <dd className="mt-1.5 text-[26px] leading-tight max-[700px]:text-[23px]">
              <Amount value={row[key]} currency={row.currency} />
              <span className="mt-1 block text-[13px] opacity-75">{note}</span>
            </dd>
          </div>
        ))}
      </dl>
    ))}
  </div>
);

const ClientDocuments = ({ customerId }) => {
  const [list, update] = useListState(readDocuments, writeDocuments);
  const query = useGetAllMyDocsQuery(
    {
      page: list.page,
      size: list.size,
      sort: list.sort,
      dir: list.dir,
      customerId,
    },
    { refetchOnMountOrArgChange: 60 },
  );
  const rows = query.data?.myDocuments || NO_DOCUMENTS;
  const pagination = serverPagination({
    rows,
    count: query.data?.totalDocuments ?? 0,
    page: list.page - 1,
    size: list.size,
    setPage: (page) => update({ page: page + 1 }),
    setSize: (size) => update({ size, page: 1 }),
  });
  const columns = [
    {
      label: "#",
      type: "index",
      render: (_row, index) => pagination.start + index + 1,
    },
    {
      label: "Document no.",
      type: "main",
      sortBy: "number",
      render: (row) =>
        row.documentNumber || <span className="text-spot-muted">Draft</span>,
    },
    { label: "Type", render: (row) => <Badge>{row.documentType}</Badge> },
    {
      label: "Amount",
      type: "amount",
      sortBy: "total",
      render: (row) => <Amount value={row.total} currency={row.currency} />,
    },
    {
      label: "Due date",
      sortBy: "dueDate",
      render: (row) => <DueDate value={row.dueDate} overdue={row.overdue} />,
    },
    {
      label: "Status",
      render: (row) => <DocumentStatusBadge document={row} />,
    },
    {
      label: "View",
      type: "action",
      render: (row) => (
        <Link
          className={rowAction}
          to={`/document/${row._id}`}
          aria-label={`View ${documentName(row)}`}
        >
          <FiEye aria-hidden="true" />
          <span>View</span>
        </Link>
      ),
    },
  ];
  return (
    <Panel
      title="Documents"
      description="Every invoice, quotation and receipt for this client."
    >
      {query.isLoading ? (
        <p className={panelNote} role="status">
          Loading documents…
        </p>
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : rows.length ? (
        <>
          <DataTable
            columns={columns}
            rows={pagination.rows}
            label="The client's documents"
            sort={{ field: list.sort, dir: list.dir }}
            onSort={(field) =>
              update({ ...nextSort(SORT_FIELDS, list, field), page: 1 })
            }
            busy={query.isFetching}
          />
          <Pagination pagination={pagination} sizes={PAGE_SIZES} />
        </>
      ) : (
        <EmptyState
          title="Nothing billed yet."
          text="Documents you create for this client will be listed here."
          icon={FiFileText}
        />
      )}
    </Panel>
  );
};

const SingleCustomerPage = () => {
  const { custId } = useParams();
  const query = useGetSingleCustomerQuery(custId);
  const summary = useGetCustomerSummaryQuery(custId, {
    skip: !query.data?.customer,
  });
  const profile = useGetUserProfileQuery().data?.userProfile;
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  const customer = query.data?.customer;
  const totals = summary.data?.totals || [];
  useTitle(`${customer?.name || "Client details"} — Invoice Spot`);
  return (
    <WorkspacePage section="Clients">
      <PageHeader
        back
        title="Client details"
        description="Contact and billing information, together."
        actions={
          customer && (
            <>
              <ActionLink
                to={`/edit-customer/${custId}`}
                secondary
                reason={editRefusal}
              >
                <FiEdit2 aria-hidden="true" /> Edit client
              </ActionLink>
              <ActionLink
                to={`/create-doc?client=${custId}`}
                reason={editRefusal}
              >
                <FiPlus aria-hidden="true" /> New invoice
              </ActionLink>
            </>
          )
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        customer && (
          <>
            <Panel>
              <div className="mb-6 flex items-center gap-5 border-b border-[#23273512] pb-6 max-[700px]:gap-4">
                <span
                  className="grid size-16 shrink-0 place-items-center rounded-[20px] bg-avatar-lilac font-display text-[22px] font-light text-avatar-lilac-ink max-[700px]:size-14 max-[700px]:rounded-[18px] max-[700px]:text-[19px]"
                  aria-hidden="true"
                >
                  {customer.name
                    ?.split(/\s+/)
                    .filter((part) => /[\p{L}\p{N}]/u.test(part))
                    .slice(0, 2)
                    .map((part) => part[0])
                    .join("")
                    .toUpperCase()}
                </span>
                <div className="min-w-0">
                  <h2 className="font-display text-[28px] font-light leading-[1.15] tracking-[-0.04em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[23px]">
                    {customer.name}
                  </h2>
                  <p className="mt-1 text-[13.5px] text-spot-muted [overflow-wrap:anywhere]">
                    {customer.accountNo}
                  </p>
                </div>
              </div>
              <InfoGrid
                items={[
                  ["Email address", customer.email],
                  ["Phone number", customer.phoneNumber],
                  ["VAT / TIN number", customer.vatTinNo],
                  ...(customer.gstin ? [["GSTIN", customer.gstin]] : []),
                  ["Address", customer.address],
                  ["City", customer.city],
                  ["Country", customer.country],
                ]}
              />
            </Panel>
            <Panel
              title="Account summary"
              description="What this client was billed, what was received from them and what they still owe."
              aside={
                <StatementActions
                  customer={customer}
                  demo={profile?.demo}
                  refusal={editRefusal}
                />
              }
            >
              {summary.isError ? (
                <ErrorState error={summary.error} retry={summary.refetch} />
              ) : summary.isLoading ? (
                <p className={panelNote} role="status">
                  Loading totals…
                </p>
              ) : totals.length ? (
                <AccountTotals totals={totals} />
              ) : (
                <p className={panelNote}>
                  Nothing issued yet. Totals appear once you bill this client.
                </p>
              )}
            </Panel>
            <ClientDocuments customerId={custId} />
          </>
        )
      )}
    </WorkspacePage>
  );
};
export default SingleCustomerPage;
