#!/usr/bin/env bash
# The guard-removal experiment behind docs/planning/implementation/EVIDENCE.md.
#
# For each of the four concurrency claims: delete the guard from the production code, run only that
# claim's tagged test, and report whether it failed. A test that still passes with its guard deleted is
# not evidence of the guard, which is the whole reason this exists.
#
# It is deliberately NOT part of `mvnw verify`. The result only changes when someone edits a guard, and
# a mutation harness maintained for four of them would cost more than the fact is worth. Run it by hand
# when a guard changes, and update the table in EVIDENCE.md with what it reports.
#
# The patched source is restored with `git checkout --` after every run, pass or fail. Run it from a
# clean working tree: an uncommitted edit to either repository file is discarded.
set -u

cd "$(dirname "$0")/.." || exit 1

EVENT_REPO=src/main/java/com/campushub/event/persistence/EventRepository.java
VENUE_REPO=src/main/java/com/campushub/venue/persistence/VenueRepository.java
SCRATCH=$(mktemp -d)
OUT=$SCRATCH/guard-removal.log
: >"$OUT"

if ! git diff --quiet -- "$EVENT_REPO" "$VENUE_REPO"; then
  echo "Refusing to run: $EVENT_REPO or $VENUE_REPO has uncommitted changes, which this would discard." >&2
  exit 1
fi

# Every run below is expected to fail, so a toolchain that cannot build is indistinguishable from a
# guard that was load-bearing unless the baseline is established first. Prove the suite is green
# before breaking anything.
echo "Baseline: the four tests must pass before any guard is removed."
if ! ./mvnw --batch-mode verify -Pconcurrency >"$SCRATCH/baseline.log" 2>&1; then
  echo "Refusing to run: the concurrency suite is not green to begin with." >&2
  tail -25 "$SCRATCH/baseline.log" >&2
  exit 1
fi

FAILED_TO_CONCLUDE=0

restore() { git checkout -- "$EVENT_REPO" "$VENUE_REPO"; }
trap restore EXIT

patch_out() { # file, path-to-heredoc-old, path-to-heredoc-new
  python3 - "$1" "$2" "$3" <<'PY'
import sys
path, old_file, new_file = sys.argv[1:4]
old = open(old_file).read()
new = open(new_file).read()
src = open(path).read()
if old not in src:
    sys.exit("GUARD NOT FOUND in " + path)
open(path, "w").write(src.replace(old, new, 1))
PY
}

run_claim() {
  local name="$1" test_filter="$2"
  echo "=== $name ===" | tee -a "$OUT"
  ./mvnw --batch-mode verify \
    -Djacoco.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true \
    -Dtest=ModularityTest -DfailIfNoSpecifiedTests=false \
    -Dit.test="$test_filter" >"$SCRATCH/raw.log" 2>&1

  # A non-zero exit is NOT the signal. A compile break, a missing JDK or a container that would not
  # start all exit non-zero too, and reading those as "the guard is load-bearing" would make this
  # script manufacture exactly the false evidence it exists to rule out. The signal is the test
  # having run and been judged: `Tests run: N, Failures: >0`.
  local summary
  summary=$(grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+' "$SCRATCH/raw.log" | tail -1)
  grep -E 'expecting:|but was:|to be equal to:|expected:|actual:|to contain exactly' "$SCRATCH/raw.log" \
    | head -6 | tee -a "$OUT"

  case "$summary" in
    "")
      echo "RESULT: INCONCLUSIVE — the test never ran. The build broke before it could." | tee -a "$OUT"
      echo "        Full output: $SCRATCH/raw.log" | tee -a "$OUT"
      FAILED_TO_CONCLUDE=1
      ;;
    *"Failures: 0, Errors: 0")
      echo "RESULT: STILL PASSED with the guard removed — NOT EVIDENCE." | tee -a "$OUT"
      FAILED_TO_CONCLUDE=1
      ;;
    *)
      echo "RESULT: FAILED with the guard removed — the guard is load-bearing. ($summary)" | tee -a "$OUT"
      ;;
  esac
  echo | tee -a "$OUT"
  restore
}

# ---- 1. Capacity guard --------------------------------------------------------------------------
cat >"$SCRATCH/old" <<'EOF'
                .and("waitlist")
                .ne(studentId)
                .and("$expr")
                .is(new Document("$lt", List.of(new Document("$size", "$enrolled"), "$capacity")));
EOF
cat >"$SCRATCH/new" <<'EOF'
                .and("waitlist")
                .ne(studentId);
EOF
patch_out "$EVENT_REPO" "$SCRATCH/old" "$SCRATCH/new" || exit 1
run_claim "1. Capacity guard — nParallelRegistrationsAgainstACapacityOfMProduceExactlyMEnrolments" \
  "EventRepositorySeatLedgerIntegrationTest#nParallelRegistrationsAgainstACapacityOfMProduceExactlyMEnrolments"

# ---- 2. One promotion per freed Seat -------------------------------------------------------------
cat >"$SCRATCH/old" <<'EOF'
                .and("startsAt")
                .gt(now)
                .and("enrolled.studentId")
                .is(studentId));

        Document studentsToPromote = promotionCount(1);
EOF
cat >"$SCRATCH/new" <<'EOF'
                .and("startsAt")
                .gt(now));

        Document studentsToPromote = promotionCount(1);
EOF
patch_out "$EVENT_REPO" "$SCRATCH/old" "$SCRATCH/new" || exit 1
run_claim "2. One promotion per freed Seat — parallelWithdrawalsOfOneSeatPromoteExactlyOneStudent" \
  "EventRepositorySeatLedgerIntegrationTest#parallelWithdrawalsOfOneSeatPromoteExactlyOneStudent"

# ---- 3. Check-in idempotency ---------------------------------------------------------------------
cat >"$SCRATCH/old" <<'EOF'
                .and("enrolled.studentId")
                .is(studentId)
                .and("attendance.studentId")
                .ne(studentId);
EOF
cat >"$SCRATCH/new" <<'EOF'
                .and("enrolled.studentId")
                .is(studentId);
EOF
patch_out "$EVENT_REPO" "$SCRATCH/old" "$SCRATCH/new" || exit 1
run_claim "3. Check-in idempotency — nParallelScansByOneStudentProduceExactlyOneAttendanceRecord" \
  "EventRepositoryAttendanceIntegrationTest#nParallelScansByOneStudentProduceExactlyOneAttendanceRecord"

# ---- 4. Venue overlap guard ----------------------------------------------------------------------
cat >"$SCRATCH/old" <<'EOF'
                .append("date", date.toString())
                .append("bookings", overlap);
EOF
cat >"$SCRATCH/new" <<'EOF'
                .append("date", date.toString());
EOF
patch_out "$VENUE_REPO" "$SCRATCH/old" "$SCRATCH/new" || exit 1
run_claim "4. Venue overlap guard — parallelOverlappingSlotRequestsHaveExactlyOneWinner" \
  "VenueModuleImplIntegrationTest#parallelOverlappingSlotRequestsHaveExactlyOneWinner"

echo "--- working tree after restore (must be empty) ---" | tee -a "$OUT"
git status --porcelain "$EVENT_REPO" "$VENUE_REPO" | tee -a "$OUT"

if [ "$FAILED_TO_CONCLUDE" -ne 0 ]; then
  echo "At least one claim did not produce evidence. See the results above." >&2
  exit 1
fi
echo "All four guards are load-bearing."
rm -rf "$SCRATCH"
