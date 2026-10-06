import { useEffect } from "react";
import { Link } from "react-router-dom";
import { FiPlus, FiUsers } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import { PAGE_SIZES, nextSort } from "../../../utils/listState";
import { countOf } from "../../../utils/counts";
import { dateSpan } from "../../../utils/dates";
import { useGetCustomerSalesQuery } from "../customersApiSlice";
import {
  SORT_FIELDS,
  SORT_OPTIONS,
  customerSalesQuery,
  customerSalesSearch,
  readCustomerSales,
} from "../customerSalesModel";
import ClientsTabs from "../components/ClientsTabs";
import CurrencyChoice from "../../documents/components/CurrencyChoice";
import PeriodFilter from "../../documents/components/PeriodFilter";
import { useBusiness } from "../../business/permissions";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
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
import { Select } from "../../../components/workspace/Select";

const NO_CLIENTS = [];

// The totals row's figures stand out a little from the clients' own.
const totalAmount = "font-normal tracking-[-0.03em]";

// Clients › Sales by customer: every client billed in the period, with the
// documents billed, what that came to, what they paid on it and what they
// still owe, as the Reports page's Top clients counts them, with the totals
// for all of them under each page. A client's name opens the client. The
// table keeps its rows' own height, like the Reports page's, so the totals
// are always in sight under the page's rows. The period, currency, order
// and page are kept in the address.
const CustomerSalesPage = () => {
  useTitle("Sales by customer — Invoice Spot");
  const [list, update] = useListState(readCustomerSales, customerSalesSearch);
  const query = useGetCustomerSalesQuery(customerSalesQuery(list), {
    refetchOnMountOrArgChange: 60,
  });
  const sales = query.data;
  const rows = sales?.clients || NO_CLIENTS;
  const pagination = serverPagination({
    rows,
    count: sales?.totalClients ?? 0,
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
  const { reason } = useBusiness();

  const currency = sales?.currency;
  const several = sales?.currencies?.length > 1;
  const amount = (value, total) => (
    <Amount
      value={value}
      currency={currency}
      hideCode={!several}
      className={total ? totalAmount : undefined}
    />
  );
  const columns = [
    {
      label: "Client",
      type: "main",
      sortBy: "name",
      render: (row) =>
        row.total ? (
          `All ${countOf(sales.totalClients, "client", "clients")}`
        ) : row.clientId ? (
          <Link className={textLink} to={`/single-customer/${row.clientId}`}>
            {row.client}
          </Link>
        ) : (
          row.client || "No client"
        ),
    },
    {
      label: "Documents",
      render: (row) => Number(row.documents).toLocaleString("en"),
    },
    {
      label: "Billed",
      type: "amount",
      sortBy: "billed",
      render: (row) => amount(row.billed, row.total),
    },
    {
      label: "Paid",
      type: "amount",
      sortBy: "paid",
      render: (row) => amount(row.paid, row.total),
    },
    {
      label: "Owed",
      type: "amount",
      sortBy: "owed",
      render: (row) => amount(row.owed, row.total),
    },
  ];
  const days =
    sales?.period?.from && sales?.period?.to
      ? `issued ${dateSpan(sales.period.from, sales.period.to)}`
      : "issued at any time";

  return (
    <WorkspacePage section="Clients">
      <PageHeader
        title="Clients"
        description="The people behind your work. Keep their details close at hand."
        actions={
          <>
            <ClientsTabs />
            <ActionLink to="/create-customer" reason={reason("EDIT_RECORDS")}>
              <FiPlus aria-hidden="true" /> Add client
            </ActionLink>
          </>
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : (
        <Panel>
          <div className="mb-5 flex flex-col gap-3 compact:mb-4">
            <h2 className="sr-only">Sales by customer</h2>
            {sales && (
              <p className={panelNote}>
                What each client was billed on the documents {days}, what they
                have paid on them, and what they still owe.
              </p>
            )}
            <div className="flex min-w-0 flex-wrap items-center gap-2.5 max-[700px]:w-full">
              <PeriodFilter
                label="Issued"
                value={list.period}
                onChange={(period) => update({ period, page: 1 })}
              />
              <CurrencyChoice
                value={currency}
                currencies={sales?.currencies}
                onChange={(chosen) => update({ currency: chosen, page: 1 })}
              />
              <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
                <span className="sr-only">Sort clients</span>
                <Select
                  size="toolbar"
                  className="max-[700px]:w-full"
                  value={`${list.sort}:${list.dir}`}
                  onChange={(value) => {
                    const [sort, dir] = value.split(":");
                    update({ sort, dir, page: 1 });
                  }}
                >
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
          </div>
          {query.isError ? (
            <ErrorState error={query.error} retry={query.refetch} />
          ) : rows.length ? (
            <>
              <DataTable
                columns={columns}
                rows={[...pagination.rows, { total: true, ...sales.totals }]}
                rowKey={(row) =>
                  row.total ? "totals" : row.clientId || `name-${row.client}`
                }
                label={`Sales by customer, ${currency}`}
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
              title={
                list.period
                  ? "No one was billed in this period."
                  : "No one has been billed yet."
              }
              text="Clients show up here once you bill them, with what they paid and what they still owe."
              icon={FiUsers}
            />
          )}
        </Panel>
      )}
    </WorkspacePage>
  );
};

export default CustomerSalesPage;
