import { useCallback } from "react";
import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { baseApiSlice } from "../api/baseApiSlice";
import { setActiveBusiness } from "./businessSlice";

// Starts over in another business: nothing loaded for the one left behind
// may show, so every cached answer goes and the app opens on its overview.
const useEnterBusiness = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  return useCallback(
    (businessId) => {
      dispatch(setActiveBusiness(businessId));
      dispatch(baseApiSlice.util.resetApiState());
      navigate("/dashboard");
    },
    [dispatch, navigate],
  );
};

export default useEnterBusiness;
