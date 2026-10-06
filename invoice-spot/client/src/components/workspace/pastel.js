// Tailwind class recipes shared by the Overview and the workspace pages, so
// every signed-in page reads as one product.

// Page title in Sora with the brand-coloured full stop, and the line below.
export const pageTitle =
  "font-display text-[54px] font-extralight leading-none tracking-[-0.055em] compact:text-[46px] max-[1359px]:text-[38px] max-[700px]:text-[clamp(29px,7.4vw,38px)] [&>span]:text-spot-brand";
export const pageSubtitle =
  "mt-2.5 text-[14.5px] text-spot-sub compact:mt-2 max-[1359px]:text-[13px] max-[700px]:leading-[1.6]";

// Panel headings and the short muted notes beside them.
export const sectionTitle =
  "font-display text-[22px] font-light leading-[normal] tracking-[-0.035em] max-[700px]:text-[21px]";
export const panelNote = "text-[12.5px] text-spot-muted";

// Hover card content.
export const hoverLabel = "flex items-center gap-2 text-[12px] text-spot-muted";
export const hoverValue =
  "mt-1 block font-display text-[17px] font-light leading-[1.25] tracking-[-0.02em]";

// Loading placeholder blocks; size them per use.
export const skeleton =
  "block max-w-full shrink-0 animate-ov-pulse bg-spot-violet/[.08]";

export const iconButton =
  "inline-flex shrink-0 items-center justify-center rounded-xl border border-transparent bg-transparent p-0 text-spot-nav transition-colors hover:enabled:border-spot-violet/15 hover:enabled:bg-white/70 hover:enabled:text-spot-night coarse:min-h-11 coarse:min-w-11 [&>svg]:size-[17px]";

// Table pager controls.
export const pagerButton =
  "inline-flex size-[30px] shrink-0 items-center justify-center rounded-[9px] border-0 bg-white/70 p-0 text-spot-night transition-colors hover:enabled:bg-white disabled:cursor-default disabled:text-spot-disabled disabled:opacity-100 coarse:min-h-11 coarse:min-w-11 [&>svg]:size-3.5 [&>svg]:stroke-[1.7]";

// The frosted surface panels and cards sit on, and a workspace panel.
export const surface =
  "rounded-[30px] border border-white/90 bg-white/[.66] shadow-panel backdrop-blur-[16px]";
export const panel = `min-w-0 ${surface} p-6 compact:p-[18px] max-[700px]:rounded-3xl max-[700px]:p-[18px]`;

// The root of a page outside the workspace shell: canvas, type and the
// workspace reset (dashboard-app, see src/index.css).
export const appSurface =
  "dashboard-app bg-spot-canvas bg-canvas font-ui font-normal leading-[normal] tracking-normal text-spot-ink [color-scheme:light]";
// The brand mark's shape, and the mark in the workspace's colours; the
// homepage's dark stage dresses the shape in its own.
export const brandMarkBase =
  "whitespace-nowrap font-display text-[24px] font-normal leading-[normal] tracking-[-0.045em] coarse:inline-flex coarse:min-h-11 coarse:items-center";
export const brandMark = `${brandMarkBase} text-spot-ink [&>span]:text-spot-brand`;
export const textLink =
  "text-spot-violet underline-offset-4 transition-colors hover:text-spot-night hover:underline";

// Buttons, and links that look like buttons. The base sets shape and type
// only; each kind adds its colours.
export const buttonBase =
  "inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-4 text-[13px] font-medium leading-[1.4] transition-colors disabled:cursor-default disabled:opacity-100 [&>svg]:size-4 [&>svg]:shrink-0 [&>svg]:stroke-[1.8]";
export const primaryButton = `${buttonBase} bg-spot-night text-white hover:bg-[#433369] disabled:bg-spot-disabled`;
export const secondaryButton = `${buttonBase} border border-spot-violet/15 bg-white/70 text-spot-night hover:bg-white disabled:bg-white/40 disabled:text-spot-disabled`;
export const dangerButton = `${buttonBase} border border-overdue-icon/20 bg-overdue text-overdue-ink hover:bg-[#f8d4c4] disabled:text-overdue-muted`;
// A square secondary button holding only an icon; give it an aria-label.
export const secondaryIconButton =
  "inline-flex size-11 shrink-0 items-center justify-center rounded-xl border border-spot-violet/15 bg-white/70 text-spot-night transition-colors hover:bg-white disabled:cursor-default disabled:bg-white/40 disabled:text-spot-disabled disabled:opacity-100 [&>svg]:size-4 [&>svg]:shrink-0 [&>svg]:stroke-[1.8]";

// Small actions inside tables and forms, such as View and Delete.
const rowActionBase =
  "inline-flex h-8 items-center justify-center gap-1.5 whitespace-nowrap rounded-[10px] px-2.5 text-[12.5px] transition-colors disabled:cursor-default disabled:opacity-100 coarse:min-h-11 [&>svg]:size-[15px] [&>svg]:shrink-0 [&>svg]:stroke-[1.7]";
export const rowAction = `${rowActionBase} bg-spot-violet/[.07] text-spot-violet hover:bg-spot-violet/[.13] disabled:bg-spot-violet/[.04] disabled:text-spot-disabled`;
export const rowActionDanger = `${rowActionBase} bg-overdue/70 text-overdue-strong hover:enabled:bg-overdue disabled:bg-overdue/30 disabled:text-overdue-muted`;

