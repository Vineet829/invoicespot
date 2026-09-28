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
            <div className="workspace-identity">
              <span aria-hidden="true">
                {customer.name
                  ?.split(/\s+/)
                  .slice(0, 2)
                  .map((part) => part[0])
                  .join("")}
              </span>
              <div>
                <h2>{customer.name}</h2>
                <p>{customer.accountNo}</p>
              </div>
            </div>
            <InfoGrid
              items={[
                ["Name", customer.name],
                ["Email address", customer.email],
                ["Phone number", customer.phoneNumber],
                ["Account number", customer.accountNo],
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
