"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Field } from "@/components/field";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function SignupPage() {
  const router = useRouter();
  const { signup } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await signup({
        organizationName: String(form.get("organizationName")),
        fullName: String(form.get("fullName")),
        email: String(form.get("email")),
        password: String(form.get("password")),
        defaultCurrency: String(form.get("defaultCurrency") || "EUR"),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      });
      router.replace("/connections");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Create your organization</CardTitle>
        <CardDescription>One login per organization. You can connect your channels right after.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit}>
        <CardContent className="grid gap-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <Field label="Company name" htmlFor="organizationName">
            <Input id="organizationName" name="organizationName" required maxLength={160} />
          </Field>
          <Field label="Your name" htmlFor="fullName">
            <Input id="fullName" name="fullName" required maxLength={160} />
          </Field>
          <Field label="Email" htmlFor="email">
            <Input id="email" name="email" type="email" autoComplete="email" required />
          </Field>
          <Field label="Password" htmlFor="password" hint="At least 10 characters with a letter and a digit.">
            <Input id="password" name="password" type="password" autoComplete="new-password" minLength={10} required />
          </Field>
          <Field label="Currency" htmlFor="defaultCurrency" hint="ISO code used for your rates, e.g. EUR, MAD, USD.">
            <Input id="defaultCurrency" name="defaultCurrency" defaultValue="EUR" maxLength={3} pattern="[A-Za-z]{3}" />
          </Field>
        </CardContent>
        <CardFooter className="mt-6 flex flex-col gap-3">
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "Creating…" : "Create organization"}
          </Button>
          <p className="text-muted-foreground text-sm">
            Already registered?{" "}
            <Link href="/login" className="text-foreground underline underline-offset-4">
              Sign in
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
