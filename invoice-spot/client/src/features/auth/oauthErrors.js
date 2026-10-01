// What went wrong when signing in with a provider such as Google, from the
// oauth_error code the server adds when it sends the browser back to /login.
export const oauthErrorMessage = (code, providerName = "Google") => {
  switch (code) {
    case "cancelled":
      return `You cancelled signing in with ${providerName}.`;
    case "expired":
      return "The sign-in took too long or was interrupted. Please try again.";
    case "unverified_email":
      return `${providerName} hasn't verified that email address, so it can't be used to sign in.`;
    case "demo_account":
      return "The demo account opens only with the Guest button.";
    case "deactivated":
      return "This account has been deactivated. Please contact us.";
    case "already_linked":
      return `This account is already linked to a different ${providerName} account.`;
    // "failed", and any code this version of the app doesn't know yet.
    default:
      return `Signing in with ${providerName} didn't work. Please try again.`;
  }
};