// Form controls. Each control type sets its own size, so no element gets
// two utilities for the same property.
export const fieldLabel = "text-[13px] text-spot-body";
const controlSurface =
  "rounded-xl border border-[#23273524] bg-white/80 text-spot-ink [font-family:inherit] transition-[border-color] placeholder:text-spot-faint hover:enabled:border-[#23273540] focus:border-spot-violet/60 disabled:cursor-default disabled:bg-white/40 disabled:text-spot-disabled aria-[invalid=true]:border-overdue-icon/70";
const control = `block w-full min-w-0 ${controlSurface}`;
export const inputControl = `${control} h-11 px-3 text-[14px]`;
// A list's search box, sized like the toolbar around it, with room on the
// left for the magnifier drawn inside it.
export const searchControl = `${control} h-11 pl-10 pr-3 text-[13px]`;

// One option of a filter group: a pill around a visually hidden radio, so
// the pill shows the radio's checked and keyboard focus states.
export const filterChip =
  "relative inline-flex h-9 cursor-pointer select-none items-center whitespace-nowrap rounded-full border border-spot-violet/15 bg-white/60 px-3.5 text-[13px] text-spot-body transition-colors has-[:checked]:border-spot-night has-[:checked]:bg-spot-night has-[:checked]:text-white has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-[3px] has-[:focus-visible]:outline-[#5b47a0] coarse:h-11 [&:not(:has(:checked)):hover]:bg-white";
export const textareaControl = `${control} min-h-[120px] resize-y px-3 py-2.5 text-[14px] leading-[1.6]`;
export const fileControl = `${control} px-2 py-1.5 text-[13px] file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-spot-violet/10 file:px-3 file:py-2 file:text-[13px] file:[font-family:inherit] file:text-spot-violet`;

// The styled dropdown (Select.jsx). Its trigger is the controls' surface
// with the violet ring while focused or open and the rust ring when not
// valid, and fades as a whole when disabled; each size sets its own height
// and type, and the toolbar and mini sizes are as wide as their longest
// option unless the control is told to fit its chosen one.
const selectSurface =
  "inline-flex w-full cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-xl border border-[#23273524] bg-white/80 text-left text-spot-ink transition-[border-color,background-color,box-shadow] hover:enabled:border-[#23273547] hover:enabled:bg-white/95 focus-visible:border-spot-violet focus-visible:bg-white focus-visible:shadow-focus focus-visible:outline-none aria-expanded:border-spot-violet aria-expanded:bg-white aria-expanded:shadow-focus disabled:cursor-default disabled:opacity-50 aria-[invalid=true]:border-owed-overdue aria-[invalid=true]:shadow-invalid [&>svg]:shrink-0 [&>svg]:text-spot-nav [&>svg]:transition-transform";
export const selectTrigger = {
  toolbar: `${selectSurface} h-11 pl-3 pr-2.5 text-[13.5px] [&>svg]:size-[15px]`,
  form: `${selectSurface} h-11 pl-3 pr-2.5 text-[14px] [&>svg]:size-[15px]`,
  mini: `${selectSurface} h-[30px] rounded-[9px] pl-2 pr-1.5 text-[12.5px] coarse:min-h-11 [&>svg]:size-3.5`,
};
// The floating menu, and the rows inside it: options (36px, taller with a
// description, 32px under a mini trigger, 44px in the phone sheet), group
// headings, the search row at the top and the match count at the bottom.
export const selectMenu =
  "flex flex-col rounded-2xl border border-[#23273514] bg-white p-1.5 shadow-menu";
export const selectOption =
  "flex cursor-pointer items-center gap-2.5 rounded-[10px] px-2.5 text-[13.5px] text-spot-ink aria-disabled:cursor-default aria-disabled:text-spot-disabled";
export const selectOptionActive =
  "bg-spot-violet/[.09] outline outline-2 -outline-offset-2 outline-spot-violet";
export const selectGroup =
  "px-2.5 pb-1 pt-2.5 text-[11px] font-medium uppercase tracking-[0.06em] text-spot-muted";
export const selectSearch =
  "mx-0.5 mb-1.5 mt-0.5 flex h-[38px] shrink-0 items-center gap-2 rounded-[10px] bg-[#2327350d] px-2.5 text-[13.5px] text-spot-muted [&>svg]:size-[15px] [&>svg]:shrink-0";
export const selectFooter =
  "mx-0.5 mt-1.5 shrink-0 border-t border-[#23273512] px-2.5 pb-1 pt-2 text-[11.5px] text-spot-muted";
// On a phone the menu is a sheet at the bottom of the window, with a
// larger search row.
export const selectSheet =
  "fixed inset-0 m-0 mt-auto max-h-[85dvh] w-full max-w-none overflow-hidden rounded-t-3xl border-0 bg-white p-0 text-spot-ink shadow-sheet backdrop:bg-[#241d3d59]";
export const selectSheetSearch =
  "flex h-11 shrink-0 items-center gap-2 rounded-xl bg-[#2327350d] px-3 text-[15px] text-spot-muted [&>svg]:size-4 [&>svg]:shrink-0";

// The peach banner for data that failed to load, and its retry button.
export const errorBanner =
  "m-0 flex items-center gap-3.5 rounded-xl border border-white/90 bg-overdue/85 px-5 py-[18px] text-overdue-ink max-[600px]:flex-wrap max-[600px]:items-start [&>svg]:size-5 [&>svg]:shrink-0";
export const errorTitle = "block text-[14px] font-medium";
export const errorDetail = "mt-1 block text-[12px] text-overdue-muted";
export const retryButton =
  "inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-lg border border-spot-violet/15 bg-white/70 px-4 py-[11px] text-[13px] font-medium leading-[1.4] text-spot-night transition-colors hover:enabled:bg-white max-[600px]:ml-[34px]";
