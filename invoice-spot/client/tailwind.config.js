const plugin = require("tailwindcss/plugin");

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  // Older pages rely on their own stylesheets and on Material UI, which
  // Tailwind's global reset would restyle, so it stays off. src/index.css
  // applies the part of the reset that utilities need inside the new UI.
  corePlugins: { preflight: false },
  // Utilities must win over the legacy workspace CSS they sit next to.
  important: "#root",
  theme: {
    extend: {
      fontFamily: {
        display: ["Sora", "sans-serif"],
        ui: [
          "Geist",
          "IBM Plex Sans",
          "-apple-system",
          "BlinkMacSystemFont",
          "sans-serif",
        ],
      },
      colors: {
        // Invoice Spot's pastel workspace palette.
        spot: {
          ink: "#232735",
          text: "#3d4152",
          body: "#4f5566",
          nav: "#4a4659",
          sub: "#585c6b",
          muted: "#5d6170",
          faint: "#6b7080",
          disabled: "#a3a3b3",
          night: "#30234f",
          violet: "#5b47a0",
          brand: "#7c62d6",
          lilac: "#a996e0",
          coral: "#e8835f",
          blue: "#3f79b0",
          canvas: "#e6e5f0",
          drawer: "#f0eef8",
          thead: "#f3f2f8",
        },
        // The three financial summary cards.
        card: {
          blue: {
            DEFAULT: "#cde3f4",
            ink: "#1f2a3a",
            muted: "#3f4d62",
            decimal: "#5a6a80",
            label: "#2b3a4d",
            icon: "#2e5b86",
            bar: "#3f79b0",
            track: "#2337501f",
          },
          lilac: {
            DEFAULT: "#e5e1f8",
            ink: "#261f3d",
            muted: "#4a4262",
            decimal: "#665d87",
            label: "#33294f",
            icon: "#5b47a0",
            bar: "#5b47a0",
            track: "#3c2d6e1f",
          },
          peach: {
            DEFAULT: "#f9d9ca",
            ink: "#3a2218",
            muted: "#5e3a2c",
            decimal: "#8a5d4d",
            label: "#4d2d20",
            icon: "#a4502f",
            bar: "#c9643f",
            track: "#783c231f",
          },
        },
        // Initials avatars.
        avatar: {
          lilac: "#e5e1f8",
          "lilac-ink": "#4a3a80",
          blue: "#d5e6f5",
          "blue-ink": "#2f5e86",
          peach: "#f9dccf",
          "peach-ink": "#8a4a2f",
          sage: "#e1ecdc",
          "sage-ink": "#40603a",
        },
        overdue: {
          DEFAULT: "#fbe3d8",
          ink: "#5a2616",
          muted: "#7a4a38",
          icon: "#b0502e",
          // Small text on peach tints; icon is too light for it.
          strong: "#9c4526",
        },
      },
      backgroundImage: {
        canvas:
          "radial-gradient(ellipse at 8% 0%, #eef0fb 0, #eef0fb00 45%), radial-gradient(ellipse at 95% 12%, #d6e6f3 0, #d6e6f300 42%), radial-gradient(ellipse at 72% 100%, #f1dcd2 0, #f1dcd200 45%), radial-gradient(ellipse at 20% 95%, #ddd3ee 0, #ddd3ee00 50%)",
      },
      boxShadow: {
        panel: "0 30px 60px -40px #463e7873",
        "panel-soft": "0 30px 60px -40px #463e7866",
        nav: "0 10px 24px -14px #3c346473, inset 0 0 0 1px #ffffffe6",
        float: "0 18px 40px -20px #3c346473",
        key: "0 6px 14px -8px #3c346480",
      },
      keyframes: {
        "ov-rise": { from: { opacity: "0", transform: "translateY(10px)" } },
        "ov-bar": { from: { transform: "scaleX(0)" } },
        "ov-ring": { from: { strokeDashoffset: "100" } },
        "ov-roll": {
          from: { transform: "translateY(400%)" },
          to: { transform: "translateY(0)" },
        },
        "ov-pulse": { to: { opacity: "0.4" } },
        "ov-fade": { from: { opacity: "0" } },
      },
      animation: {
        // Panels rise in sequence; set --rise-delay per panel.
        "ov-rise":
          "ov-rise 0.7s var(--rise-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-bar": "ov-bar 1.1s 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-ring":
          "ov-ring 1.4s var(--ring-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-roll":
          "ov-roll 1.4s calc(300ms + var(--digit-delay, 0ms)) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-pulse": "ov-pulse 1.6s ease-in-out infinite alternate",
        "ov-fade": "ov-fade 0.12s ease-out both",
      },
    },
  },
  plugins: [
    // Registered as variants rather than screens: custom screens would switch
    // off Tailwind's min-[…] and max-[…] breakpoint variants.
    plugin(({ addVariant }) => {
      // A desktop window below 900px tall (a laptop, or a 1080p screen at
      // 125% zoom) keeps the desktop layout with tighter spacing.
      addVariant(
        "compact",
        "@media (min-width: 1360px) and (max-height: 900px)",
      );
      addVariant("coarse", "@media (pointer: coarse)");
      // Workspace tables become labelled cards when their panel is too
      // narrow for every column, whatever the window size or zoom. Tables
      // with eight or more columns need more room.
      addVariant("stacked", [
        "@container table (max-width: 860px)",
        "@container wide-table (max-width: 960px)",
      ]);
      // Document line items: three columns in a narrow form, then two.
      addVariant("items-md", "@container items (max-width: 720px)");
      addVariant("items-sm", "@container items (max-width: 480px)");
    }),
  ],
};
