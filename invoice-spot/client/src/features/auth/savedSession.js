import { decodeToken } from "react-jwt";

// The session saved in this browser, or none when it can't be read: a value
// that isn't one, or whose token isn't a token, is dropped, so the app starts
// signed out instead of failing to load.
export const readSavedUser = (storage) => {
  try {
    const user = JSON.parse(storage.getItem("user"));
    if (user && typeof user === "object" && decodeToken(user.accessToken)) {
      return user;
    }
  } catch {
    // Dropped below, like any other value that isn't a session.
  }
  storage.removeItem("user");
  return null;
};

// The roles an access token carries, or none when it can't be read.
export const tokenRoles = (token) => {
  const roles = token ? decodeToken(token)?.roles : null;
  return Array.isArray(roles) ? roles : [];
};
