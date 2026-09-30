import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import {
  FiArrowRight,
  FiArrowUpRight,
  FiCheck,
  FiSend,
  FiUsers,
} from "react-icons/fi";
import { toast } from "react-toastify";
import { useLoginUserMutation } from "../features/auth/authApiSlice";
import { logIn } from "../features/auth/authSlice";
import { GUEST_CREDENTIALS } from "../features/auth/components/authCommon";
import useTitle from "../hooks/useTitle";
import PublicHeader from "../components/public/PublicHeader";
import { PublicFooter, pageFrame } from "../components/public/PublicPage";
import { primaryButton, secondaryButton } from "../components/workspace/pastel";

// Each feature takes one of the overview's financial summary colours.
const features = [
  {
    icon: FiUsers,
    title: "A place for every client.",
    description: "Save their details once. Make the next invoice even easier.",
    tone: {
      card: "bg-card-blue",
      icon: "text-card-blue-icon",
      title: "text-card-blue-ink",
      text: "text-card-blue-muted",
    },
  },
  {
    icon: FiSend,
    title: "Ready when you are.",
    description: "Invoices, quotations, and receipts. Send them as PDFs.",
    tone: {
      card: "bg-card-lilac",
      icon: "text-card-lilac-icon",
      title: "text-card-lilac-ink",
      text: "text-card-lilac-muted",
    },
  },
  {
    icon: FiCheck,
    title: "Every payment, in view.",
    description: "Track full and partial payments. Know what’s still owed.",
    tone: {
      card: "bg-card-peach",
      icon: "text-card-peach-icon",
      title: "text-card-peach-ink",
      text: "text-card-peach-muted",
    },
  },
];

const HomePage = () => {
  useTitle("Invoice Spot — Good work. Great invoices.");
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const user = useSelector((state) => state.auth.user);
  const [loginGuest, { isLoading }] = useLoginUserMutation();
  const [demoError, setDemoError] = useState("");

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
    <div className={pageFrame}>
      <PublicHeader signUp={false} />
      <main
        className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col justify-center gap-12 py-6 compact:gap-8 compact:py-2 max-[700px]:gap-10 max-[700px]:py-4"
        id="main"
      >
        <section
          className="flex animate-ov-rise flex-col items-center gap-5 text-center"
          aria-labelledby="landing-title"
        >
          <h1
            id="landing-title"
            className="text-balance font-display text-[76px] font-extralight leading-[1.02] tracking-[-0.055em] text-spot-ink compact:text-[64px] max-[1359px]:text-[60px] max-[700px]:text-[44px]"
          >
            Good work. <span className="text-spot-brand">Great invoices.</span>
          </h1>
          <p className="text-balance text-[19px] leading-[1.6] text-spot-text max-[700px]:text-[17px]">
            You take care of the craft. We’ll take care of the paperwork.
          </p>
          <div className="mt-1 flex flex-wrap items-center justify-center gap-3 max-[480px]:w-full max-[480px]:[&>*]:flex-1">
            <Link className={primaryButton} to="/register">
              Start for free <FiArrowUpRight aria-hidden="true" />
            </Link>
            <button
              className={secondaryButton}
              type="button"
              onClick={openDemo}
              disabled={isLoading}
              aria-busy={isLoading}
            >
              {isLoading
                ? "Opening demo…"
                : user
                  ? "Open dashboard"
                  : "Try as guest"}
              <FiArrowRight aria-hidden="true" />
            </button>
          </div>
          {demoError && (
            <p className="text-[13px] text-overdue-strong" role="alert">
              {demoError}
            </p>
          )}
        </section>
        <section
          className="grid animate-ov-rise grid-cols-3 gap-4 [--rise-delay:120ms] max-[900px]:grid-cols-1"
          aria-label="Invoice Spot features"
        >
          {features.map(({ icon: Icon, title, description, tone }) => (
            <div
              className={`flex flex-col gap-4 rounded-3xl p-6 ${tone.card}`}
              key={title}
            >
              <span
                className={`grid size-10 place-items-center rounded-full bg-white/60 [&>svg]:size-[18px] [&>svg]:stroke-[1.6] ${tone.icon}`}
              >
                <Icon aria-hidden="true" />
              </span>
              <div>
                <h2
                  className={`font-display text-[22px] font-light leading-[1.25] tracking-[-0.035em] ${tone.title}`}
                >
                  {title}
                </h2>
                <p
                  className={`mt-2 text-pretty text-[14.5px] leading-[1.6] ${tone.text}`}
                >
                  {description}
                </p>
              </div>
            </div>
          ))}
        </section>
      </main>
      <PublicFooter />
    </div>
  );
};

export default HomePage;
