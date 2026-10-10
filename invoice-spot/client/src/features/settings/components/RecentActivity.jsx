import { useState } from "react";
import {
  Badge,
  DataTable,
  EmptyState,
  ErrorState,
  LoadingState,
  Pagination,
  Panel,
  serverPagination,
} from "../../../components/workspace/Workspace";
import { panelNote } from "../../../components/workspace/pastel";
import { WARNING_EVENTS, eventLabel, whenLabel } from "../auditEvents";
import { useGetActivityQuery } from "../securityApiSlice";

// The account's own sign-ins and security changes, newest first.
const RecentActivity = ({ demo }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const query = useGetActivityQuery(
    { page: page + 1, size },
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
    <Panel
      title="Recent activity"
      description={
        demo
          ? "What you did in this visit to the shared demo account."
          : "Sign-ins and changes to your account’s security, newest first."
      }
    >
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : events.length ? (
        <>
          <DataTable
            label="Recent activity"
            rows={events}
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
                label: "Device",
                render: (event) => (
                  <span className="flex flex-col gap-0.5">
                    <span className="flex flex-wrap items-center gap-2">
                      {event.device}
                      {event.thisDevice && (
                        <Badge tone="paid">This device</Badge>
                      )}
                    </span>
                    {event.ip && <span className={panelNote}>{event.ip}</span>}
                  </span>
                ),
              },
              { label: "When", render: (event) => whenLabel(event.occurredAt) },
            ]}
          />
          <Pagination pagination={pagination} sizes={[10, 25, 50]} />
        </>
      ) : (
        <EmptyState
          title="Nothing yet."
          text="Sign-ins and security changes show up here."
        />
      )}
    </Panel>
  );
};

export default RecentActivity;
