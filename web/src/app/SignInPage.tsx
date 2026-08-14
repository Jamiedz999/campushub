import { useState } from "react";
import type { FormEvent } from "react";
import { Navigate } from "react-router";
import { LoadingScreen } from "../components/LoadingScreen";
import { useCurrentActor, useLogin } from "../lib/auth";

function errorMessage(code: string): string {
  if (code === "INVALID_CREDENTIALS") {
    return "Incorrect email or password.";
  }
  return "Something went wrong. Please try again.";
}

export function SignInPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const currentActor = useCurrentActor();
  const login = useLogin();

  if (currentActor.status === "pending") {
    return <LoadingScreen />;
  }

  if (currentActor.status === "success") {
    return <Navigate to="/" replace />;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    login.mutate({ email, password });
  }

  return (
    <main className="flex min-h-screen items-center justify-center">
      <form onSubmit={handleSubmit} className="flex w-full max-w-sm flex-col gap-4 rounded-lg border p-6 shadow-sm">
        <h1 className="text-xl font-semibold">Sign in</h1>
        <label className="flex flex-col gap-1">
          <span>Email</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            autoComplete="username"
            className="rounded border px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            autoComplete="current-password"
            className="rounded border px-3 py-2"
          />
        </label>
        {login.isError && (
          <p role="alert" className="text-red-700">
            {errorMessage(login.error.code)}
          </p>
        )}
        <button
          type="submit"
          disabled={login.isPending}
          className="rounded bg-slate-900 px-3 py-2 text-white disabled:opacity-50"
        >
          {login.isPending ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
