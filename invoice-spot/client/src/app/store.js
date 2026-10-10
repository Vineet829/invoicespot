import { configureStore } from "@reduxjs/toolkit";
import { baseApiSlice } from "../features/api/baseApiSlice";
import authReducer from "../features/auth/authSlice";
import businessReducer from "../features/business/businessSlice";

export const store = configureStore({
  reducer: {
    [baseApiSlice.reducerPath]: baseApiSlice.reducer,
    auth: authReducer,
    business: businessReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(baseApiSlice.middleware),
  devTools: false,
});
