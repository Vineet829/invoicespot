import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { FiCheck } from "react-icons/fi";
import { useBusiness } from "../../business/permissions";
import { setupSteps } from "../dashboardModel";
import { hideSetup, setupHidden } from "../setupHidden";

// The title and progress beside the steps and Hide on a wide strip; on a
// narrower one, the steps go under them, in five columns, then three, then
// one on phones. The ranges meet without overlapping, since their order in
// the stylesheet is not fixed.
const layout =
  "grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-[18px] gap-y-3 [grid-template-areas:'title_hide'_'steps_steps'] [@container_setup_(width>=1320px)]:grid-cols-[230px_minmax(0,1fr)_auto] [@container_setup_(width>=1320px)]:[grid-template-areas:'title_steps_hide']";
const stepList =
  "m-0 grid list-none grid-cols-1 gap-2.5 p-0 [grid-area:steps] [@container_setup_(600px<=width<1000px)]:grid-cols-3 [@container_setup_(width>=1000px)]:grid-cols-5";
const stepLink = (done) =>
  `flex min-w-0 items-center gap-2.5 rounded-[14px] px-3 py-2.5 transition-colors coarse:min-h-11 ${done ? "bg-card-sage-bar/10 hover:bg-card-sage-bar/[.16]" : "bg-white hover:bg-white/70"}`;
const stepMark = (done) =>
  `grid size-[26px] shrink-0 place-items-center rounded-full text-[12px] ${done ? "bg-card-sage-bar text-white [&>svg]:size-3.5 [&>svg]:stroke-[2.4]" : "border-[1.5px] border-dashed border-spot-violet/45 text-spot-violet"}`;
const hideButton =
  "h-9 rounded-[10px] bg-transparent px-3 text-[13px] text-spot-nav transition-colors [grid-area:hide] hover:bg-white/60 hover:text-spot-night coarse:min-h-11";

// What is left to make a new business ready, for those who can change its
// details, until every step is done. Each step opens the page that does it.
// Hide puts the strip away in this business on this browser; in its place,
// a note for screen readers takes focus, so no one loses their place.
const SetupStrip = ({ setup }) => {
  const { business, can } = useBusiness();
  const businessId = business?._id;
  const [hiddenIn, setHiddenIn] = useState(null);
  const note = useRef(null);
  useEffect(() => {
    if (hiddenIn) note.current?.focus();
  }, [hiddenIn]);
  const steps = setupSteps(setup);
  const done = steps.filter((step) => step.done).length;
  if (businessId && hiddenIn === businessId) {
    return (
      <p ref={note} tabIndex={-1} className="sr-only">
        The setup steps are hidden.
      </p>
    );
  }
  if (
    !businessId ||
    !can("MANAGE_BUSINESS") ||
    done === steps.length ||
    setupHidden(businessId)
  ) {
    return null;
  }
  const hide = () => {
    hideSetup(businessId);
    setHiddenIn(businessId);
  };
  return (
    <section
      className="min-w-0 animate-ov-rise rounded-[22px] border border-white/90 bg-[linear-gradient(90deg,#e5e1f8e6,#ffffffb3)] px-[18px] py-3.5 [--rise-delay:40ms] [container-name:setup] [container-type:inline-size]"
      aria-labelledby="setup-title"
    >
      <div className={layout}>
        <div className="min-w-0 [grid-area:title]">
          <h2 id="setup-title" className="text-[15px] font-medium">
            Finish setting up
          </h2>
          <p className="mt-1 text-[12.5px] text-spot-body">
            {done} of {steps.length} done · each opens its setting
          </p>
          <span
            className="mt-2 block h-1.5 max-w-[230px] overflow-hidden rounded-full bg-spot-violet/[.14]"
            aria-hidden="true"
          >
            <span
              className="block h-full origin-left animate-ov-bar rounded-[inherit] bg-spot-violet"
              style={{ width: `${(done / steps.length) * 100}%` }}
            />
          </span>
        </div>
        <ol className={stepList}>
          {steps.map((step, index) => (
            <li className="min-w-0" key={step.key}>
              <Link to={step.to} className={stepLink(step.done)}>
                <span className={stepMark(step.done)} aria-hidden="true">
                  {step.done ? <FiCheck /> : index + 1}
                </span>
                <span className="flex min-w-0 flex-col">
                  <span
                    className={`text-[13.5px] ${step.done ? "text-[#2f5a2c]" : "font-medium"}`}
                  >
                    {step.label}
                  </span>
                  <span className="truncate text-[12px] text-spot-muted">
                    {step.done ? "Done" : step.detail}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ol>
        <button
          type="button"
          className={hideButton}
          onClick={hide}
          aria-label="Hide the setup steps"
        >
          Hide
        </button>
      </div>
    </section>
  );
};

export default SetupStrip;
