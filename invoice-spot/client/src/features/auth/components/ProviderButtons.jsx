import { useEffect, useState } from "react";
import { API_BASE } from "../../api/apiBase";
import { useGetSignInProvidersQuery } from "../authApiSlice";
import { GoogleIcon } from "./authCommon";
import { auth } from "./authForm";

// One button per sign-in provider the server is set up for, such as Google.
// Until the list arrives, or if it can't be loaded, there is nothing to show,
// so the buttons beside these keep the row to themselves.
const ProviderButtons = () => {
  const { data, isSuccess } = useGetSignInProvidersQuery();
  const [leaving, setLeaving] = useState(null);

  // Back from the provider's page can restore this page from the browser's
  // back/forward cache with the button still off, so turn it back on.
  useEffect(() => {
    const onPageShow = (event) => {
      if (event.persisted) setLeaving(null);
    };
    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, []);

  if (!isSuccess || !data?.providers?.length) return null;

  return data.providers.map(({ id, name }) => (
    <button
      key={id}
      type="button"
      className={`${auth.ghost} ${auth.tight}`}
      onClick={() => {
        // The provider's own page takes over from here, so the button
        // stays off while the browser leaves.
        setLeaving(id);
        window.location.assign(`${API_BASE}/auth/oauth2/authorization/${id}`);
      }}
      disabled={leaving === id}
    >
      {id === "google" && <GoogleIcon />}
      {name}
    </button>
  ));
};

export default ProviderButtons;
