// A GSTIN as the server checks it, for feedback while typing: 15 letters and
// digits, a state code first and a check character last. The server stays
// the judge; this only saves a round trip.
const ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

export const normalizeGstin = (value) =>
  String(value ?? "")
    .trim()
    .toUpperCase();

// Shaped like a GSTIN, whether or not its check character is right.
export const looksLikeGstin = (value) =>
  /^[0-9]{2}[0-9A-Z]{13}$/.test(normalizeGstin(value));

export const gstinCheckCharacter = (first14) => {
  let sum = 0;
  for (let index = 0; index < 14; index += 1) {
    const product =
      ALPHABET.indexOf(first14[index]) * (index % 2 === 0 ? 1 : 2);
    sum += Math.floor(product / 36) + (product % 36);
  }
  return ALPHABET[(36 - (sum % 36)) % 36];
};

export const isValidGstin = (value) => {
  const gstin = normalizeGstin(value);
  return (
    looksLikeGstin(gstin) &&
    gstinCheckCharacter(gstin.slice(0, 14)) === gstin[14]
  );
};

// What is wrong with a GSTIN being typed, once it can be told; nothing while
// it may still be right.
export const gstinProblem = (value) => {
  const gstin = normalizeGstin(value);
  if (!gstin) return null;
  if (/^[^0-9]|^[0-9][^0-9]/.test(gstin)) {
    return "A GSTIN starts with the two-digit state code.";
  }
  if (/[^0-9A-Z]/.test(gstin)) {
    return "A GSTIN has only letters and digits.";
  }
  if (gstin.length > 15) return "A GSTIN has 15 letters and digits.";
  if (gstin.length === 15 && !isValidGstin(gstin)) {
    return "Its last character doesn’t match the rest. Check it for a typing mistake.";
  }
  return null;
};
