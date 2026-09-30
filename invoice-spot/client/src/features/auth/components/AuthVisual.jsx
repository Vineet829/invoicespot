import { useEffect, useRef } from "react";

// Each card uses one of the overview's financial summary colours.
const STAT_CARDS = [
  {
    label: "40%",
    value: "62%",
    sd: "0s",
    gd: "1.4s",
    tone: "bg-card-blue",
    bar: "bg-card-blue-bar/30",
  },
  {
    label: "34%",
    value: "48%",
    sd: "0.15s",
    gd: "1.5s",
    tone: "bg-card-lilac",
    bar: "bg-card-lilac-bar/30",
  },
  {
    label: "44%",
    value: "70%",
    sd: "0.3s",
    gd: "1.6s",
    tone: "bg-card-peach",
    bar: "bg-card-peach-bar/30",
  },
];

const TABLE_ROWS = [
  { a: "40%", b: "20%", status: "paid", rd: "1.55s", sd: "0.45s" },
  { a: "32%", b: "26%", status: "pending", rd: "1.65s", sd: "0.6s" },
  { a: "46%", b: "16%", status: "draft", rd: "1.75s", sd: "0.75s" },
];

// The overview's status colours: violet, lilac and coral.
const BADGES = {
  paid: "bg-spot-violet/30",
  pending: "bg-spot-lilac/45",
  draft: "bg-spot-coral/35",
};

const PILLS = [
  {
    label: "Invoices",
    icon: (
      <>
        <rect x="1.5" y="2.5" width="9" height="7" rx="1.5" />
        <path d="M1.5 5h9" />
      </>
    ),
  },
  {
    label: "PDF Email",
    icon: (
      <>
        <path d="M1.5 4l4.5 3 4.5-3" />
        <rect x="1.5" y="3" width="9" height="6" rx="1.5" />
      </>
    ),
  },
  {
    label: "Payments",
    icon: (
      <>
        <circle cx="6" cy="6" r="4.5" />
        <path d="M4 6.5l1.5 1.5 3-3" />
      </>
    ),
  },
  {
    label: "Customers",
    icon: (
      <>
        <circle cx="6" cy="4.5" r="2" />
        <path d="M2 11c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      </>
    ),
  },
];

const skeleton =
  "relative block w-[var(--w,50%)] shrink-0 overflow-hidden rounded-[3px] after:absolute after:inset-0 after:animate-auth-shimmer after:bg-[linear-gradient(90deg,transparent,#ffffffcc,transparent)] after:content-[''] after:[translate:-100%_0]";
const glow = "pointer-events-none absolute rounded-full blur-[100px]";
const streak = "pointer-events-none absolute left-[-10%] h-px rotate-[-15deg]";

