import { createSlice } from "@reduxjs/toolkit";
import { logOut } from "../auth/authSlice";

const initialState = { activeId: null, changedElsewhere: false };

// The business this tab works in. The server keeps the choice for the
// session; the tab sends it with each request, so a tab still showing one
// business never writes into another.
const businessSlice = createSlice({
  name: "business",
  initialState,
  reducers: {
    setActiveBusiness: (state, action) => {
      state.activeId = action.payload;
      state.changedElsewhere = false;
    },
    // The server says the session now works in another business, or none.
    businessChanged: (state, action) => {
      state.activeId = action.payload;
      state.changedElsewhere = true;
    },
    changeNoticed: (state) => {
      state.changedElsewhere = false;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(logOut, () => initialState);
  },
});

export const { setActiveBusiness, businessChanged, changeNoticed } =
  businessSlice.actions;

export default businessSlice.reducer;
