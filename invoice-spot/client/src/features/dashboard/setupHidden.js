// Whether the overview's setup steps were hidden in a business, on this
// browser. Without storage they show, and hiding them lasts until the page
// is left.
const key = (businessId) => `setupHidden:${businessId}`;

export const setupHidden = (businessId) => {
  try {
    return localStorage.getItem(key(businessId)) === "1";
  } catch {
    return false;
  }
};

export const hideSetup = (businessId) => {
  try {
    localStorage.setItem(key(businessId), "1");
  } catch {
    // Hidden until the page is left.
  }
};
