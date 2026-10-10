import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FiArrowRight } from "react-icons/fi";
import Tour from "./Tour";
import { column } from "./layout";
import { useInterval, useRunning } from "./motion";

// The ends of the headline, one at a time.
export const ENDINGS = ["without the chase.", "by UPI or bank.", "on time."];
export const TURN = 2800;

// The end of the headline, sliding up to the next while `running`. It is
// one line tall and shows one ending at a time; assistive technology reads
// the first ending, once.
const Ticker = ({ running }) => {
  const [word, setWord] = useState(0);
  useInterval(
    () => setWord((current) => (current + 1) % ENDINGS.length),
    TURN,
    running,
  );
  return (
    <>
      <span
        className="inline-block h-[1.08em] overflow-hidden align-bottom text-[#c9bdf2]"
        aria-hidden="true"
      >
        <span
          className="block [transition:transform_.6s_cubic-bezier(.6,0,.2,1)]"
          style={{ transform: `translateY(${-1.08 * word}em)` }}
        >
          {ENDINGS.map((ending) => (
            <span className="block h-[1.08em] whitespace-nowrap" key={ending}>
              {ending}
            </span>
          ))}
        </span>
      </span>
      <span className="sr-only">{ENDINGS[0]}</span>
    </>
  );
};

// The hero on the dark stage: the headline, the sign-up and the demo, and
// the product tour beside them. Its headline and tour play while it is on
// screen in a tab that is being looked at. `onDemo` opens the demo; `busy`
// while it is opening, and `error` if it could not. The headline keeps to
// the design's size, or less where its column is too narrow for the
// widest ending, 8.4 of its ems, so no ending is ever cut.
const Hero = ({ motion, busy, error, onDemo }) => {
  const ref = useRef(null);
  const running = useRunning(ref, motion);
  return (
    <section
      ref={ref}
      className={`${column} flex flex-wrap items-center gap-[clamp(48px,6vw,88px)] pb-[clamp(64px,7vw,96px)] pt-[clamp(28px,5vw,64px)]`}
      aria-labelledby="hero-title"
    >
      <div className="min-w-0 flex-[1_1_380px] [container-type:inline-size]">
        <h1
          id="hero-title"
          className={`font-display text-[min(clamp(42px,4.6vw,66px),11.8cqi)] font-light leading-[1.08] tracking-[-0.05em] text-white ${motion ? "animate-home-lift" : ""}`}
        >
          Invoice in minutes.
          <br />
          Get paid <Ticker running={running} />
        </h1>
        <p
          className={`mt-6 max-w-[460px] text-[18px] leading-[1.6] text-[#cfc8ea] ${motion ? "animate-home-lift [--lift-delay:100ms]" : ""}`}
        >
          GST-ready invoices that clients pay by UPI or bank, with reminders
          that go out on their own.
        </p>
        <div
          className={`mt-[34px] flex flex-wrap items-center gap-x-[22px] gap-y-3 ${motion ? "animate-home-lift [--lift-delay:200ms]" : ""}`}
        >
          <Link
            to="/register"
            className="inline-flex min-h-[54px] items-center rounded-2xl bg-white px-7 text-[16px] font-medium text-[#1b1430] shadow-[0_18px_40px_-16px_#a996e0b3] transition-colors hover:bg-[#ebe7f8]"
          >
            Start for free
          </Link>
          <button
            type="button"
            className="inline-flex min-h-[54px] items-center gap-2 bg-transparent p-0 text-[16px] font-medium text-white [&>svg]:size-[17px] [&>svg]:stroke-[2]"
            onClick={onDemo}
            disabled={busy}
            aria-busy={busy}
          >
            {busy ? "Opening demo…" : "See the live demo"}
            <FiArrowRight aria-hidden="true" />
          </button>
        </div>
        {error && (
          <p className="mt-3.5 text-[14px] text-spot-coral" role="alert">
            {error}
          </p>
        )}
        <p
          className={`mt-[18px] text-[14px] text-[#a99fd0] ${motion ? "animate-home-lift [--lift-delay:260ms]" : ""}`}
        >
          Free to use · No card needed
        </p>
      </div>
      <Tour motion={motion} running={running} />
    </section>
  );
};

export default Hero;
