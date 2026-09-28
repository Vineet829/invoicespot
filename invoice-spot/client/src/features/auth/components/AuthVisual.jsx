import { useEffect, useRef } from "react";

const STAT_CARDS = [
  { label: "40%", value: "62%", sd: "0s", gd: "1.4s" },
  { label: "34%", value: "48%", sd: "0.15s", gd: "1.5s" },
  { label: "44%", value: "70%", sd: "0.3s", gd: "1.6s" },
];

const TABLE_ROWS = [
  { a: "40%", b: "20%", status: "paid", rd: "1.55s", sd: "0.45s" },
  { a: "32%", b: "26%", status: "pending", rd: "1.65s", sd: "0.6s" },
  { a: "46%", b: "16%", status: "draft", rd: "1.75s", sd: "0.75s" },
];

const PILLS = [
  { label: "Invoices", icon: <><rect x="1.5" y="2.5" width="9" height="7" rx="1.5" /><path d="M1.5 5h9" /></> },
  { label: "PDF Email", icon: <><path d="M1.5 4l4.5 3 4.5-3" /><rect x="1.5" y="3" width="9" height="6" rx="1.5" /></> },
  { label: "Payments", icon: <><circle cx="6" cy="6" r="4.5" /><path d="M4 6.5l1.5 1.5 3-3" /></> },
  { label: "Customers", icon: <><circle cx="6" cy="4.5" r="2" /><path d="M2 11c0-2.2 1.8-4 4-4s4 1.8 4 4" /></> },
];

const delay = (d, extra) => ({ "--d": `${d}s`, ...extra });

const AuthVisual = ({ headline, subtext, loaderLabel }) => {
  const panelRef = useRef(null);

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return undefined;
    const particles = Array.from({ length: 25 }, () => {
      const p = document.createElement("div");
      const size = 1 + Math.random() * 2.5;
      p.className = "particle";
      p.style.cssText = `left:${Math.random() * 100}%;top:${Math.random() * 100}%;width:${size}px;height:${size}px;--dur:${5 + Math.random() * 8}s;--dx:${(Math.random() - 0.5) * 50}px;--dy:${(Math.random() - 0.5) * 70}px;animation-delay:${Math.random() * -10}s`;
      panel.appendChild(p);
      return p;
    });
    return () => particles.forEach((p) => p.remove());
  }, []);

  return (
    <aside className="auth-visual-panel" ref={panelRef}>
      <div className="grid-lines" />
      <div className="glow glow-1" />
      <div className="glow glow-2" />
      <div className="glow glow-3" />
      <div className="streak" />
      <div className="streak streak-2" />

      <div className="auth-visual-content">
        <div className="auth-intro">
          <h2 className="auth-headline">
            {headline.map((line, i) => (
              <span key={line}>
                {i > 0 && <br />}
                <span
                  className={`auth-word auth-rise${i === headline.length - 1 ? " auth-word--accent" : ""}`}
                  style={delay(0.15 + i * 0.15, { "--rise": "20px" })}
                >
                  {line}
                </span>
              </span>
            ))}
          </h2>
          <p className="auth-subtext auth-rise" style={delay(0.45)}>{subtext}</p>
        </div>

        <div className="auth-loader auth-rise" style={delay(0.6)}>
          <div className="al-track">
            <div className="al-bar"><div className="al-fill" /></div>
            <div className="al-glow" />
          </div>
          <div className="al-label">{loaderLabel}</div>
        </div>

        <div className="auth-pills">
          {PILLS.map((pill, i) => (
            <span key={pill.label} className="auth-pill auth-rise" style={delay(0.75 + i * 0.07)}>
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="#A29BFE" strokeWidth="1.2" aria-hidden="true">{pill.icon}</svg>
              {pill.label}
            </span>
          ))}
        </div>

        <div className="auth-mockup auth-rise" style={delay(1, { "--rise": "24px" })} aria-hidden="true">
          <div className="mock-window">
            <div className="mock-bar">
              <div className="mock-dots">
                <div className="mock-dot" style={{ background: "#FF5F57" }} />
                <div className="mock-dot" style={{ background: "#FFBD2E" }} />
                <div className="mock-dot" style={{ background: "#28C840" }} />
              </div>
              <div className="mock-url">invoicespot.com/dashboard</div>
            </div>
            <div className="mock-body">
              <div className="mock-stats">
                {STAT_CARDS.map((card) => (
                  <div key={card.value} className="mock-card">
                    <div className="sk sk-label" style={{ "--w": card.label, "--sd": card.sd }} />
                    <div className="sk sk-value" style={{ "--w": card.value, "--sd": card.sd, "--gd": card.gd }} />
                  </div>
                ))}
              </div>
              <div className="mock-table">
                {TABLE_ROWS.map((row) => (
                  <div key={row.status} className="mock-tr" style={{ "--rd": row.rd }}>
                    <div className="sk" style={{ "--w": row.a, "--sd": row.sd }} />
                    <div className="sk" style={{ "--w": row.b, "--sd": row.sd }} />
                    <div className={`mock-badge ${row.status}`} />
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="mock-toast">Payment received</div>
        </div>
      </div>
    </aside>
  );
};

export default AuthVisual;
