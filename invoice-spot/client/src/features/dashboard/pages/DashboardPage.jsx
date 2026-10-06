import { Link, useSearchParams } from "react-router-dom";
import { FiAlertCircle } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetOverviewQuery } from "../dashboardApiSlice";
import {
  DEFAULT_PERIOD,
  PERIODS,
  findPeriod,
  periodSubtitle,
} from "../dashboardModel";
import DashboardShell from "../components/DashboardShell";
import {
  DashboardLoading,
  attentionPair,
  listRow,
} from "../components/DashboardPanels";
import ReceivablesCard from "../components/ReceivablesCard";
import OverviewTiles from "../components/OverviewTiles";
import CashFlowPanel from "../components/CashFlowPanel";
import AttentionPanel from "../components/AttentionPanel";
import TopClientsPanel from "../components/TopClientsPanel";
import TopItemsPanel from "../components/TopItemsPanel";
import ComingUpPanel from "../components/ComingUpPanel";
import Shortcuts from "../components/Shortcuts";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  pageSubtitle,
  pageTitle,
  retryButton,
} from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";

// What the overview draws from. An answer without these, as an older server
// sends, can't be shown.
const OVERVIEW_FIELDS = ["period", "totals", "owedNow", "trend"];

const periodPill =
  "inline-flex min-h-9 items-center justify-center whitespace-nowrap rounded-[11px] px-3.5 text-[13px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:px-2";

const DashboardPage = () => {
  useTitle("Business overview — Invoice Spot");
  const [params, setParams] = useSearchParams();
  const period = findPeriod(params.get("period")).key;
  const overviewQuery = useGetOverviewQuery(
    { currency: params.get("currency") || undefined, period },
    { refetchOnMountOrArgChange: 60 },
  );
  const answer = overviewQuery.data;
  const snapshot =
    answer && OVERVIEW_FIELDS.every((key) => answer[key]) ? answer : undefined;
  const refreshing = overviewQuery.isFetching;
  const hasError = overviewQuery.isError || (Boolean(answer) && !snapshot);
  // Until the chosen period's or currency's figures arrive, or when they
  // couldn't be loaded, the last ones stay on screen, dimmed.
  const stale = Boolean(snapshot) && !overviewQuery.currentData;
  const shownPeriod = snapshot && findPeriod(snapshot.period.key);
  const failure = !snapshot
    ? [
        "We couldn’t load your overview.",
        "Please try again to see your latest figures.",
      ]
    : stale && shownPeriod.key !== period
      ? [
          `We couldn’t load ${findPeriod(period).called}.`,
          `Showing ${shownPeriod.called}.`,
        ]
      : stale
        ? [
            `We couldn’t load the figures in ${params.get("currency") || snapshot.currencies[0]}.`,
            `Showing ${snapshot.currency}.`,
          ]
        : [
            "Some workspace data couldn’t be refreshed.",
            "Your last loaded figures are still shown. Try refreshing.",
          ];
  // Money is shown for one period and one currency at a time. The address
  // keeps what was chosen; the default period, and the first currency,
  // stay out of it. The currency switch appears only when there is more
  // than one.
  const search = (changes) => {
    const chosen = { period, currency: params.get("currency"), ...changes };
    const next = new URLSearchParams();
    if (chosen.period !== DEFAULT_PERIOD) next.set("period", chosen.period);
    if (chosen.currency && chosen.currency !== snapshot?.currencies[0]) {
      next.set("currency", chosen.currency);
    }
    return next.toString() ? `?${next}` : "";
  };
  const chooseCurrency = (currency) =>
    setParams(search({ currency }), { replace: true });

  const refresh = () => overviewQuery.refetch();

  return (
    <DashboardShell>
      <div className="flex min-w-0 flex-col gap-5">
        <header className="flex animate-ov-rise flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div className="min-w-0">
            <h1 className={pageTitle}>
              Business overview<span>.</span>
            </h1>
            {/* The words follow the figures shown, which can still be the
                last period's while the chosen one loads. */}
            <p className={pageSubtitle}>
              {snapshot
                ? periodSubtitle(snapshot.period, snapshot.totals.hasPrevious)
                : findPeriod(period).name}
            </p>
            <Shortcuts />
          </div>
          <div className="flex flex-wrap items-center gap-3 max-[700px]:w-full">
            <nav
              aria-label="Period"
              className="inline-flex gap-1 rounded-[14px] border border-white/90 bg-white/55 p-1 max-[700px]:w-full"
            >
              {PERIODS.map(({ key, label }) => (
                <Link
                  key={key}
                  to={{ search: search({ period: key }) }}
                  replace
                  aria-current={key === period ? "true" : undefined}
                  className={`${periodPill} ${key === period ? "bg-white text-spot-ink shadow-key" : "text-spot-nav hover:bg-white/60 hover:text-spot-night"}`}
                >
                  {label}
                </Link>
              ))}
            </nav>
            {snapshot?.currencies.length > 1 && (
              <label className="flex items-center gap-2 text-[13px] text-spot-body">
                Currency
                <Select
                  size="toolbar"
                  value={snapshot.currency}
                  onChange={chooseCurrency}
                >
                  {snapshot.currencies.map((currency) => (
                    <option key={currency} value={currency}>
                      {currency}
                    </option>
                  ))}
                </Select>
              </label>
            )}
          </div>
        </header>
        {hasError && (
          <div className={errorBanner} role="alert">
            <FiAlertCircle aria-hidden="true" />
            <div className="flex-1">
              <strong className={errorTitle}>{failure[0]}</strong>
              <span className={errorDetail}>{failure[1]}</span>
            </div>
            <button
              type="button"
              className={retryButton}
              onClick={refresh}
              disabled={refreshing}
            >
              Try again
            </button>
          </div>
        )}
        {overviewQuery.isLoading ? (
          <DashboardLoading />
        ) : (
          snapshot && (
            <div
              className={`flex min-w-0 flex-col gap-5 transition-opacity ${stale ? "opacity-60" : ""}`}
              aria-busy={stale || undefined}
            >
              <ReceivablesCard
                snapshot={snapshot}
                currencyChosen={Boolean(params.get("currency"))}
              />
              <OverviewTiles
                snapshot={snapshot}
                currencyChosen={Boolean(params.get("currency"))}
              />
              <CashFlowPanel snapshot={snapshot} />
              {/* A panel an older server's answer has no figures for is
                  left out, and the rest of its row takes its room. */}
              <div className={snapshot.topClients ? attentionPair : "contents"}>
                <AttentionPanel snapshot={snapshot} />
                {snapshot.topClients && (
                  <TopClientsPanel
                    snapshot={snapshot}
                    currencyChosen={Boolean(params.get("currency"))}
                  />
                )}
              </div>
              {(snapshot.topItems || snapshot.comingUp) && (
                <div className={listRow}>
                  {snapshot.topItems && (
                    <TopItemsPanel
                      snapshot={snapshot}
                      currencyChosen={Boolean(params.get("currency"))}
                    />
                  )}
                  {snapshot.comingUp && (
                    <ComingUpPanel
                      snapshot={snapshot}
                      currencyChosen={Boolean(params.get("currency"))}
                    />
                  )}
                </div>
              )}
            </div>
          )
        )}
      </div>
    </DashboardShell>
  );
};

export default DashboardPage;
