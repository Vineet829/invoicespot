import { FiChevronDown, FiChevronUp, FiMinus } from "react-icons/fi";
import { Amount, money } from "../../../components/workspace/Workspace";
import { dateSpan } from "../../../utils/dates";
import { formatAmount } from "../../../utils/money";
import { changeFrom, percentLabel } from "../../dashboard/dashboardModel";

// How a total moved, as the overview's cards show it: an arrow and the size
// of the change, green when it rose and rust when it fell.
const CHANGES = {
  up: { icon: FiChevronUp, color: "text-change-up", words: "up" },
  down: { icon: FiChevronDown, color: "text-change-down", words: "down" },
  flat: { icon: FiMinus, color: "text-spot-muted", words: "no change" },
};

// A summary's headline: what it adds up, its total, what the total is on
// and, when the period has a period before it, that period's total, with
// the change from it unless it was zero. Amounts name their currency when
// the business bills in more than one.
const SummaryFigure = ({
  label,
  total,
  currency,
  several,
  detail,
  period,
  previousTotal,
}) => {
  const compared =
    previousTotal != null && period?.previousFrom && period?.previousTo;
  const change = compared ? changeFrom(total, previousTotal) : null;
  const { icon: Icon, color, words } = CHANGES[change?.direction || "flat"];
  return (
    <div className="min-w-0">
      <p className="text-[13px] text-spot-body">{label}</p>
      <p className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <Amount
          value={total}
          currency={currency}
          hideCode={!several}
          className="text-[36px] font-extralight tracking-[-0.04em] text-spot-ink"
        />
        {detail && (
          <span className="text-[13px] text-spot-muted">{detail}</span>
        )}
      </p>
      {compared && (
        <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12.5px] text-spot-muted">
          {change && (
            <span
              className={`inline-flex items-center gap-[3px] rounded-full bg-[#2327350d] py-[3px] pl-[5px] pr-2 font-medium leading-[1.3] ${color}`}
            >
              <Icon aria-hidden="true" className="size-3.5 stroke-[2.2]" />
              <span className="sr-only">{words} </span>
              {percentLabel(change.percent)}
            </span>
          )}
          <span>
            vs{" "}
            {several
              ? money(previousTotal, currency)
              : formatAmount(previousTotal, currency)}{" "}
            in {dateSpan(period.previousFrom, period.previousTo)}
          </span>
        </p>
      )}
    </div>
  );
};

export default SummaryFigure;
