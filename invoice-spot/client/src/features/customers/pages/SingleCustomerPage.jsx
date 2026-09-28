import { useParams } from "react-router-dom";
import { FiEdit2 } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetSingleCustomerQuery } from "../customersApiSlice";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  InfoGrid,
  LoadingState,
  ErrorState,
} from "../../../components/workspace/Workspace";
const SingleCustomerPage = () => {
  const { custId } = useParams();
  const query = useGetSingleCustomerQuery(custId);
  const customer = query.data?.customer;
  useTitle(`${customer?.name || "Client details"} — Invoice Spot`);
  return (
    <WorkspacePage section="Clients">
      <PageHeader
        back
        title="Client details"
        description="Contact and billing information, together."
        actions={
          customer && (
            <ActionLink to={`/edit-customer/${custId}`}>
              <FiEdit2 aria-hidden="true" /> Edit client
            </ActionLink>
          )
        }
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        customer && (
          <Panel>
            <div className="mb-6 flex items-center gap-5 border-b border-[#23273512] pb-6 max-[700px]:gap-4">
              <span
                className="grid size-16 shrink-0 place-items-center rounded-[20px] bg-avatar-lilac font-display text-[22px] font-light text-avatar-lilac-ink max-[700px]:size-14 max-[700px]:rounded-[18px] max-[700px]:text-[19px]"
                aria-hidden="true"
              >
                {customer.name
                  ?.split(/\s+/)
                  .slice(0, 2)
                  .map((part) => part[0])
                  .join("")}
              </span>
              <div className="min-w-0">
                <h2 className="font-display text-[28px] font-light leading-[1.15] tracking-[-0.04em] text-spot-ink [overflow-wrap:anywhere] max-[700px]:text-[23px]">
                  {customer.name}
                </h2>
                <p className="mt-1 text-[13.5px] text-spot-muted [overflow-wrap:anywhere]">
                  {customer.accountNo}
                </p>
              </div>
            </div>
            <InfoGrid
              items={[
                ["Email address", customer.email],
                ["Phone number", customer.phoneNumber],
                ["VAT / TIN number", customer.vatTinNo],
                ["Address", customer.address],
                ["City", customer.city],
                ["Country", customer.country],
              ]}
            />
          </Panel>
        )
      )}
    </WorkspacePage>
  );
};
export default SingleCustomerPage;
