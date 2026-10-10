import React from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { store } from "./app/store";
import App from "./App";
import { authApiSlice } from "./features/auth/authApiSlice";
import { restoreSession } from "./features/auth/authSlice";
import ErrorBoundary from "./components/ErrorBoundary";
import { disableReactDevTools } from "@fvilers/disable-react-devtools";
import "./index.css";

import { BrowserRouter as Router, Route, Routes } from "react-router-dom";

if (import.meta.env.PROD) {
  disableReactDevTools();
}

// A reload keeps no access token: the session's cookie gives a new one.
restoreSession(store, () =>
  store.dispatch(
    authApiSlice.endpoints.refreshSession.initiate(undefined, { track: false }),
  ),
);

const container = document.getElementById("root");
const root = createRoot(container);

root.render(
  <React.StrictMode>
    <ErrorBoundary>
      <Provider store={store}>
        <Router>
          <Routes>
            <Route path="/*" element={<App />} />
          </Routes>
        </Router>
      </Provider>
    </ErrorBoundary>
  </React.StrictMode>,
);
