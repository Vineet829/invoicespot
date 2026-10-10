import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

// A list's search, filters, sort and page, kept in the URL so a reload, a
// shared link or the back button shows the same list. `read` turns search
// params into the state and `write` turns a state back into params; both
// must be module-level functions. A change rewrites the URL in place, so
// the back button leaves the list rather than stepping through each filter.
const useListState = (read, write) => {
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => read(params), [params, read]);
  const update = useCallback(
    (changes) =>
      setParams((current) => write({ ...read(current), ...changes }), {
        replace: true,
      }),
    [setParams, read, write],
  );
  return [state, update];
};

export default useListState;
