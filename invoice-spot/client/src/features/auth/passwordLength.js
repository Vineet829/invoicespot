// Passwords are kept as bcrypt hashes, which hold at most 72 bytes of one:
// an English letter is a byte, an accented letter two, a letter of most
// other scripts three. The server refuses a longer password with this same
// message (PasswordPolicy.TOO_LONG).
export const MAX_PASSWORD_BYTES = 72;

export const PASSWORD_TOO_LONG =
  "Password can be at most 72 characters long, or fewer with letters outside the English alphabet";

export const fitsPassword = (value) =>
  new TextEncoder().encode(value ?? "").length <= MAX_PASSWORD_BYTES;
