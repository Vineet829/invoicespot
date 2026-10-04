const plugin = require("tailwindcss/plugin");

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  // Tailwind's global reset stays off; src/index.css supplies the parts of
  // it that the pages rely on.
  corePlugins: { preflight: false },
  // Utilities must win over that reset, which is scoped to .dashboard-app
  // and so outranks a single class.
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
        mono: ["IBM Plex Mono", "monospace"],
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
        // The financial cards, and the overview's tiles.
        card: {
          blue: {
            DEFAULT: "#cde3f4",
            ink: "#1f2a3a",
            muted: "#3f4d62",
            decimal: "#5a6a80",
            label: "#2b3a4d",
            icon: "#2e5b86",
            bar: "#3f79b0",
          },
          lilac: {
            DEFAULT: "#e5e1f8",
            ink: "#261f3d",
            muted: "#4a4262",
            decimal: "#665d87",
            label: "#33294f",
            icon: "#5b47a0",
            bar: "#5b47a0",
          },
          peach: {
            DEFAULT: "#f9d9ca",
            ink: "#3a2218",
            muted: "#5e3a2c",
            decimal: "#8a5d4d",
            label: "#4d2d20",
            icon: "#a4502f",
            bar: "#c9643f",
          },
          // The overview's profit tile. A loss is darker as well as
          // warmer, so colour-blind readers can still tell it apart.
          sage: {
            DEFAULT: "#dcebd6",
            ink: "#1f3320",
            muted: "#3f5a3a",
            decimal: "#4f6a4a",
            label: "#233621",
            bar: "#4f8a4a",
            loss: "#8c3b1f",
          },
        },
        // A figure's change against the period before. Each also has an
        // arrow and words, so colour is never the only sign.
        change: {
          up: "#2f6b2c",
          down: "#a4502f",
        },
        // Money owed, by whether it is due yet.
        owed: {
          due: "#a99fd6",
          overdue: "#b5532f",
        },
        // The overview's cash flow: money in is spot violet, money out
        // peach, and what was left the dark net line.
        flow: {
          out: "#d9774f",
          net: "#2d3142",
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
        "ov-column": { from: { transform: "scaleY(0)" } },
        "ov-ring": { from: { strokeDashoffset: "100" } },
        "ov-roll": {
          from: { transform: "translateY(400%)" },
          to: { transform: "translateY(0)" },
        },
        "ov-pulse": { to: { opacity: "0.4" } },
        "ov-fade": { from: { opacity: "0" } },
        // Log in and sign up. Entrances move with `translate`, so hover
        // transforms still apply.
        "auth-rise": {
          from: { opacity: "0", translate: "0 var(--rise, 12px)" },
        },
        "auth-sheen": { to: { translate: "100% 0" } },
        "auth-divider": {
          from: { backgroundPosition: "200% 0" },
          to: { backgroundPosition: "-200% 0" },
        },
        "auth-glow-1": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.6" },
          "50%": {
            transform: "translate(20px, 15px) scale(1.15)",
            opacity: "1",
          },
        },
        "auth-glow-2": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.4" },
          "50%": {
            transform: "translate(-15px, -20px) scale(1.1)",
            opacity: "0.8",
          },
        },
        "auth-glow-3": {
          "0%, 100%": { transform: "translate(0, 0) scale(1)", opacity: "0.3" },
          "50%": {
            transform: "translate(10px, -25px) scale(1.2)",
            opacity: "0.6",
          },
        },
        "auth-grid": { to: { backgroundPosition: "60px 60px" } },
        "auth-streak": {
          "0%": { left: "-20%", opacity: "0" },
          "20%, 80%": { opacity: "1" },
          "100%": { left: "110%", opacity: "0" },
        },
        "auth-float": {
          "0%, 100%": { transform: "translate(0, 0)", opacity: "0.15" },
          "25%": { opacity: "0.5" },
          "50%": {
            transform: "translate(var(--dx), var(--dy))",
            opacity: "0.3",
          },
          "75%": { opacity: "0.6" },
        },
        "auth-grow": { from: { width: "0" } },
        "auth-wave": {
          "0%": { translate: "-100% 0" },
          "55%, 100%": { translate: "100% 0" },
        },
        "auth-dot": {
          "0%": { left: "0", opacity: "0" },
          "5%, 95%": { opacity: "1" },
          "100%": { left: "calc(100% - 12px)", opacity: "0" },
        },
        "auth-row": { from: { opacity: "0", translate: "-8px 0" } },
        "auth-toast": {
          "0%": { opacity: "0", translate: "0 8px" },
          "12%, 85%": { opacity: "1", translate: "0 0" },
          "100%": { opacity: "0", translate: "0 -4px" },
        },
      },
      animation: {
        // Panels rise in sequence; set --rise-delay per panel.
        "ov-rise":
          "ov-rise 0.7s var(--rise-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-bar": "ov-bar 1.1s 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) both",
        // Chart columns grow from the baseline; set --column-delay per column.
        "ov-column":
          "ov-column 0.9s var(--column-delay, 300ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-ring":
          "ov-ring 1.4s var(--ring-delay, 0ms) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-roll":
          "ov-roll 1.4s calc(300ms + var(--digit-delay, 0ms)) cubic-bezier(0.2, 0.7, 0.2, 1) both",
        "ov-pulse": "ov-pulse 1.6s ease-in-out infinite alternate",
        "ov-fade": "ov-fade 0.12s ease-out both",
        // Content rises in sequence; set --d (and --rise) per element.
        "auth-rise":
          "auth-rise 0.6s cubic-bezier(0.16, 1, 0.3, 1) var(--d, 0s) backwards",
        "auth-sheen": "auth-sheen 0.7s ease forwards",
        "auth-divider": "auth-divider 4s linear infinite",
        "auth-glow-1": "auth-glow-1 8s ease-in-out infinite",
        "auth-glow-2": "auth-glow-2 10s ease-in-out infinite",
        "auth-glow-3": "auth-glow-3 12s ease-in-out infinite",
        "auth-grid": "auth-grid 25s linear infinite",
        "auth-streak": "auth-streak 6s ease-in-out infinite",
        "auth-streak-late": "auth-streak 6s ease-in-out -3s infinite",
        "auth-float": "auth-float var(--dur) ease-in-out infinite",
        // The loader fills from 0.8s to 2.6s, then its sheen repeats.
        "auth-fill":
          "auth-grow 1.8s cubic-bezier(0.4, 0, 0.2, 1) 0.8s backwards",
        "auth-dot": "auth-dot 1.8s cubic-bezier(0.4, 0, 0.2, 1) 0.8s",
        "auth-loader-wave": "auth-wave 1.8s ease-in-out 2.6s infinite",
        // The sample window: rows slide in, values grow, then a shimmer
        // repeats; set --rd, --gd and --sd per element.
        "auth-row": "auth-row 0.45s ease var(--rd, 1.5s) backwards",
        "auth-grow":
          "auth-grow 0.7s cubic-bezier(0.16, 1, 0.3, 1) var(--gd, 1.4s) backwards",
        "auth-shimmer":
          "auth-wave 2.2s ease-in-out calc(1.9s + var(--sd, 0s)) infinite",
        "auth-fade": "ov-fade 0.6s ease 1.8s backwards",
        "auth-toast": "auth-toast 2.6s ease 3.2s",
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
      // with eight or more columns need more room; narrow ones, with a few
      // short columns, stay tables in half-width panels.
      addVariant("stacked", [
        "@container table (max-width: 860px)",
        "@container wide-table (max-width: 960px)",
        "@container narrow-table (max-width: 400px)",
      ]);
      // Document line items: one row each in a wide form, four columns once
      // the form is too narrow for all eight fields, then two.
      addVariant("items-md", "@container items (max-width: 900px)");
      addVariant("items-sm", "@container items (max-width: 480px)");
    }),
  ],
};
