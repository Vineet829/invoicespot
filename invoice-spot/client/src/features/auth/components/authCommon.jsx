export const GUEST_CREDENTIALS = {
  email: "guest@demo.invoicespot.local",
  password: "Guest1!demo",
};

export const GoogleIcon = () => (
  <svg viewBox="0 0 18 18" fill="none" aria-hidden="true">
    <path
      d="M16.5 9.19c0-.63-.06-1.24-.16-1.82H9v3.44h4.2a3.6 3.6 0 01-1.56 2.36v1.96h2.52c1.48-1.36 2.34-3.36 2.34-5.94z"
      fill="#4285F4"
    />
    <path
      d="M9 17c2.1 0 3.86-.7 5.16-1.87l-2.52-1.96c-.7.47-1.59.74-2.64.74-2.03 0-3.75-1.37-4.36-3.21H2.04v2.02A7.99 7.99 0 009 17z"
      fill="#34A853"
    />
    <path
      d="M4.64 10.7a4.8 4.8 0 010-3.4V5.28H2.04a8 8 0 000 7.44l2.6-2.02z"
      fill="#FBBC05"
    />
    <path
      d="M9 3.58c1.14 0 2.17.4 2.98 1.17l2.24-2.24A7.96 7.96 0 009 1 7.99 7.99 0 002.04 5.28l2.6 2.02C5.25 5.95 6.97 3.58 9 3.58z"
      fill="#EA4335"
    />
  </svg>
);

export const GitHubIcon = () => (
  <svg viewBox="0 0 18 18" fill="currentColor" aria-hidden="true">
    <path d="M9 1.5a7.5 7.5 0 00-2.37 14.61c.37.07.51-.16.51-.36v-1.26c-2.1.46-2.54-1.01-2.54-1.01-.34-.87-.84-1.1-.84-1.1-.69-.47.05-.46.05-.46.76.05 1.16.78 1.16.78.67 1.15 1.76.82 2.19.63.07-.49.26-.82.48-1.01-1.67-.19-3.43-.84-3.43-3.73 0-.82.3-1.5.78-2.02-.08-.19-.34-.96.07-2 0 0 .63-.2 2.07.77a7.2 7.2 0 013.76 0c1.44-.97 2.07-.77 2.07-.77.41 1.04.15 1.81.07 2 .49.53.78 1.2.78 2.02 0 2.9-1.77 3.54-3.45 3.72.27.24.52.7.52 1.41v2.1c0 .2.14.43.52.36A7.5 7.5 0 009 1.5z" />
  </svg>
);

export const GuestIcon = () => (
  <svg
    viewBox="0 0 18 18"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    aria-hidden="true"
  >
    <circle cx="9" cy="7" r="3" />
    <path d="M3 16c0-3.3 2.7-6 6-6s6 2.7 6 6" />
  </svg>
);

export const EyeIcon = ({ slashed }) => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    aria-hidden="true"
  >
    <path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z" />
    <circle cx="8" cy="8" r="2" />
    {slashed && <path d="M2.5 2.5l11 11" />}
  </svg>
);

export const ArrowIcon = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.5"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M3 8h10M9 4l4 4-4 4" />
  </svg>
);

export const MailIcon = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.3"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <rect x="2" y="3.5" width="12" height="9" rx="1.5" />
    <path d="M2.5 4.5 8 8.5l5.5-4" />
  </svg>
);
