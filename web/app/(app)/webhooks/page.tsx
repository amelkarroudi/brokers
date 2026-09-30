"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { RotateCcw } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, errorMessage } from "@/lib/api";
import { channelName, formatDateTime } from "@/lib/format";
import type { Page, WebhookEvent } from "@/lib/types";

export default function WebhooksPage() {
  return (
    <Suspense>
      <Webhooks />
    </Suspense>
  );
}

function Webhooks() {
  const searchParams = useSearchParams();
  const [status, setStatus] = useState(searchParams.get("status") ?? "ALL");
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  const params = new URLSearchParams({ page: String(page), size: "25" });
  if (status !== "ALL") {
    params.set("status", status);
  }
  const { data, mutate } = useSWR<Page<WebhookEvent>>(`/api/webhook-events?${params}`, { refreshInterval: 5000 });
  const { data: detail, mutate: mutateDetail } = useSWR<WebhookEvent>(openId ? `/api/webhook-events/${openId}` : null);

  async function replay(id: string) {
    try {
      const result = await api<WebhookEvent>(`/api/webhook-events/${id}/replay`, "POST");
      toast.success(`Replayed: ${result.status.toLowerCase()}`);
      await Promise.all([mutate(), mutateDetail()]);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="Webhooks"
        description="Every authentic delivery from your channels. Duplicates are acknowledged once; failed events can be replayed."
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
          <SelectItem value="ALL">All events</SelectItem>
          <SelectItem value="PROCESSED">Processed</SelectItem>
          <SelectItem value="IGNORED">Ignored</SelectItem>
          <SelectItem value="FAILED">Failed</SelectItem>
        </SelectContent>
      </Select>

      {data && data.totalItems === 0 ? (
        <EmptyState title="No webhooks received" description="Configure the webhook URL and secret from the Channels page in each extranet." />
      ) : (
        <Card className="py-0">
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Received</TableHead>
                  <TableHead>Channel</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data?.items ?? []).map((event) => (
                  <TableRow key={event.id} className="cursor-pointer" onClick={() => setOpenId(event.id)}>
                    <TableCell className="pl-4">{formatDateTime(event.receivedAt)}</TableCell>
                    <TableCell>{channelName(event.channel)}</TableCell>
                    <TableCell className="font-mono text-xs">{event.eventType}</TableCell>
                    <TableCell className="max-w-80 whitespace-normal">
                      <StatusBadge status={event.status} />
                      {event.lastError && <p className="text-muted-foreground mt-1 line-clamp-2 text-xs">{event.lastError}</p>}
                    </TableCell>
                    <TableCell className="text-right">
                      {event.status === "FAILED" && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={(clickEvent) => {
                            clickEvent.stopPropagation();
                            replay(event.id);
                          }}
                        >
                          <RotateCcw /> Replay
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

      <Dialog open={openId !== null} onOpenChange={(open) => !open && setOpenId(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{detail?.eventType ?? "Webhook"}</DialogTitle>
          </DialogHeader>
          {detail && (
            <div className="grid gap-3 text-sm">
              <div className="flex items-center gap-2">
                <StatusBadge status={detail.status} />
                <span className="text-muted-foreground">
                  {detail.externalEventId} · {detail.attempts} attempt(s)
                </span>
              </div>
              {detail.lastError && <p className="text-muted-foreground">{detail.lastError}</p>}
              <pre className="bg-muted max-h-96 overflow-auto rounded-md p-3 text-xs">{prettyJson(detail.payload)}</pre>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

function prettyJson(payload?: string): string {
  if (!payload) {
    return "";
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}
