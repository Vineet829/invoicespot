// The number the server hands out next, as long as it isn't taken.
export const numberPreview = (prefix, next) =>
  `${prefix || ""}${String(Math.max(1, Number(next) || 1)).padStart(4, "0")}`;
