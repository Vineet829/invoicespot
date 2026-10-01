import { Link } from "react-router-dom";
import { FiArrowRight } from "react-icons/fi";
import {
  Amount,
  Panel,
  dateLabel,
} from "../../../components/workspace/Workspace";
import { rowAction } from "../../../components/workspace/pastel";
import { useGetWaitingPaymentClaimsQuery } from "../documentsApiSlice";

// Payments clients reported from their share pages, waiting to be confirmed.
// Each leads to its invoice, where it is confirmed or dismissed.
const WaitingPayments = () => {
  const claims = useGetWaitingPaymentClaimsQuery().data?.claims || [];
  if (claims.length === 0) return null;
  return (
    <Panel
      title="Payments to confirm"
      description="Clients reported paying these. Nothing is recorded until you confirm."
    >
      <ul className="m-0 flex list-none flex-col p-0">
        {claims.map((claim) => (
          <li
            key={claim.id}
            className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-[#23273512] py-3 text-[14px] text-spot-body last:border-b-0"
          >
            <span className="min-w-0">
              {claim.client || "A client"} reported{" "}
              <Amount value={claim.amount} currency={claim.currency} /> by{" "}
              {claim.methodLabel} for {claim.documentNumber}, paid{" "}
              {dateLabel(claim.paidOn)}
            </span>
            <Link className={rowAction} to={`/document/${claim.documentId}`}>
              Review <FiArrowRight aria-hidden="true" />
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  );
};

export default WaitingPayments;
