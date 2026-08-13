/** Full-page loading state shared by RequireAuth and SignInPage while the session check is pending. */
export function LoadingScreen() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p role="status">Loading…</p>
    </main>
  );
}
