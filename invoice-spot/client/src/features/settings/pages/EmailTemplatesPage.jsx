import { useState } from "react";
import { FiCheck, FiEye, FiMail, FiRotateCcw } from "react-icons/fi";
import { toast } from "react-toastify";
import useTitle from "../../../hooks/useTitle";
import {
  Badge,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Panel,
  WorkspacePage,
  errorMessage,
} from "../../../components/workspace/Workspace";
import {
  filterChip,
  panelNote,
  primaryButton,
  secondaryButton,
} from "../../../components/workspace/pastel";
import {
  useGetEmailTemplatesQuery,
  usePreviewEmailTemplateMutation,
  useResetEmailTemplateMutation,
  useSaveEmailTemplateMutation,
} from "../settingsApiSlice";
import SettingsTabs from "../components/SettingsTabs";
import { useBusiness } from "../../business/permissions";

// When each kind of email goes out.
const SENT_WHEN = {
  INVOICE:
    "When you email an invoice, and when a repeating invoice is sent automatically.",
  QUOTATION: "When you email a quotation.",
  RECEIPT: "When you email a receipt.",
  CREDIT_NOTE: "When you email a credit note.",
  REMINDER:
    "With payment reminders, whether you send them or they go out automatically.",
  PAYMENT_RECEIPT: "When you email the receipt for a payment.",
  STATEMENT: "When you email a client their statement of account.",
};

// A client named "Fresh Client Co." ends the sentence itself.
const sentence = (text) => (text.endsWith(".") ? text : `${text}.`);

const Preview = ({ preview }) =>
  preview.error ? (
    <p className={panelNote} role="status">
      {preview.error}
    </p>
  ) : (
    <div className="flex flex-col gap-3 text-[14px]">
      <p className={panelNote}>
        {sentence(
          `Filled in from ${preview.document}${preview.client ? ` for ${preview.client}` : ""}`,
        )}
      </p>
      <dl className="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2">
        <dt className="text-spot-body">To</dt>
        <dd className="m-0 text-spot-ink [overflow-wrap:anywhere]">
          {preview.to || "No email address yet"}
        </dd>
        <dt className="text-spot-body">Subject</dt>
        <dd className="m-0 text-spot-ink [overflow-wrap:anywhere]">
          {preview.subject}
        </dd>
      </dl>
      <p className="m-0 whitespace-pre-wrap rounded-2xl border border-white/90 bg-white/70 p-4 leading-[1.7] text-spot-ink [overflow-wrap:anywhere]">
        {preview.message}
      </p>
    </div>
  );

