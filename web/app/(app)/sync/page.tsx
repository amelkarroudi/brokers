"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Play, RotateCcw } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, errorMessage } from "@/lib/api";
import { channelName, formatDateTime, humanize } from "@/lib/format";
import type { Page, SyncJob } from "@/lib/types";

export default function SyncPage() {
  return (
    <Suspense>
      <SyncLog />
    </Suspense>
  );
}

function SyncLog() {
  const searchParams = useSearchParams();
  const [status, setStatus] = useState(searchParams.get("status") ?? "ALL");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams({ page: String(page), size: "25" });
  if (status !== "ALL") {
    params.set("status", status);
  }
  const { data, mutate } = useSWR<Page<SyncJob>>(`/api/sync-jobs?${params}`, { refreshInterval: 3000 });

  async function retry(job: SyncJob) {
    try {
      await api(`/api/sync-jobs/${job.id}/retry`, "POST");
      toast.success("Job queued again");
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  async function runNow() {
    try {
      const result = await api<{ processedJobs: number }>("/api/sandbox/sync/run", "POST");
      toast.success(`Processed ${result.processedJobs} jobs`);
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="Sync log"
        description="Every push to a channel. Failed pushes are retried automatically with backoff; permanent failures can be retried here."
        actions={
          <Button variant="outline" onClick={runNow}>
            <Play /> Run queue now
          </Button>
        }
      />

      <Select
        value={status}
        onValueChange={(value) => {
          setStatus(value);
          setPage(0);
        }}
      >
        <SelectTrigger className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All jobs</SelectItem>
          <SelectItem value="PENDING">Pending</SelectItem>
          <SelectItem value="RUNNING">Running</SelectItem>
          <SelectItem value="SUCCEEDED">Succeeded</SelectItem>
          <SelectItem value="FAILED">Failed</SelectItem>
        </SelectContent>
      </Select>

      {data && data.totalItems === 0 ? (
        <EmptyState title="No sync jobs" description="Jobs are created whenever a published car, its photos or its availability change." />
      ) : (
        <Card className="py-0">
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Created</TableHead>
                  <TableHead>Channel</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Why</TableHead>
                  <TableHead>Attempts</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data?.items ?? []).map((job) => (
                  <TableRow key={job.id}>
                    <TableCell className="pl-4">
                      <Link href={`/vehicles/${job.vehicleId}`} className="hover:underline">
                        {formatDateTime(job.createdAt)}
                      </Link>
                    </TableCell>
                    <TableCell>{channelName(job.channel)}</TableCell>
                    <TableCell>{humanize(job.type)}</TableCell>
                    <TableCell className="text-muted-foreground">{humanize(job.trigger)}</TableCell>
                    <TableCell className="tabular-nums">
                      {job.attempts}/{job.maxAttempts}
                    </TableCell>
                    <TableCell className="max-w-72 whitespace-normal">
                      <StatusBadge status={job.status} />
                      {job.status === "PENDING" && job.attempts > 0 && (
                        <p className="text-muted-foreground mt-1 text-xs">Next try {formatDateTime(job.nextAttemptAt)}</p>
                      )}
                      {job.lastError && <p className="text-destructive mt-1 line-clamp-2 text-xs">{job.lastError}</p>}
                    </TableCell>
                    <TableCell className="text-right">
                      {job.status === "FAILED" && (
                        <Button size="sm" variant="outline" onClick={() => retry(job)}>
                          <RotateCcw /> Retry
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
