import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { FiArrowRight, FiArrowUpRight } from "react-icons/fi";
import { toast } from "react-toastify";
import { useLoginUserMutation } from "../features/auth/authApiSlice";
import { logIn } from "../features/auth/authSlice";
import { GUEST_CREDENTIALS } from "../features/auth/components/authCommon";
import useTitle from "../hooks/useTitle";
import PublicHeader from "../components/public/PublicHeader";
import { PublicFooter } from "../components/public/PublicPage";
import {
  appSurface,
  buttonBase,
  primaryButton,
  secondaryButton,
} from "../components/workspace/pastel";
import Features from "./home/Features";
import Showcase from "./home/Showcase";
import Steps from "./home/Steps";
import TeamAndSafety from "./home/TeamAndSafety";
import Templates from "./home/Templates";
import { rise, usePrefersReducedMotion, useReveal } from "./home/motion";

const SECTIONS = [
  { id: "features", label: "Features" },
  { id: "get-paid", label: "Getting paid" },
  { id: "templates", label: "Templates" },
];

// The buttons on the closing band, light on its dark ground.
const BUTTONS = {
  light: [primaryButton, secondaryButton],
  dark: [
    `${buttonBase} bg-white text-spot-night hover:bg-[#ebe7f8]`,
    `${buttonBase} border border-white/25 bg-white/[.12] text-white hover:bg-white/20`,
  ],
};

// Sign up, or look around the sample business: as a guest, or straight in
// when already signed in.
const Actions = ({ tone = "light", user, busy, onDemo }) => (
  <div className="flex flex-wrap items-center justify-center gap-3 max-[480px]:w-full max-[480px]:[&>*]:flex-1">
    <Link className={BUTTONS[tone][0]} to="/register">
      Start for free <FiArrowUpRight aria-hidden="true" />
    </Link>
    <button
      className={BUTTONS[tone][1]}
      type="button"
      onClick={onDemo}
      disabled={busy}
      aria-busy={busy}
    >
      {busy ? "Opening demo…" : user ? "Open dashboard" : "Try as guest"}
      <FiArrowRight aria-hidden="true" />
    </button>
  </div>
);

// The closing call to action.
const Closing = ({ motion, children }) => {
  const [ref, reveal] = useReveal(motion);
  return (
    <section
      id="closing"
      ref={ref}
      className={`pb-4 pt-12 max-[700px]:pt-9 ${reveal}`}
      aria-labelledby="closing-title"
    >
      <div className="flex flex-wrap items-center justify-between gap-6 rounded-[32px] bg-spot-night bg-[radial-gradient(ellipse_at_90%_0%,#5b47a0_0,#5b47a000_60%)] p-[clamp(28px,5vw,56px)] text-white">
        <div className="flex max-w-[620px] flex-col gap-2.5">
          <h2
            id="closing-title"
            className="text-balance font-display text-[clamp(30px,4vw,44px)] font-extralight leading-[1.1] tracking-[-0.05em]"
          >
            Send your first invoice today.
          </h2>
          <p className="text-pretty text-[16px] leading-[1.6] text-[#d9d3f2]">
            Free to use. No card needed. Or look around the sample business
            first.
          </p>
        </div>
        {children}
      </div>
    </section>
  );
};

const HomePage = () => {
  useTitle("Invoice Spot — Good work. Great invoices.");
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const user = useSelector((state) => state.auth.user);
  const [loginGuest, { isLoading }] = useLoginUserMutation();
  const [demoError, setDemoError] = useState("");
  const motion = !usePrefersReducedMotion();

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
      const message =
        error?.data?.message || "Unable to open the demo. Please try again.";
      setDemoError(message);
      toast.error(message);
    }
  };

  return (
    <div className={`${appSurface} flex min-h-dvh flex-col`}>
      <div className="mx-auto flex w-full max-w-[1200px] flex-1 flex-col px-8 pb-6 pt-4 max-[960px]:px-6 max-[700px]:px-4 max-[700px]:pb-5 max-[700px]:pt-2">
        <PublicHeader sections={SECTIONS} signUpLabel="Start for free" />
        <main className="flex flex-1 flex-col" id="main">
          <section
            className={`flex flex-col items-center gap-[22px] pb-9 pt-14 text-center max-[700px]:gap-4 max-[700px]:pb-7 max-[700px]:pt-8 ${rise(motion)}`}
            aria-labelledby="landing-title"
          >
            <span className="inline-flex items-center gap-2 text-balance rounded-full border border-white/90 bg-white/70 px-3.5 py-[7px] text-[13px] text-spot-nav">
              <span
                className="size-2 rounded-full bg-spot-brand"
                aria-hidden="true"
              />
              Invoices, GST and getting paid, in one place
            </span>
            <h1
              id="landing-title"
              className="text-balance font-display text-[clamp(46px,7.4vw,96px)] font-extralight leading-[1.02] tracking-[-0.06em] text-spot-ink max-[360px]:text-[38px]"
            >
              Good work.{" "}
              <span className="text-spot-brand">Great invoices.</span>
            </h1>
            <p className="max-w-[640px] text-balance text-[19px] leading-[1.6] text-spot-text max-[700px]:text-[17px]">
              You take care of the craft. We’ll take care of the paperwork.
            </p>
            <Actions user={user} busy={isLoading} onDemo={openDemo} />
            {demoError && (
              <p className="text-[13px] text-overdue-strong" role="alert">
                {demoError}
              </p>
            )}
            <p className="text-balance text-[13.5px] text-spot-sub">
              Free to use · No card needed · Explore a sample business in one
              click
            </p>
          </section>
          <Showcase motion={motion} />
          <Features motion={motion} />
          <Steps motion={motion} />
          <Templates motion={motion} />
          <TeamAndSafety motion={motion} />
          <Closing motion={motion}>
            <Actions
              tone="dark"
              user={user}
              busy={isLoading}
              onDemo={openDemo}
            />
          </Closing>
        </main>
        <PublicFooter />
      </div>
    </div>
  );
};

export default HomePage;
