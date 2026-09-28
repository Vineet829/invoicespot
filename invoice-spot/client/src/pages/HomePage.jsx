import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { FiArrowRight, FiArrowUpRight, FiCheck, FiSend, FiUsers } from "react-icons/fi";
import { toast } from "react-toastify";
import { useLoginUserMutation } from "../features/auth/authApiSlice";
import { logIn } from "../features/auth/authSlice";
import { GUEST_CREDENTIALS } from "../features/auth/components/authCommon";
import useTitle from "../hooks/useTitle";
import MetricsShowcase from "../components/home/MetricsShowcase";
import useHomeViewportFit from "../components/home/useHomeViewportFit";
import "../styles/landing.css";

const features = [
  { icon: FiUsers, title: "A place for every client.", emphasis: "every client.", description: "Save their details once. Make the next invoice even easier." },
  { icon: FiSend, title: "Ready when you are.", emphasis: "you are.", description: "Invoices, quotations, and receipts. Send them as PDFs." },
  { icon: FiCheck, title: "Every payment, in view.", emphasis: "in view.", description: "Track full and partial payments. Know what’s still owed." },
];

const HomeAtmosphere = () => (
  <div className="landing-atmosphere" aria-hidden="true">
    <div className="landing-grid" />
    <div className="landing-glow landing-glow-one" />
    <div className="landing-glow landing-glow-two" />
  </div>
);

const HomePage = () => {
  useTitle("Invoice Spot — Good work. Great invoices.");
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const user = useSelector((state) => state.auth.user);
  const [loginGuest, { isLoading }] = useLoginUserMutation();
  const [demoError, setDemoError] = useState("");
  const { viewportRef, pageRef } = useHomeViewportFit(`${isLoading}-${Boolean(user)}-${demoError}`);

  const openDemo = async () => {
    if (user) {
      navigate("/dashboard");
      return;
    }
    setDemoError("");
    try {
      const credentials = await loginGuest(GUEST_CREDENTIALS).unwrap();
      dispatch(logIn(credentials));
      navigate("/dashboard");
    } catch (error) {
      const message = error?.data?.message || "Unable to open the demo. Please try again.";
      setDemoError(message);
      toast.error(message);
    }
  };

  return (
    <div className="landing-viewport" ref={viewportRef}>
      <div className="landing" ref={pageRef}>
        <header className="landing-header landing-container">
          <Link className="landing-brand" to="/" aria-label="Invoice Spot home">
            <span className="landing-brand-name">Invoice Spot</span>
          </Link>
          <nav className="landing-nav" aria-label="Main navigation">
            <Link to="/login">Log in</Link>
            <Link className="landing-nav-cta" to="/register">Get started <FiArrowUpRight aria-hidden="true" /></Link>
          </nav>
        </header>

        <section className="landing-hero landing-container" aria-labelledby="landing-title">
          <HomeAtmosphere />
          <div className="landing-copy">
            <div className="landing-intro">
              <p className="landing-eyebrow"><span /> FOR THE WORK YOU DO.</p>
              <h1 id="landing-title">
                <span className="landing-title-mask"><span>Good work.</span></span>
                <span className="landing-title-mask"><span className="landing-title-accent">Great invoices.</span></span>
              </h1>
            </div>
            <div className="landing-supporting-copy">
              <p className="landing-description">You take care of the craft.<br />We’ll take care of the paperwork.</p>
              <p className="landing-detail">Create polished invoices, send them to clients, and keep payments in order. All in one simple workspace.</p>
            </div>
            <div className="landing-conversion">
              <div className="landing-actions">
                <Link className="landing-primary" to="/register">Start for free <FiArrowUpRight aria-hidden="true" /></Link>
                <button className="landing-demo" type="button" onClick={openDemo} disabled={isLoading} aria-busy={isLoading}>
                  {isLoading ? "Opening demo…" : user ? "Open dashboard" : "Try as guest"}
                  <FiArrowRight aria-hidden="true" />
                </button>
              </div>
              <p className="landing-free-note"><FiCheck aria-hidden="true" /> Free to start. No credit card needed.</p>
              {demoError && <p className="landing-error" role="alert">{demoError}</p>}
            </div>
          </div>
          <MetricsShowcase />
        </section>

        <section className="landing-features landing-container" aria-label="Invoice Spot features">
          {features.map(({ icon: Icon, title, emphasis, description }) => (
            <div className="landing-feature" key={title}>
              <Icon aria-hidden="true" />
              <div>
                <h2>{title.slice(0, -emphasis.length)}<span>{emphasis}</span></h2>
                <p>{description}</p>
              </div>
            </div>
          ))}
        </section>

        <footer className="landing-footer landing-container">
          <span className="landing-copyright"><span>© {new Date().getFullYear()}</span>{" "}<strong>Invoice Spot</strong></span>
          <span className="landing-footer-note">Made for <strong>independent business</strong><span className="landing-footer-dot">.</span></span>
        </footer>
      </div>
    </div>
  );
};

export default HomePage;
