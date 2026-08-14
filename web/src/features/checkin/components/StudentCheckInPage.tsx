import { useEffect, useRef, type ReactNode } from "react";
import { useParams, useSearchParams } from "react-router";
import type { ApiError } from "../../../lib/apiError";
import { formatCampusTime } from "../../../lib/campusTimeZone";
import { describeCheckInFailure } from "../describeCheckInFailure";
import { useCheckIn } from "../hooks/useCheckIn";

/**
 * The Student's side of the door, covering every state the prototype found — see
 * docs/planning/prototypes/10-prototype-student-registration-and-checkin.md.
 *
 * The Student arrives here by pointing their own camera at the door screen, so the rotating code is
 * already in the URL and the scan is submitted on arrival. Which screen they then see is decided by
 * the stable `code` on the refusal, never by its status or its wording.
 */
export function StudentCheckInPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const token = searchParams.get("token");
  const checkIn = useCheckIn(eventId ?? "");
  const { mutate, reset } = checkIn;
  const submittedToken = useRef<string | null>(null);

  useEffect(() => {
    if (eventId === undefined || token === null || submittedToken.current === token) {
      return;
    }
    // One scan per code. Without this the Student could be told "already checked in" by their own
    // second request, which is true and useless.
    submittedToken.current = token;
    mutate(token);
  }, [eventId, token, mutate]);

  if (eventId === undefined) {
    return (
      <Screen headline="Check in" tone="bad">
        <p role="alert">No Event was specified.</p>
      </Screen>
    );
  }

  if (token === null) {
    return (
      <Screen headline="Check in" tone="neutral">
        <p>Point your camera at the code on the screen at the door.</p>
        <p className="text-sm text-slate-600">
          The code changes every minute, so it only works while you are in the room. You will be checked
          in as yourself — you are already signed in.
        </p>
      </Screen>
    );
  }

  if (checkIn.isPending) {
    return (
      <Screen headline="Check in" tone="neutral">
        <p role="status">Checking you in…</p>
      </Screen>
    );
  }

  if (checkIn.isSuccess) {
    return (
      <Screen headline="Checked in" tone="good">
        <p className="text-5xl" aria-hidden="true">
          ✓
        </p>
        <p className="text-lg font-medium">{checkIn.data.eventTitle}</p>
        <p>{formatCampusTime(checkIn.data.at)}</p>
      </Screen>
    );
  }

  if (checkIn.isError) {
    // "Scan again" drops the stale code and returns to the ready screen, because re-sending a code the
    // server already refused would only be refused again — a fresh one off the screen is the way through.
    const rescan = () => {
      reset();
      submittedToken.current = null;
      setSearchParams({});
    };
    return <Refusal error={checkIn.error} onRetry={() => mutate(token)} onRescan={rescan} />;
  }

  return (
    <Screen headline="Check in" tone="neutral">
      <p role="status">Checking you in…</p>
    </Screen>
  );
}

interface RefusalProps {
  error: ApiError;
  onRetry: () => void;
  onRescan: () => void;
}

function Refusal({ error, onRetry, onRescan }: RefusalProps) {
  const failure = describeCheckInFailure(error.code);
  // An expired code is the most common outcome and the proof the mechanism works, so it is worded and
  // coloured as a normal retry rather than as an error.
  const tone = error.code === "ALREADY_CHECKED_IN" ? "good" : failure.action === "RESCAN" ? "neutral" : "bad";
  const checkedInAt = error.stringExtension("at");

  return (
    <Screen headline={failure.headline} tone={tone}>
      <p>{failure.message}</p>
      {checkedInAt !== null && <p>You checked in at {formatCampusTime(checkedInAt)}.</p>}
      {failure.action === "RETRY" && (
        <button type="button" onClick={onRetry} className="rounded bg-slate-900 px-4 py-2 text-white">
          Try again
        </button>
      )}
      {failure.action === "RESCAN" && (
        <button type="button" onClick={onRescan} className="rounded bg-slate-900 px-4 py-2 text-white">
          Scan again
        </button>
      )}
    </Screen>
  );
}

const TONES = {
  good: "border-emerald-600 text-emerald-800",
  bad: "border-rose-600 text-rose-800",
  neutral: "border-slate-400 text-slate-800",
};

interface ScreenProps {
  headline: string;
  tone: keyof typeof TONES;
  children: ReactNode;
}

/**
 * Every state of this page is one screen with one headline, and the headline is the page's `h1`
 * rather than a styled paragraph — it is the only thing that says which of the outcomes this is, so
 * it is what a Student arriving with a screen reader should land on. The tone is a border colour
 * agreeing with words that already say the same thing; nothing here is carried by colour alone.
 */
function Screen({ headline, tone, children }: ScreenProps) {
  return (
    <main className="mx-auto flex max-w-sm flex-col gap-4 p-6">
      <h1 className={`w-fit rounded-full border px-3 py-1 text-base font-medium ${TONES[tone]}`}>
        {headline}
      </h1>
      {children}
    </main>
  );
}
