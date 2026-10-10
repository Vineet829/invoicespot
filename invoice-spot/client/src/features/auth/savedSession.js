import { decodeToken } from "react-jwt";

// Who was signed in on this browser, or no one when it can't be read. Only
// who it was is kept, never the access token: a reload gets a new one through
// the session's cookie. A token an older version saved here is dropped.
export const readSavedUser = (storage) => {
  try {
    const user = JSON.parse(storage.getItem("user"));
    if (user && typeof user === "object" && typeof user.username === "string") {
      const profile = withoutToken(user);
      if ("accessToken" in user) {
        storage.setItem("user", JSON.stringify(profile));
      }
      return profile;
    }
  } catch {
    // Dropped below, like any other value that isn't a session.
  }
  storage.removeItem("user");
  return null;
};

// What a sign-in leaves in this browser: who it is, without the token.
export const saveUser = (storage, user) =>
  storage.setItem("user", JSON.stringify(withoutToken(user)));

const withoutToken = ({ accessToken, ...profile }) => profile;

// The roles an access token carries, or none when it can't be read.
export const tokenRoles = (token) => {
  const roles = token ? decodeToken(token)?.roles : null;
  return Array.isArray(roles) ? roles : [];
};