const ui = {
  panel:
    "relative flex min-h-0 min-w-0 flex-col overflow-hidden rounded-[30px] border border-white/80 bg-[linear-gradient(160deg,#e2dcf7_0%,#e9e6f6_60%,#e0e8f5_100%)] px-[clamp(32px,4vw,72px)] py-9 shadow-panel max-[900px]:hidden [@media(max-height:699px)_and_(min-width:901px)]:overflow-y-auto",
  gridLines:
    "pointer-events-none absolute inset-0 animate-auth-grid bg-[linear-gradient(#5b47a080_1px,transparent_1px),linear-gradient(90deg,#5b47a080_1px,transparent_1px)] bg-[length:60px_60px] opacity-[.06]",
  glow1: `${glow} left-[-8%] top-[4%] size-[380px] animate-auth-glow-1 bg-[#b9a6f099]`,
  glow2: `${glow} bottom-[6%] right-[-6%] size-[280px] animate-auth-glow-2 bg-[#9fc5e8b3]`,
  glow3: `${glow} left-[42%] top-[48%] size-[220px] animate-auth-glow-3 bg-[#f4c3ad99]`,
  streak1: `${streak} top-[30%] w-[300px] animate-auth-streak bg-[linear-gradient(90deg,transparent,#7c62d659,transparent)]`,
  streak2: `${streak} top-[66%] w-[200px] animate-auth-streak-late bg-[linear-gradient(90deg,transparent,#a996e066,transparent)]`,
  particle:
    "pointer-events-none absolute animate-auth-float rounded-full bg-spot-violet/50",
  content:
    "relative z-[1] m-auto flex w-full max-w-[460px] flex-col min-[901px]:[zoom:1.4]",
  intro: "flex flex-col gap-6",
  headline:
    "text-balance font-display text-[46px] font-extralight leading-[1.04] tracking-[-0.055em] text-spot-ink",
  word: "inline-block animate-auth-rise",
  accent: "inline-block animate-auth-rise text-spot-brand",
  subtext:
    "max-w-[360px] animate-auth-rise text-[16px] leading-[1.6] text-spot-sub",
  loader: "mt-9 flex animate-auth-rise flex-col gap-3.5",
  track: "relative h-[3px] w-[220px]",
  bar: "absolute inset-0 overflow-hidden rounded-sm bg-spot-violet/[.12] after:absolute after:inset-0 after:animate-auth-loader-wave after:bg-[linear-gradient(90deg,transparent,#ffffffb3,transparent)] after:content-[''] after:[translate:-100%_0]",
  fill: "h-full w-full animate-auth-fill rounded-sm bg-[linear-gradient(90deg,#5b47a0,#a996e0)] shadow-[0_0_8px_#7c62d666]",
  dot: "absolute -top-1 left-0 h-[11px] w-3 animate-auth-dot rounded-full bg-spot-brand opacity-0 shadow-[0_0_12px_#7c62d6,0_0_24px_#7c62d659]",
  loaderLabel:
    "font-mono text-[10.5px] font-medium uppercase leading-none tracking-[0.12em] text-spot-muted",
  pills: "mt-[30px] flex flex-wrap gap-2",
  pill: "inline-flex animate-auth-rise items-center gap-1.5 rounded-full border border-white/90 bg-white/60 px-[13px] py-2 text-[12px] leading-none text-spot-text transition-[transform,color,background-color,border-color] duration-200 hover:-translate-y-px hover:border-spot-violet/25 hover:bg-white/90 hover:text-spot-violet [&>svg]:text-spot-violet",
  mockup: "relative mt-11 animate-auth-rise",
  window:
    "relative overflow-hidden rounded-2xl border border-white/90 bg-white/[.72] shadow-float backdrop-blur-[12px] before:pointer-events-none before:absolute before:inset-0 before:z-[1] before:animate-auth-fade before:rounded-[inherit] before:bg-[linear-gradient(135deg,#7c62d614,transparent_50%)] before:content-['']",
  windowBar:
    "flex items-center gap-2 border-b border-[#2327350f] bg-white/80 px-3 py-[9px]",
  windowDots: "flex gap-[5px]",
  windowDot: "size-[7px] rounded-full",
  url: "flex-1 rounded bg-spot-thead px-2 py-1 text-center font-mono text-[9px] leading-none text-spot-faint",
  body: "flex flex-col gap-2.5 p-3.5",
  stats: "grid grid-cols-3 gap-2",
  card: "flex flex-col gap-[9px] rounded-[10px] p-3",
  skLabel: `${skeleton} h-[5px] bg-white/75`,
  skValue: `${skeleton} h-2.5 animate-auth-grow`,
  table:
    "flex flex-col gap-[11px] rounded-[10px] border border-[#2327350d] bg-white p-3",
  row: "flex animate-auth-row items-center gap-2.5",
  sk: `${skeleton} h-1.5 bg-spot-violet/10`,
  badge: "ml-auto h-3 w-[34px] shrink-0 rounded-md",
  toast:
    "absolute -bottom-3.5 -right-3 z-[2] flex animate-auth-toast items-center gap-[5px] rounded-md border border-white bg-white/90 px-[9px] py-1.5 text-[9px] font-medium leading-none text-avatar-sage-ink opacity-0 shadow-float backdrop-blur-[8px] before:size-[5px] before:rounded-full before:bg-[#5f9d70] before:content-['']",
};

const delay = (d, extra) => ({ "--d": `${d}s`, ...extra });