const EmailTemplates = ({ templates }) => {
  const [kind, setKind] = useState(templates[0].kind);
  // Unsaved changes, kept per kind while switching between them.
  const [drafts, setDrafts] = useState({});
  const [preview, setPreview] = useState(null);
  const [save, { isLoading: saving }] = useSaveEmailTemplateMutation();
  const [reset, { isLoading: resetting }] = useResetEmailTemplateMutation();
  const [fill, { isLoading: previewing }] = usePreviewEmailTemplateMutation();
  const refusal = useBusiness().reason("MANAGE_BUSINESS");
  const template = templates.find((candidate) => candidate.kind === kind);
  const text = drafts[kind] || {
    subject: template.subject,
    body: template.body,
  };
  const busy = saving || resetting || Boolean(refusal);
  const change = (key) => (event) =>
    setDrafts((current) => ({
      ...current,
      [kind]: { ...text, [key]: event.target.value },
    }));
  const forget = () => setDrafts(({ [kind]: _, ...others }) => others);
  const choose = (next) => {
    setKind(next);
    setPreview(null);
  };
  const submit = async (event) => {
    event.preventDefault();
    try {
      const result = await save({ kind, ...text }).unwrap();
      forget();
      toast.success(result.message || "Your email was saved.");
    } catch (error) {
      toast.error(errorMessage(error, "Your email could not be saved."));
    }
  };
  const standard = async () => {
    try {
      const result = await reset(kind).unwrap();
      forget();
      setPreview(null);
      toast.success(result.message || "Your email is the standard one again.");
    } catch (error) {
      toast.error(errorMessage(error, "Your email could not be reset."));
    }
  };
  const show = async () => {
    try {
      setPreview(await fill({ kind, ...text }).unwrap());
    } catch (error) {
      setPreview({
        error: errorMessage(error, "The preview could not be made."),
      });
    }
  };
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_400px] items-start gap-6 compact:gap-5 max-[1359px]:grid-cols-1">
      <form
        className="flex min-w-0 flex-col gap-6 compact:gap-5"
        onSubmit={submit}
      >
        {refusal && <p className={panelNote}>{refusal}.</p>}
        <fieldset className="m-0 flex min-w-0 flex-wrap gap-2 border-0 p-0">
          <legend className="sr-only">Kind of email</legend>
          {templates.map((candidate) => (
            <label key={candidate.kind} className={filterChip}>
              <input
                type="radio"
                name="email-kind"
                className="sr-only"
                checked={kind === candidate.kind}
                onChange={() => choose(candidate.kind)}
              />
              {candidate.label}
            </label>
          ))}
        </fieldset>
        <Panel
          title={template.label}
          description={SENT_WHEN[kind]}
          aside={
            <Badge tone={template.custom ? "paid" : "neutral"}>
              {template.custom ? "Your own text" : "Standard text"}
            </Badge>
          }
        >
          <div className="flex flex-col gap-5">
            <Field
              label="Subject"
              maxLength={200}
              required
              value={text.subject}
              onChange={change("subject")}
              disabled={busy}
            />
            <Field
              label="Message"
              as="textarea"
              rows={9}
              maxLength={5000}
              required
              value={text.body}
              onChange={change("body")}
              disabled={busy}
            />
            <div>
              <h3 className="mb-2 text-[13px] font-normal text-spot-body">
                Filled in for each email
              </h3>
              <dl className="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                {template.placeholders.map((placeholder) => (
                  <div key={placeholder.name} className="contents">
                    <dt className="font-mono text-spot-violet">
                      {`{${placeholder.name}}`}
                    </dt>
                    <dd className="m-0 text-spot-muted">
                      {placeholder.meaning}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
        </Panel>
        <div className="flex flex-wrap justify-end gap-2.5 max-[700px]:[&>*]:flex-1">
          {template.custom && (
            <button
              type="button"
              className={secondaryButton}
              onClick={standard}
              disabled={busy}
              title={refusal}
            >
              <FiRotateCcw aria-hidden="true" />
              {resetting ? "Resetting…" : "Use the standard text"}
            </button>
          )}
          <button
            type="button"
            className={secondaryButton}
            onClick={show}
            disabled={previewing || Boolean(refusal)}
            title={refusal}
          >
            <FiEye aria-hidden="true" />
            {previewing ? "Filling in…" : "Preview"}
          </button>
          <button
            type="submit"
            className={primaryButton}
            disabled={busy}
            title={refusal}
          >
            <FiCheck aria-hidden="true" />
            {saving ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
      <aside className="min-w-0 min-[1360px]:sticky min-[1360px]:top-[30px]">
        <Panel
          title="Preview"
          description="The email as it would go out, filled in from your latest document. Nothing is sent."
        >
          {preview ? (
            <Preview preview={preview} />
          ) : (
            <p className={`${panelNote} flex items-center gap-2`}>
              <FiMail aria-hidden="true" /> Choose Preview to see this email
              filled in.
            </p>
          )}
        </Panel>
      </aside>
    </div>
  );
};

const EmailTemplatesPage = () => {
  useTitle("Emails — Invoice Spot");
  const query = useGetEmailTemplatesQuery();
  return (
    <WorkspacePage section="Settings">
      <PageHeader
        title="Settings"
        description="What each kind of email you send says."
        actions={<SettingsTabs />}
      />
      {query.isLoading ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={query.refetch} />
      ) : (
        query.data?.templates?.length > 0 && (
          <EmailTemplates templates={query.data.templates} />
        )
      )}
    </WorkspacePage>
  );
};

export default EmailTemplatesPage;
