"use client";

import Link from "next/link";
import useSWR from "swr";
import { AlertTriangle } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { channelName } from "@/lib/format";
import type { Dashboard } from "@/lib/types";

export default function DashboardPage() {
  const { data } = useSWR<Dashboard>("/api/dashboard", { refreshInterval: 5000 });

  if (!data) {
    return <Skeleton className="h-64 w-full" />;
  }

  const problems = [
    data.reservations.conflicts > 0 && {
      text: `${data.reservations.conflicts} reservations overlap on the same car.`,
      href: "/reservations?conflictOnly=true",
    },
    data.sync.failed > 0 && { text: `${data.sync.failed} sync jobs failed.`, href: "/sync?status=FAILED" },
    data.failedWebhooks > 0 && { text: `${data.failedWebhooks} webhooks could not be applied.`, href: "/webhooks?status=FAILED" },
    data.connections.some((connection) => connection.status === "INVALID_CREDENTIALS") && {
      text: "A channel rejected its credentials; syncing to it is paused.",
      href: "/connections",
    },
  ].filter(Boolean) as { text: string; href: string }[];

  return (
    <>
      <PageHeader title="Dashboard" description="Your fleet and channel integrations at a glance." />

      {problems.length > 0 && (
        <Alert variant="warning">
          <AlertTriangle />
          <AlertTitle>Needs attention</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {problems.map((problem) => (
                <li key={problem.href}>
                  <Link href={problem.href} className="underline underline-offset-4">
                    {problem.text}
                  </Link>
                </li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat title="Published cars" value={data.vehicles.active} hint={`${data.vehicles.draft} drafts`} />
        <Stat title="Live listings" value={data.listings.published} hint={`${data.listings.pending} pending, ${data.listings.failed} failed`} />
        <Stat title="Upcoming reservations" value={data.reservations.upcoming} hint={`${data.reservations.conflicts} conflicts`} />
        <Stat title="Sync queue" value={data.sync.queued} hint={`${data.sync.failed} failed`} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Channels</CardTitle>
          <CardDescription>Connect both channels to publish your fleet everywhere.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          {(["BOOKING", "RENTALCARS"] as const).map((channel) => {
            const status = data.connections.find((connection) => connection.channel === channel)?.status ?? "NOT_CONNECTED";
            return (
              <Link key={channel} href="/connections" className="hover:bg-accent flex items-center justify-between rounded-lg border p-4">
                <span className="font-medium">{channelName(channel)}</span>
                <StatusBadge status={status} />
              </Link>
            );
          })}
        </CardContent>
      </Card>
    </>
  );
}

function Stat({ title, value, hint }: { title: string; value: number; hint: string }) {
  return (
    <Card className="gap-2">
      <CardHeader>
        <CardDescription>{title}</CardDescription>
        <CardTitle className="text-3xl tabular-nums">{value}</CardTitle>
      </CardHeader>
      <CardContent className="text-muted-foreground text-xs">{hint}</CardContent>
    </Card>
  );
}
