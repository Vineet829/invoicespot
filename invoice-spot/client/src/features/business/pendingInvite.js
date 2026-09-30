// An invitation opened before signing in. Logging in leads back to it, even
// after creating an account and verifying its email in between. It is kept
// no longer than an invitation stays open.
const KEY = "pendingInvite";
const OPEN_FOR = 7 * 24 * 60 * 60 * 1000;

export const rememberInvite = (token) => {
  try {
    localStorage.setItem(KEY, JSON.stringify({ token, savedAt: Date.now() }));
  } catch {
    // Without storage, the emailed link still leads back to the invitation.
  }
};

export const forgetInvite = () => {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Nothing could have been kept.
  }
};

// The page of the invitation waiting for a sign-in, if there is one.
export const pendingInvitePath = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    if (
      typeof saved?.token === "string" &&
      Date.now() - saved.savedAt < OPEN_FOR
    ) {
      return `/invite/${encodeURIComponent(saved.token)}`;
    }
  } catch {
    // An unreadable entry leads nowhere.
  }
  return null;
};