const AuthVisual = ({ headline, subtext, loaderLabel }) => {
  const panelRef = useRef(null);

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return undefined;
    const particles = Array.from({ length: 25 }, () => {
      const p = document.createElement("div");
      const size = 1 + Math.random() * 2.5;
      p.className = ui.particle;
      p.style.cssText = `left:${Math.random() * 100}%;top:${Math.random() * 100}%;width:${size}px;height:${size}px;--dur:${5 + Math.random() * 8}s;--dx:${(Math.random() - 0.5) * 50}px;--dy:${(Math.random() - 0.5) * 70}px;animation-delay:${Math.random() * -10}s`;
      panel.appendChild(p);
      return p;
    });
    return () => particles.forEach((p) => p.remove());
  }, []);

  return (
    <aside className={ui.panel} ref={panelRef}>
      <div className={ui.gridLines} />
      <div className={ui.glow1} />
      <div className={ui.glow2} />
      <div className={ui.glow3} />
      <div className={ui.streak1} />
      <div className={ui.streak2} />

      <div className={ui.content}>
        <div className={ui.intro}>
          <h2 className={ui.headline}>
            {headline.map((line, i) => (
              <span key={line}>
                {i > 0 && <br />}
                <span
                  className={i === headline.length - 1 ? ui.accent : ui.word}
                  style={delay(0.15 + i * 0.15, { "--rise": "20px" })}
                >
                  {line}
                </span>
              </span>
            ))}
          </h2>
          <p className={ui.subtext} style={delay(0.45)}>
            {subtext}
          </p>
        </div>

        <div className={ui.loader} style={delay(0.6)}>
          <div className={ui.track}>
            <div className={ui.bar}>
              <div className={ui.fill} />
            </div>
            <div className={ui.dot} />
          </div>
          <div className={ui.loaderLabel}>{loaderLabel}</div>
        </div>

        <div className={ui.pills}>
          {PILLS.map((pill, i) => (
            <span
              key={pill.label}
              className={ui.pill}
              style={delay(0.75 + i * 0.07)}
            >
              <svg
                width="12"
                height="12"
                viewBox="0 0 12 12"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.2"
                aria-hidden="true"
              >
                {pill.icon}
              </svg>
              {pill.label}
            </span>
          ))}
        </div>

        <div
          className={ui.mockup}
          style={delay(1, { "--rise": "24px" })}
          aria-hidden="true"
        >
          <div className={ui.window}>
            <div className={ui.windowBar}>
              <div className={ui.windowDots}>
                <div
                  className={ui.windowDot}
                  style={{ background: "#e8835f" }}
                />
                <div
                  className={ui.windowDot}
                  style={{ background: "#e3bf66" }}
                />
                <div
                  className={ui.windowDot}
                  style={{ background: "#94bf87" }}
                />
              </div>
              <div className={ui.url}>invoicespot.com/dashboard</div>
            </div>
            <div className={ui.body}>
              <div className={ui.stats}>
                {STAT_CARDS.map((card) => (
                  <div key={card.value} className={`${ui.card} ${card.tone}`}>
                    <div
                      className={ui.skLabel}
                      style={{ "--w": card.label, "--sd": card.sd }}
                    />
                    <div
                      className={`${ui.skValue} ${card.bar}`}
                      style={{
                        "--w": card.value,
                        "--sd": card.sd,
                        "--gd": card.gd,
                      }}
                    />
                  </div>
                ))}
              </div>
              <div className={ui.table}>
                {TABLE_ROWS.map((row) => (
                  <div
                    key={row.status}
                    className={ui.row}
                    style={{ "--rd": row.rd }}
                  >
                    <div
                      className={ui.sk}
                      style={{ "--w": row.a, "--sd": row.sd }}
                    />
                    <div
                      className={ui.sk}
                      style={{ "--w": row.b, "--sd": row.sd }}
                    />
                    <div className={`${ui.badge} ${BADGES[row.status]}`} />
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className={ui.toast}>Payment received</div>
        </div>
      </div>
    </aside>
  );
};

export default AuthVisual;
