import { useSelector } from "react-redux";
import {
  selectCurrentUserToken,
  selectCurrentUserGoogleToken,
} from "../features/auth/authSlice";
import { tokenRoles } from "../features/auth/savedSession";

// The signed-in user's roles, read from their access token; none when there
// is no token or it can't be read, which sends them to sign in.
const useAuthUser = () => {
  const token = useSelector(selectCurrentUserToken);
  const googleToken = useSelector(selectCurrentUserGoogleToken);
  const roles = tokenRoles(token || googleToken);
  const isAdmin = roles.includes("Admin");
  return { roles, isAdmin, accessRight: isAdmin ? "Admin" : "User" };
};

export default useAuthUser;
