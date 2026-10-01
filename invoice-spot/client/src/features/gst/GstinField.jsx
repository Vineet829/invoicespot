import { Field } from "../../components/workspace/Workspace";
import { gstinProblem, isValidGstin, normalizeGstin } from "./gstin";
import { useGetGstStatesQuery } from "./gstApiSlice";

// A client's GSTIN, checked as it is typed: the state it was issued in once it
// is valid, or what is wrong once that can be told.
const GstinField = ({ value, ...props }) => {
  const states = useGetGstStatesQuery().data?.states;
  const gstin = normalizeGstin(value);
  const problem = gstinProblem(value);
  const state =
    isValidGstin(gstin) &&
    states?.find((entry) => entry.code === gstin.slice(0, 2));
  return (
    <Field
      label="GSTIN"
      autoComplete="off"
      maxLength={15}
      value={value}
      error={problem || undefined}
      hint={
        state
          ? `Registered in ${state.name} (${state.code}).`
          : "The client’s Indian GST number, printed on your documents."
      }
      {...props}
    />
  );
};

export default GstinField;
