import { Link } from "react-router-dom";
import { FiPackage } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import { countOf } from "../../../utils/counts";
import { dateSpan } from "../../../utils/dates";
import { useGetItemSalesDetailQuery } from "../itemsApiSlice";
import {
  OTHER_LINES,
  itemDetailQuery,
  itemDetailSearch,
  readItemDetail,
  shareLabel,
  soldCount,
} from "../itemSalesModel";
import CurrencyChoice from "../../documents/components/CurrencyChoice";
import PeriodFilter from "../../documents/components/PeriodFilter";
import SummaryFigure from "../../documents/components/SummaryFigure";
import { share } from "../../reports/reportModel";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  EmptyState,
  LoadingState,
  ErrorState,
  Amount,
  Badge,
  ShareBar,
  dateLabel,
} from "../../../components/workspace/Workspace";
import { panelNote, textLink } from "../../../components/workspace/pastel";

const NO_ROWS = [];
const partTitle = "mb-1 text-[14px] font-medium text-spot-ink";

// One item's sales over a period, in one currency: what it brought in, how
// many were sold and its share of the income, the clients who bought it,
// largest first, and the latest lines it was sold on, newest first, each
// opening its document. The item is matched by name, in any case, as Sales
// by item groups it; other lines are those saved without a name. The name,
// period and currency are kept in the address.
const ItemSalesDetailPage = () => {
  const [view, update] = useListState(readItemDetail, itemDetailSearch);
  useTitle(`${view.name || "Item"} sales — Invoice Spot`);
  const query = useGetItemSalesDetailQuery(itemDetailQuery(view), {
    skip: !view.name,
    refetchOnMountOrArgChange: 60,
  });
  const detail = query.data;
  const currency = detail?.currency;
  const several = detail?.currencies?.length > 1;
  const clients = detail?.clients || NO_ROWS;
  const lines = detail?.lines || NO_ROWS;
  const largest = Math.max(0, ...clients.map((row) => Number(row.amount)));
  const amount = (value) => (
    <Amount value={value} currency={currency} hideCode={!several} />
  );
  // Other lines are the lines saved without an item name, and say so.
  const days = `Sold ${
    detail?.period?.from && detail?.period?.to
      ? dateSpan(detail.period.from, detail.period.to)
      : "at any time"
  }, before tax${detail?.item === OTHER_LINES ? ", on lines saved without an item name" : ""}.`;

  const header = (
    <PageHeader
      back
      title="Item sales"
      description="What one item brought in, who bought it, and each line it was sold on."
    />
  );
  if (!view.name) {
    return (
      <WorkspacePage section="Items">
        {header}
        <Panel>
          <EmptyState
            title="No item was chosen."
            text="Open an item from Sales by item to see who bought it and each line it was sold on."
            icon={FiPackage}
            action={
              <ActionLink to="/items/sales" secondary>
                Sales by item
              </ActionLink>
            }
          />
        </Panel>
      </WorkspacePage>
    );
  }
  return (
    <WorkspacePage section="Items">
      {header}
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError && !detail ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        <>
          <Panel
            title={detail.item || view.name}
            description={days}
            aside={
              <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
                <PeriodFilter
                  label="Sold"
                  value={view.period}
                  onChange={(period) => update({ period })}
                />
                <CurrencyChoice
                  value={currency}
                  currencies={detail.currencies}
                  onChange={(chosen) => update({ currency: chosen })}
                />
              </div>
            }
          >
            <div
              className={`grid min-w-0 gap-x-10 gap-y-6 transition-opacity min-[1100px]:grid-cols-[minmax(0,1fr)_minmax(420px,0.85fr)] ${query.isFetching ? "opacity-60" : ""}`}
              aria-busy={query.isFetching || undefined}
            >
              <SummaryFigure
                label="Brought in"
                total={detail.amount}
                currency={currency}
                several={several}
                detail={`${soldCount(detail.quantity)} sold, ${shareLabel(detail.share)} of income, on ${countOf(detail.documents, "document", "documents")}`}
              />
              <div className="min-w-0">
                <h3 className={partTitle}>Who bought it</h3>
                {clients.length ? (
                  <DataTable
                    label={`Who bought ${detail.item || view.name}, ${currency}`}
                    narrow
                    rows={clients}
                    rowKey={(row) => row.clientId || `name-${row.client}`}
                    columns={[
                      {
                        label: "Client",
                        type: "main",
                        render: (row) => (
                          <>
                            {row.clientId ? (
                              <Link
                                className={textLink}
                                to={`/single-customer/${row.clientId}`}
                              >
                                {row.client}
                              </Link>
                            ) : (
                              row.client || "No client"
                            )}
                            <ShareBar
                              ratio={share(row.amount, largest)}
                              tone="bg-card-sage-bar"
                            />
                          </>
                        ),
                      },
                      {
                        label: "Sold",
                        render: (row) => soldCount(row.quantity),
                      },
                      {
                        label: "Amount",
                        type: "amount",
                        render: (row) => amount(row.amount),
                      },
                    ]}
                  />
                ) : (
                  <p className={`mt-2 ${panelNote}`}>
                    No one bought it in this period.
                  </p>
                )}
              </div>
            </div>
          </Panel>
          <Panel
            title="Lines"
            description={
              detail.linesTotal > lines.length
                ? `The latest ${lines.length.toLocaleString("en")} of ${countOf(detail.linesTotal, "line", "lines")}, newest first.`
                : "Each line it was sold on, newest first."
            }
          >
            {lines.length ? (
              <DataTable
                label={`Lines selling ${detail.item || view.name}`}
                rows={lines}
                rowKey={(row, index) => `${row.documentId}-${index}`}
                busy={query.isFetching}
                columns={[
                  {
                    label: "Document",
                    type: "main",
                    render: (row) => (
                      <Link
                        className={textLink}
                        to={`/document/${row.documentId}`}
                      >
                        {row.documentNumber || "No number yet"}
                      </Link>
                    ),
                  },
                  {
                    label: "Type",
                    render: (row) => <Badge>{row.documentType}</Badge>,
                  },
                  {
                    label: "Issued",
                    render: (row) => dateLabel(row.issueDate),
                  },
                  { label: "Client", render: (row) => row.client || "—" },
                  {
                    label: "Sold",
                    render: (row) => soldCount(row.quantity),
                  },
                  {
                    label: "Amount",
                    type: "amount",
                    render: (row) => amount(row.amount),
                  },
                ]}
              />
            ) : (
              <EmptyState
                title="Nothing sold in this period."
                text="Each invoice or receipt line this item is sold on shows up here, newest first."
                icon={FiPackage}
              />
            )}
          </Panel>
        </>
      )}
    </WorkspacePage>
  );
};

export default ItemSalesDetailPage;
