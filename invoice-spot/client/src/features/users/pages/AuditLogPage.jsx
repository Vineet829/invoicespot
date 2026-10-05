import { useState } from "react";
import useTitle from "../../../hooks/useTitle";
import {
  Badge,
  DataTable,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Pagination,
  Panel,
  SearchField,
  WorkspacePage,
  serverPagination,
} from "../../../components/workspace/Workspace";
import { panelNote } from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";
import {
  EVENT_LABELS,
  WARNING_EVENTS,
  eventLabel,
  whenLabel,
} from "../../settings/auditEvents";
import { useGetAuditLogQuery } from "../auditApiSlice";
import AdminTabs from "../components/AdminTabs";

// Every account's sign-ins and security changes, and what admins did.
const AuditLogPage = () => {
  useTitle("Audit log — Invoice Spot");
  const [q, setQ] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const query = useGetAuditLogQuery(
    { q, type, page: page + 1, size },
    { refetchOnMountOrArgChange: true },
  );
  const events = query.data?.events || [];
  const pagination = serverPagination({
    rows: events,
    count: query.data?.total || 0,
    page,
    size,
    setPage,
    setSize: (next) => {
      setSize(next);
      setPage(0);
    },
  });
  return (
    <WorkspacePage section="Administration" fill>
      <PageHeader
        title="Administration"
        description="Sign-ins, security and team changes across every account."
        actions={<AdminTabs />}
      />
      <Panel fill={events.length > 0}>
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <h2 className="sr-only">Audit log</h2>
          <SearchField
            value={q}
            onSearch={(next) => {
              setQ(next);
              setPage(0);
            }}
            label="Search by email"
            placeholder="Account email"
            className="w-[260px] max-[700px]:w-full"
          />
          <label className="min-w-0 max-[700px]:w-full">
            <span className="sr-only">Kind of event</span>
            <Select
              size="toolbar"
              className="max-[700px]:w-full"
              value={type}
              onChange={(kind) => {
                setType(kind);
                setPage(0);
              }}
            >
              <option value="">Every kind of event</option>
              {Object.entries(EVENT_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </label>
        </div>
        {query.isLoading ? (
          <LoadingState />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={query.refetch} />
        ) : events.length ? (
          <>
            <DataTable
              label="Audit log"
              rows={events}
              fillRows={events.length}
              busy={query.isFetching}
              columns={[
                {
                  label: "Event",
                  type: "main",
                  render: (event) => (
                    <span className="flex flex-col gap-0.5">
                      <span className="flex flex-wrap items-center gap-2">
                        {eventLabel(event.type)}
                        {WARNING_EVENTS.has(event.type) && (
                          <Badge tone="unpaid">Check</Badge>
                        )}
                      </span>
                      {event.detail && (
                        <span className={`font-normal ${panelNote}`}>
                          {event.detail}
                        </span>
                      )}
                    </span>
                  ),
                },
                {
                  label: "Account",
                  type: "email",
                  // A team change also names the business it happened in.
                  render: (event) => (
                    <span className="flex flex-col gap-0.5">
                      {event.email || "—"}
                      {event.business && (
                        <span className={panelNote}>in {event.business}</span>
                      )}
                    </span>
                  ),
                },
                {
                  label: "By",
                  type: "email",
                  render: (event) => event.actorEmail || "Themselves",
                },
                {
                  label: "Device",
                  render: (event) => (
                    <span className="flex flex-col gap-0.5">
                      {event.device}
                      {event.ip && (
                        <span className={panelNote}>{event.ip}</span>
                      )}
                    </span>
                  ),
                },
                {
                  label: "When",
                  render: (event) => whenLabel(event.occurredAt),
                },
              ]}
            />
            <Pagination pagination={pagination} sizes={[10, 25, 50, 100]} />
          </>
        ) : (
          <EmptyState
            title={q || type ? "Nothing matches." : "Nothing yet."}
            text="Sign-ins, password and two-factor changes, and admin actions show up here."
          />
        )}
      </Panel>
    </WorkspacePage>
  );
};

export default AuditLogPage;
