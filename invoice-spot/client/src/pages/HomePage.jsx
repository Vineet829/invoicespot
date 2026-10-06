import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { useLoginUserMutation } from "../features/auth/authApiSlice";
import { logIn } from "../features/auth/authSlice";
import { GUEST_CREDENTIALS } from "../features/auth/components/authCommon";
import useTitle from "../hooks/useTitle";
import PublicHeader from "../components/public/PublicHeader";
import Facts from "./home/Facts";
import GetPaid from "./home/GetPaid";
import Hero from "./home/Hero";
import Pricing from "./home/Pricing";
import { column } from "./home/layout";
import { usePrefersReducedMotion } from "./home/motion";

const SECTIONS = [
  { id: "get-paid", label: "How it works" },
  { id: "pricing", label: "Pricing" },
];

// The page's canvas: the workspace's reset and type, on two soft glows.
const page =
  "dashboard-app min-h-dvh overflow-x-hidden bg-[radial-gradient(ellipse_at_10%_60%,#eef0fb_0,#eef0fb00_44%),radial-gradient(ellipse_at_90%_75%,#f1dcd2_0,#f1dcd200_38%)] bg-spot-canvas font-ui font-normal leading-[normal] tracking-normal text-spot-ink [color-scheme:light]";
// The dark stage the page opens on: rounded, inset from the window's
// edges and lit by three soft glows, with focus rings that show on it.
const stage =
  "m-[clamp(8px,1.2vw,14px)] overflow-hidden rounded-[clamp(24px,3vw,38px)] bg-[radial-gradient(ellipse_at_74%_42%,#7c62d68c_0,#7c62d600_52%),radial-gradient(ellipse_at_18%_108%,#e8835f3d_0,#e8835f00_46%),radial-gradient(ellipse_at_100%_0%,#6ea0d238_0,#6ea0d200_40%)] bg-[#1b1430] text-white [&_:is(a,button):focus-visible]:outline-white";

// The homepage: the dark stage with the hero, then what the app covers,
// how an invoice gets paid, the price, and the sign-off.
const HomePage = () => {
  useTitle("Invoice Spot — Good work. Great invoices.");
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const user = useSelector((state) => state.auth.user);
  const [loginGuest, { isLoading }] = useLoginUserMutation();
  const [demoError, setDemoError] = useState("");
  const motion = !usePrefersReducedMotion();

  // The demo: the sample business, as a guest, or straight in when
  // already signed in.
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
    <div className={page}>
      <main id="main">
        <div id="top" className={stage}>
          <div className={`${column} py-3`}>
            <PublicHeader
              tone="dark"
              sections={SECTIONS}
              signUpLabel="Start for free"
            />
          </div>
          <Hero
            motion={motion}
            busy={isLoading}
            error={demoError}
            onDemo={openDemo}
          />
        </div>
        <Facts motion={motion} />
        <GetPaid motion={motion} />
        <Pricing motion={motion} />
      </main>
      <footer
        className={`${column} flex flex-wrap items-center justify-between gap-x-7 gap-y-3.5 pb-9 pt-[clamp(72px,8vw,104px)] text-[14px] text-spot-muted`}
      >
        <Link
          to="/"
          className="font-display text-[20px] font-light tracking-[-0.04em] text-spot-ink [&>span]:text-spot-brand"
          aria-label="Invoice Spot home"
        >
          Invoice Spot<span>.</span>
        </Link>
        <p>
          © {new Date().getFullYear()} Invoice Spot · Made for independent
          business
        </p>
      </footer>
    </div>
  );
};

export default HomePage;
