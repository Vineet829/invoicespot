import { useState } from "react";
import {
  DataTable,
  EmptyState,
  ErrorState,
  LoadingState,
  Pagination,
  Panel,
  serverPagination,
} from "../../../components/workspace/Workspace";
import { panelNote } from "../../../components/workspace/pastel";
import { useGetTeamActivityQuery } from "../../business/businessApiSlice";
import { eventLabel, whenLabel } from "../auditEvents";

// Changes to the business's team, newest first, for its owner and admins:
// who was invited, joined, left or changed role, and who handed it over.
const TeamActivity = ({ business }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const query = useGetTeamActivityQuery(
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
      title="Team activity"
      description={`Invitations and changes to the team of ${business}, newest first.`}
    >
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : events.length ? (
        <>
          <DataTable
            label="Team activity"
            rows={events}
            busy={query.isFetching}
            columns={[
              {
                label: "Change",
                type: "main",
                render: (event) => (
                  <span className="flex flex-col gap-0.5">
                    {eventLabel(event.type)}
                    {event.detail && (
                      <span className={`font-normal ${panelNote}`}>
                        {event.detail}
                      </span>
                    )}
                  </span>
                ),
              },
              {
                label: "About",
                type: "email",
                render: (event) => event.email || "—",
              },
              {
                label: "By",
                type: "email",
                render: (event) =>
                  event.actor ? (
                    <span className="flex flex-col gap-0.5">
                      {event.actor}
                      <span className={panelNote}>{event.actorEmail}</span>
                    </span>
                  ) : (
                    "—"
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
          text="Invitations and changes to the team show up here."
        />
      )}
    </Panel>
  );
};

export default TeamActivity;
