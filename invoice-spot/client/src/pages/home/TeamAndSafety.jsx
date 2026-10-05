import { FiCheck } from "react-icons/fi";
import { surface } from "../../components/workspace/pastel";
import { useReveal } from "./motion";

// Who works in a business, and what keeps their sign-in safe: two cards
// side by side, each its own section.
const PEOPLE = [
  ["PS", "Priya S.", "Owner", "bg-avatar-lilac text-avatar-lilac-ink"],
  ["AK", "Arjun K.", "Admin", "bg-avatar-blue text-avatar-blue-ink"],
  ["MR", "Meera R.", "Member", "bg-avatar-peach text-avatar-peach-ink"],
  ["DV", "Dev V.", "Viewer", "bg-avatar-sage text-avatar-sage-ink"],
];

const POINTS = [
  "Two-factor codes from any authenticator app, with recovery codes",
  "Sign in with Google once you set it up",
  "See every signed-in device and end any of them",
  "Old links and tokens stop working when they're replaced",
];

const card = `${surface} flex min-w-0 flex-col gap-3.5 p-7 max-[700px]:p-6`;
const title =
  "font-display text-[30px] font-extralight leading-[1.1] tracking-[-0.045em] text-spot-ink max-[700px]:text-[27px]";
const line = "text-pretty text-[15px] leading-[1.6] text-spot-body";

const TeamAndSafety = ({ motion }) => {
  const [ref, reveal] = useReveal(motion);
  return (
    <div
      ref={ref}
      className={`grid grid-cols-[repeat(auto-fit,minmax(min(100%,440px),1fr))] gap-4 pb-6 pt-12 max-[700px]:pt-9 ${reveal}`}
    >
      <section id="team" className={card} aria-labelledby="team-title">
        <h2 id="team-title" className={title}>
          Bring your team.
        </h2>
        <p className={line}>
          Each person gets the access their role needs, in every business they
          belong to.
        </p>
        <ul className="m-0 list-none p-0">
          {PEOPLE.map(([initials, name, role, tone]) => (
            <li
              className="flex items-center gap-3 border-t border-[#23273512] py-2.5 first:border-t-0"
              key={name}
            >
              <span
                className={`grid size-9 shrink-0 place-items-center rounded-xl text-[12px] ${tone}`}
                aria-hidden="true"
              >
                {initials}
              </span>
              <span className="flex-1 text-[14.5px] text-spot-ink">{name}</span>
              <span className="rounded-full bg-[#2327350d] px-2.5 py-1 text-[12.5px] text-spot-text">
                {role}
              </span>
            </li>
          ))}
        </ul>
      </section>
      <section id="safety" className={card} aria-labelledby="safety-title">
        <h2 id="safety-title" className={title}>
          Safe by default.
        </h2>
        <p className={line}>
          Your clients’ details and your numbers stay yours.
        </p>
        <ul className="m-0 grid list-none gap-3 p-0">
          {POINTS.map((point) => (
            <li
              className="flex items-start gap-2.5 text-[15px] leading-[1.5] text-spot-text"
              key={point}
            >
              <FiCheck
                className="mt-0.5 size-[17px] shrink-0 stroke-2 text-spot-violet"
                aria-hidden="true"
              />
              {point}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
};

export default TeamAndSafety;
