import { useState } from "react";
import { Link } from "react-router-dom";
import {
  FiEdit2,
  FiPause,
  FiPlay,
  FiPlus,
  FiRepeat,
  FiTrash2,
} from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  ActionLink,
  Amount,
  Badge,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  dateLabel,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  rowAction,
  rowActionDanger,
  textLink,
} from "../../../components/workspace/pastel";
import DocumentsTabs from "../../documents/components/DocumentsTabs";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import {
  useDeleteScheduleMutation,
  useGetSchedulesQuery,
  useSetSchedulePausedMutation,
} from "../recurringApiSlice";
import { frequencyLabel } from "../frequencies";
import ScheduleDialog from "../components/ScheduleDialog";
import { useBusiness } from "../../business/permissions";

const NO_SCHEDULES = [];

// A schedule can copy a draft, which has no number yet.
const templateName = (schedule) => schedule.documentNumber || "Draft invoice";

const RecurringPage = () => {
  useTitle("Recurring invoices — Invoice Spot");
  const query = useGetSchedulesQuery(undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const demo = useGetUserProfileQuery().data?.userProfile?.demo;
  const schedules = query.data?.schedules || NO_SCHEDULES;
  const [editing, setEditing] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [setPaused, { isLoading: pausing }] = useSetSchedulePausedMutation();
  const [remove, { isLoading: deleting }] = useDeleteScheduleMutation();
  const editRefusal = useBusiness().reason("EDIT_RECORDS");
  const togglePause = async (schedule) => {
    try {
      const result = await setPaused({
        id: schedule._id,
        paused: !schedule.paused,
      }).unwrap();
      toast.success(result.message);
    } catch (error) {
      toast.error(errorMessage(error, "The schedule could not be changed."));
    }
  };
  const confirmRemove = async () => {
    try {
      const result = await remove(removing._id).unwrap();
      toast.success(result.message);
      setRemoving(null);
    } catch (error) {
      toast.error(errorMessage(error, "The schedule could not be removed."));
    }
  };
  const columns = [
    {
      label: "Invoice",
      type: "main",
      render: (schedule) => (
        <span className="flex flex-col gap-0.5">
          <Link className={textLink} to={`/document/${schedule.documentId}`}>
            {templateName(schedule)}
          </Link>
          <span className="text-[12.5px] font-normal text-spot-muted">
            {schedule.clientName}
          </span>
        </span>
      ),
    },
    {
      label: "Amount",
      type: "amount",
      render: (schedule) => (
        <Amount value={schedule.total} currency={schedule.currency} />
      ),
    },
    {
      label: "Repeats",
      render: (schedule) => frequencyLabel(schedule.frequency),
    },
    {
      label: "Next invoice",
      render: (schedule) =>
        schedule.finished ? (
          <Badge>Finished</Badge>
        ) : schedule.paused ? (
          <Badge tone="partial">Paused</Badge>
        ) : (
          dateLabel(schedule.nextRun)
        ),
    },
    {
      label: "Made",
      render: (schedule) =>
        `${schedule.runsDone}${schedule.maxRuns ? ` of ${schedule.maxRuns}` : ""}`,
    },
    {
      label: "Each invoice",
      render: (schedule) =>
        !schedule.issueAutomatically
          ? "Left as a draft"
          : schedule.emailAutomatically
            ? "Issued and emailed"
            : "Issued",
    },
    {
      label: "Edit",
      type: "action",
      render: (schedule) => (
        <button
          type="button"
          className={rowAction}
          onClick={() => setEditing(schedule)}
          aria-label={`Change how ${templateName(schedule)} repeats`}
        >
          <FiEdit2 aria-hidden="true" />
          <span>Edit</span>
        </button>
      ),
    },
    {
      label: "Pause",
      type: "action",
      render: (schedule) =>
        !schedule.finished && (
          <button
            type="button"
            className={rowAction}
            onClick={() => togglePause(schedule)}
            disabled={pausing}
            aria-label={`${schedule.paused ? "Resume" : "Pause"} ${templateName(schedule)}`}
          >
            {schedule.paused ? (
              <FiPlay aria-hidden="true" />
            ) : (
              <FiPause aria-hidden="true" />
            )}
            <span>{schedule.paused ? "Resume" : "Pause"}</span>
          </button>
        ),
    },
    {
      label: "Stop",
      type: "action",
      render: (schedule) => (
        <button
          type="button"
          className={rowActionDanger}
          onClick={() => setRemoving(schedule)}
          aria-label={`Stop repeating ${templateName(schedule)}`}
        >
          <FiTrash2 aria-hidden="true" />
          <span>Stop</span>
        </button>
      ),
    },
  ];
  // Viewers see the schedules, but not the ways to change them.
  const shown = editRefusal
    ? columns.filter(
        (column) => !["Edit", "Pause", "Stop"].includes(column.label),
      )
    : columns;
  return (
    <WorkspacePage section="Documents">
      <PageHeader
        title="Documents"
        description="Invoices, quotations, and receipts. All your paperwork in one place."
        actions={
          <>
            <DocumentsTabs />
            <ActionLink to="/create-doc" reason={editRefusal}>
              <FiPlus aria-hidden="true" /> Create document
            </ActionLink>
          </>
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <Panel
          title="Recurring invoices"
          description="Each schedule makes a new invoice from its template on the day it is due."
        >
          {schedules.length ? (
            <DataTable
              columns={shown}
              rows={schedules}
              label="Recurring invoices"
            />
          ) : (
            <EmptyState
              title="Nothing repeats yet."
              text="Open an invoice and choose Make recurring to bill it every week, month, quarter or year."
              icon={FiRepeat}
            />
          )}
        </Panel>
      )}
      <ScheduleDialog
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        documentId={editing?.documentId}
        documentNumber={editing?.documentNumber}
        schedule={editing}
        demo={demo}
      />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Stop repeating this invoice?"
        confirmLabel="Stop repeating"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
        busy={deleting}
      >
        No more invoices will be made from {removing && templateName(removing)}.
        The invoices already made stay as they are.
      </ConfirmDialog>
    </WorkspacePage>
  );
};

export default RecurringPage;
