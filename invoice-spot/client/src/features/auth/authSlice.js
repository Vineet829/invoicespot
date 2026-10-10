import { createSlice } from "@reduxjs/toolkit";
import { readSavedUser, saveUser } from "./savedSession";

// The access token lives only here, in memory. A reload starts without it
// and, when someone was signed in, gets it back through the session's cookie
// before any page draws.
const initialState = {
  user: null,
  restoring: readSavedUser(localStorage) !== null,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    logIn: (state, action) => {
      state.user = action.payload;
      state.restoring = false;
      saveUser(localStorage, action.payload);
    },
    logOut: (state, action) => {
      state.user = null;
      state.restoring = false;
      localStorage.removeItem("user");
    },
    // The server couldn't answer: this load starts signed out, and the next
    // one tries again.
    restoreFailed: (state) => {
      state.restoring = false;
    },
  },
});

export const { logIn, logOut, restoreFailed } = authSlice.actions;

export default authSlice.reducer;

export const selectCurrentUserToken = (state) => state.auth.user?.accessToken;

// Swaps the session's cookie for an access token after a reload. A cookie the
// server refuses signs out; a server that can't answer leaves who was signed
// in for the next load.
export const restoreSession = async ({ dispatch, getState }, refresh) => {
  if (!getState().auth.restoring) return;
  let result;
  try {
    result = await refresh();
  } catch (error) {
    result = { error };
  }
  if (result?.data) {
    dispatch(logIn({ ...result.data }));
    return;
  }
  const status = result?.error?.originalStatus ?? result?.error?.status;
  dispatch(status === 401 || status === 403 ? logOut() : restoreFailed());
};
