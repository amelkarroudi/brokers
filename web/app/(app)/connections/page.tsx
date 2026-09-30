"use client";

import { useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Copy, KeyRound, RefreshCw, Unplug } from "lucide-react";

import { Field } from "@/components/field";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { api, errorMessage } from "@/lib/api";
import { channelName, formatDateTime } from "@/lib/format";
import type { Channel, Connection, ConnectionSecret } from "@/lib/types";

const HINTS: Record<Channel, { account: string; key: string; secret: string }> = {
  BOOKING: { account: "Supplier ID", key: "Machine account username", secret: "Machine account password" },
  RENTALCARS: { account: "Supplier code", key: "OAuth client ID", secret: "OAuth client secret" },
};

export default function ConnectionsPage() {
  const { data: connections, mutate } = useSWR<Connection[]>("/api/connections");
  const [editing, setEditing] = useState<Channel | null>(null);
  const [revealedSecret, setRevealedSecret] = useState<{ channel: Channel; secret: string } | null>(null);

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      toast.success(success);
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  function onSecretIssued(channel: Channel, result: ConnectionSecret) {
    if (!result.webhookSecret) {
      return;
    }
    setRevealedSecret({ channel, secret: result.webhookSecret });
  }

  return (
    <>
      <PageHeader
        title="Channels"
        description="Connect each channel once. Listings, photos and availability are then kept in sync automatically."
      />

      {revealedSecret && (
        <Alert variant="warning">
          <KeyRound />
          <AlertTitle>Webhook secret for {channelName(revealedSecret.channel)}. Copy it now; it is shown only once.</AlertTitle>
          <AlertDescription>
            <p>Paste it, with the webhook URL below, into the channel&apos;s extranet so it can send you reservations.</p>
            <CopyValue value={revealedSecret.secret} />
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {(connections ?? []).map((connection) => (
          <Card key={connection.channel}>
            <CardHeader>
              <CardTitle>{channelName(connection.channel)}</CardTitle>
              <CardDescription>
                {connection.accountId ? `Account ${connection.accountId} · key ${connection.apiKeyHint}` : "Not connected yet"}
              </CardDescription>
              <CardAction>
                <StatusBadge status={connection.status} />
              </CardAction>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              {connection.lastError && (
                <Alert variant="destructive">
                  <AlertDescription className="break-all">{connection.lastError}</AlertDescription>
                </Alert>
              )}
              {connection.webhookUrl && (
                <div className="grid gap-1">
                  <span className="text-muted-foreground text-xs">Webhook URL</span>
                  <CopyValue value={connection.webhookUrl} />
                </div>
              )}
              {connection.lastVerifiedAt && (
                <p className="text-muted-foreground text-xs">Last verified {formatDateTime(connection.lastVerifiedAt)}</p>
              )}
            </CardContent>
            <CardFooter className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => setEditing(connection.channel)}>
                {connection.status === "NOT_CONNECTED" || connection.status === "DISCONNECTED" ? "Connect" : "Update credentials"}
              </Button>
              {connection.status !== "NOT_CONNECTED" && connection.status !== "DISCONNECTED" && (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => run(() => api(`/api/connections/${connection.channel}/verify`, "POST"), "Credentials checked")}
                  >
                    <RefreshCw /> Verify
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      run(async () => {
                        const result = await api<ConnectionSecret>(`/api/connections/${connection.channel}/webhook-secret`, "POST");
                        onSecretIssued(connection.channel, result);
                      }, "New webhook secret issued")
                    }
                  >
                    <KeyRound /> Rotate secret
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      if (!confirm(`Disconnect ${channelName(connection.channel)}? All your cars will be taken off sale there.`)) {
                        return;
                      }
                      run(() => api(`/api/connections/${connection.channel}`, "DELETE"), "Channel disconnected");
                    }}
                  >
                    <Unplug /> Disconnect
                  </Button>
                </>
              )}
            </CardFooter>
          </Card>
        ))}
      </div>

      <ConnectDialog
        channel={editing}
        onClose={() => setEditing(null)}
        onConnected={async (channel, result) => {
          setEditing(null);
          onSecretIssued(channel, result);
          toast.success(`${channelName(channel)} connected. Publishing your fleet…`);
          await mutate();
        }}
      />
    </>
  );
}

function ConnectDialog({
  channel,
  onClose,
  onConnected,
}: {
  channel: Channel | null;
  onClose: () => void;
  onConnected: (channel: Channel, result: ConnectionSecret) => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!channel) {
      return;
    }
    const form = new FormData(event.currentTarget);
    const webhookSecret = String(form.get("webhookSecret") ?? "").trim();
    setSubmitting(true);
    setError(null);
    try {
      const result = await api<ConnectionSecret>(`/api/connections/${channel}`, "PUT", {
        accountId: form.get("accountId"),
        apiKey: form.get("apiKey"),
        apiSecret: form.get("apiSecret"),
        webhookSecret: webhookSecret || undefined,
      });
      onConnected(channel, result);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  const hints = channel ? HINTS[channel] : HINTS.BOOKING;
  return (
    <Dialog open={channel !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Connect {channel && channelName(channel)}</DialogTitle>
          <DialogDescription>
            Credentials are checked with the channel before they are saved, and stored encrypted.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="grid gap-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <Field label={hints.account} htmlFor="accountId">
            <Input id="accountId" name="accountId" required pattern="[A-Za-z0-9._\-]+" />
          </Field>
          <Field label={hints.key} htmlFor="apiKey">
            <Input id="apiKey" name="apiKey" required autoComplete="off" />
          </Field>
          <Field label={hints.secret} htmlFor="apiSecret" hint="In the sandbox, any value works except “invalid”.">
            <Input id="apiSecret" name="apiSecret" type="password" required autoComplete="off" />
          </Field>
          <Field
            label="Webhook secret (optional)"
            htmlFor="webhookSecret"
            hint="Leave empty to generate one, or paste the secret the channel's extranet gave you."
          >
            <Input id="webhookSecret" name="webhookSecret" type="password" minLength={16} autoComplete="off" />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Checking…" : "Connect"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CopyValue({ value }: { value: string }) {
  return (
    <div className="bg-muted flex items-center gap-2 rounded-md px-3 py-2">
      <code className="flex-1 truncate font-mono text-xs">{value}</code>
      <Button
        size="icon"
        variant="ghost"
        className="size-7"
        onClick={async () => {
          await navigator.clipboard.writeText(value);
          toast.success("Copied");
        }}
      >
        <Copy />
      </Button>
    </div>
  );
}
