import { useEffect } from "react";
import { Link } from "react-router-dom";
import { FiPackage } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import { PAGE_SIZES } from "../../../utils/listState";
import { countOf } from "../../../utils/counts";
import { dateSpan } from "../../../utils/dates";
import { useGetItemSalesQuery } from "../itemsApiSlice";
import {
  itemName,
  itemSalesLink,
  itemSalesQuery,
  itemSalesSearch,
  readItemSales,
  shareLabel,
  shareLabels,
  soldCount,
} from "../itemSalesModel";
import ItemsTabs from "../components/ItemsTabs";
import CurrencyChoice from "../../documents/components/CurrencyChoice";
import PeriodFilter from "../../documents/components/PeriodFilter";
import {
  WorkspacePage,
  PageHeader,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  EmptyState,
  LoadingState,
  ErrorState,
  Amount,
} from "../../../components/workspace/Workspace";
import { panelNote, textLink } from "../../../components/workspace/pastel";

const NO_ITEMS = [];

// Items › Sales by item: what each item sold in the period, before tax, as
// the overview's Top items counts it, with no limit, and its share of the
// income behind Profit, with the totals under each page. Each item opens
// its own sales page for the same period and currency, the lines saved
// without a name too. The table keeps its rows' own height, like the
// Reports page's, so the totals are always in sight. The period, currency
// and page are kept in the address.
const ItemSalesPage = () => {
  useTitle("Sales by item — Invoice Spot");
  const [list, update] = useListState(readItemSales, itemSalesSearch);
  const query = useGetItemSalesQuery(itemSalesQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const sales = query.data;
  const rows = sales?.items || NO_ITEMS;
  const pagination = serverPagination({
    rows,
    count: sales?.totalItems ?? 0,
    page: list.page - 1,
    size: list.size,
    setPage: (page) => update({ page: page + 1 }),
    setSize: (size) => update({ size, page: 1 }),
  });
  // A page past the end, from an old link, shows the last.
  const pages = query.currentData?.numberOfPages;
  useEffect(() => {
    if (pages && list.page > pages) update({ page: pages });
  }, [pages, list.page, update]);

  const currency = sales?.currency;
  const several = sales?.currencies?.length > 1;
  // The shares shown add up to the 100% of the totals row.
  const shares = shareLabels(pagination.rows, sales?.total);
  const columns = [
    {
      label: "Item",
      type: "main",
      render: (row) =>
        row.total ? (
          `All ${countOf(sales.totalItems, "item", "items")}`
        ) : (
          <Link
            className={textLink}
            to={itemSalesLink(itemName(row.item), list.period, list.currency)}
          >
            {itemName(row.item)}
          </Link>
        ),
    },
    {
      label: "Sold",
      render: (row) => (row.total ? "" : soldCount(row.quantity)),
    },
    {
      label: "Amount",
      type: "amount",
      render: (row) => (
        <Amount
          value={row.amount}
          currency={currency}
          hideCode={!several}
          className={row.total ? "font-normal tracking-[-0.03em]" : undefined}
        />
      ),
    },
    {
      label: "Share of income",
      render: (row, index) =>
        row.total ? shareLabel(row.share) : shares[index],
    },
  ];
  const days =
    sales?.period?.from && sales?.period?.to
      ? `in ${dateSpan(sales.period.from, sales.period.to)}`
      : "at any time";

  return (
    <WorkspacePage section="Items">
      <PageHeader
        title="Items"
        description="The products and services you bill for, with their prices."
        actions={<ItemsTabs />}
      />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel>
          <div className="mb-5 flex flex-col gap-3 compact:mb-4">
            <h2 className="sr-only">Sales by item</h2>
            {sales && (
              <p className={panelNote}>
                What each item sold {days}, before tax, less what credit notes
                took back. Everything sold adds up to the income behind Profit.
              </p>
            )}
            <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
              <PeriodFilter
                label="Sold"
                value={list.period}
                onChange={(period) => update({ period, page: 1 })}
              />
              <CurrencyChoice
                value={currency}
                currencies={sales?.currencies}
                onChange={(chosen) => update({ currency: chosen, page: 1 })}
              />
            </div>
          </div>
          {query.isError ? (
            <ErrorState error={query.error} retry={query.refetch} />
          ) : rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={[
                  ...pagination.rows,
                  { total: true, amount: sales.total, share: 1 },
                ]}
                rowKey={(row, index) =>
                  row.total ? "totals" : `${index}-${row.item}`
                }
                label={`Sales by item, ${currency}`}
                busy={query.isFetching}
              />
              <Pagination pagination={pagination} sizes={PAGE_SIZES} />
            </>
          ) : (
            <EmptyState
              title={
                list.period
                  ? "Nothing was sold in this period."
                  : "Nothing sold yet."
              }
              text="Items show up here once they are on an invoice or receipt you issue, with what each brought in."
              icon={FiPackage}
            />
          )}
        </Panel>
      )}
    </WorkspacePage>
  );
};

export default ItemSalesPage;
