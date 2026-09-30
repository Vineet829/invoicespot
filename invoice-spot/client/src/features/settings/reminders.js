// What the automatic reminder settings will do, in one sentence.
export const reminderSummary = ({
  remindersEnabled,
  reminderDaysBefore,
  reminderOnDue,
  reminderEveryDays,
  reminderMax,
}) => {
  if (!remindersEnabled) return "Automatic reminders are off.";
  const before = Number(reminderDaysBefore) || 0;
  const every = Number(reminderEveryDays) || 0;
  const most = Number(reminderMax) || 0;
  const steps = [
    before && `${before} ${before === 1 ? "day" : "days"} before the due date`,
    reminderOnDue && "on the due date",
    every && `every ${every === 1 ? "day" : `${every} days`} after it`,
  ].filter(Boolean);
  if (!steps.length || !most)
    return "No reminder will go out with these choices.";
  const list =
    steps.length === 1
      ? steps[0]
      : `${steps.slice(0, -1).join(", ")} and ${steps.at(-1)}`;
  return `Reminders go out ${list}, at most ${most} ${most === 1 ? "time" : "times"} per invoice.`;
};
