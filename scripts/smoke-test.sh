#!/usr/bin/env bash
#
# The deployment's own test. Everything else in this repository proves the code; this proves that one
# particular running instance of it is actually serving — which is the one claim a public URL makes and
# the one thing a green build cannot check.
#
# It is deliberately the same script for the composed stack and for the deployed URL. CI runs it against
# localhost on every build, so it is exercised long before a host exists and cannot rot into a script
# that only ever ran once, by hand, on release day.
#
# Usage:
#   scripts/smoke-test.sh http://localhost:8080
#   scripts/smoke-test.sh https://campushub.example [email] [password]
#
# Only curl and a POSIX shell are required — no jq, so this runs on a bare host as readily as in CI.

set -euo pipefail

BASE_URL="${1:-}"
# The credentials have an environment form as well as a positional one because CI passes them from a
# repository variable and a secret, and a password on a command line is a password in a process list.
EMAIL="${2:-${SMOKE_EMAIL:-student@demo.campushub}}"
PASSWORD="${3:-${SMOKE_PASSWORD:-123456}}"

if [ -z "$BASE_URL" ]; then
    echo "usage: $0 <base-url> [email] [password]" >&2
    exit 2
fi
BASE_URL="${BASE_URL%/}"

JAR="$(mktemp)"
BODY="$(mktemp)"
trap 'rm -f "$JAR" "$BODY"' EXIT

failures=0

# Prints the outcome of one check and remembers a failure rather than exiting, so a single run reports
# everything that is wrong with the deployment instead of only the first thing.
check() {
    if [ "$1" = "pass" ]; then
        printf '  ok   %s\n' "$2"
    else
        printf '  FAIL %s\n' "$2" >&2
        failures=$((failures + 1))
    fi
}

# Body into $BODY, HTTP status onto stdout. Never fails the script itself: a 500 is a result to assert
# against, not a reason to stop asking questions.
request() {
    curl --silent --show-error --location \
        --cookie "$JAR" --cookie-jar "$JAR" \
        --output "$BODY" --write-out '%{http_code}' \
        "$@"
}

expect_status() {
    local expected="$1" description="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then
        check pass "$description"
    else
        check fail "$description (expected HTTP $expected, got $actual)"
    fi
}

echo "Smoke-testing $BASE_URL"

status="$(request "$BASE_URL/actuator/health")"
expect_status 200 "health endpoint answers" "$status"
if grep -q '"status":"UP"' "$BODY"; then
    check pass "health reports UP"
else
    check fail "health reports UP (body: $(head -c 200 "$BODY"))"
fi

status="$(request "$BASE_URL/api/system")"
expect_status 200 "/api/system answers unauthenticated" "$status"

# The single-origin claim in the README: the React build and its own API come off the same host, so
# there is one process to run and one port to open.
status="$(request "$BASE_URL/")"
expect_status 200 "the web app is served from the same origin" "$status"
if grep -qi '<div id="root"' "$BODY"; then
    check pass "the served page is the built React app"
else
    check fail "the served page is the built React app"
fi

# A deployment that answers this one unauthenticated has published every Student's data. It is the
# cheapest possible check and the most expensive thing to get wrong, so it runs before signing in.
status="$(request "$BASE_URL/api/events")"
expect_status 401 "browsing Events is refused without a session" "$status"

csrf_token() {
    awk '$6 == "XSRF-TOKEN" { token = $7 } END { print token }' "$JAR"
}

status="$(request --request POST "$BASE_URL/api/auth/login" \
    --header "X-XSRF-TOKEN: $(csrf_token)" \
    --data-urlencode "email=$EMAIL" \
    --data-urlencode "password=$PASSWORD")"
# 204, not 200: the success handler answers with no body at all, and the session cookie is the whole
# result. See JsonAuthenticationSuccessHandler.
expect_status 204 "the published demo account signs in" "$status"

status="$(request "$BASE_URL/api/auth/me")"
expect_status 200 "the session survives the redirect to a second request" "$status"
if grep -q "\"email\":\"$EMAIL\"" "$BODY"; then
    check pass "the session belongs to $EMAIL"
else
    check fail "the session belongs to $EMAIL (body: $(head -c 200 "$BODY"))"
fi

# Seeded demo data is part of what the public URL promises: a stranger who signs in has to land on
# something. An empty database answers 200 here and would otherwise pass silently.
status="$(request "$BASE_URL/api/events")"
expect_status 200 "signed in, Events browse answers" "$status"
total="$(tr ',' '\n' <"$BODY" | sed -n 's/.*"total":\([0-9][0-9]*\).*/\1/p' | head -n 1)"
if [ -n "$total" ] && [ "$total" -gt 0 ]; then
    check pass "the environment has seeded Events to show ($total)"
else
    check fail "the environment has seeded Events to show (total: ${total:-absent})"
fi

if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed against $BASE_URL" >&2
    exit 1
fi
echo "All checks passed against $BASE_URL"
