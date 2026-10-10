// An HSN code (goods) or SAC code (services): 4 to 8 digits, spaces dropped,
// as the server keeps it. The server stays the judge; this only saves a
// round trip.
export const normalizeHsnSac = (value) =>
  String(value ?? "").replace(/\s+/g, "");

export const isValidHsnSac = (value) =>
  /^[0-9]{4,8}$/.test(normalizeHsnSac(value));

// What an input accepts before its form is sent.
export const HSN_SAC_PATTERN = "\\s*(\\d\\s*){4,8}";
