"use client";

import useSWR from "swr";
import { FlaskConical } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api";
import { channelName, formatDateTime } from "@/lib/format";
import type { Channel, SandboxListing } from "@/lib/types";

const CHANNELS: Channel[] = ["BOOKING", "RENTALCARS"];

export default function SandboxPage() {
  return (
    <>
      <PageHeader
        title="Channel sandbox"
        description="What the built-in fake Booking.com and Rentalcars.com hold for your accounts, in each channel's own format."
      />
      <Alert>
        <FlaskConical />
        <AlertTitle>Development only</AlertTitle>
        <AlertDescription>
          The API points at these fake channels by default. Set BOOKING_API_URL and RENTALCARS_API_URL to the real partner
          endpoints and BROKERS_SANDBOX_ENABLED=false in production.
        </AlertDescription>
      </Alert>
      <Tabs defaultValue="BOOKING">
        <TabsList>
          {CHANNELS.map((channel) => (
            <TabsTrigger key={channel} value={channel}>
              {channelName(channel)}
            </TabsTrigger>
          ))}
        </TabsList>
        {CHANNELS.map((channel) => (
          <TabsContent key={channel} value={channel}>
            <ChannelListings channel={channel} />
          </TabsContent>
        ))}
      </Tabs>
    </>
  );
}

function ChannelListings({ channel }: { channel: Channel }) {
  const { data, error } = useSWR<SandboxListing[]>(`/api/sandbox/channels/${channel}/listings`, {
    refreshInterval: 3000,
    shouldRetryOnError: false,
  });

  if (error) {
    const notConnected = error instanceof ApiError && error.status === 404;
    return (
      <p className="text-muted-foreground text-sm">
        {notConnected ? `Connect ${channelName(channel)} first.` : "The sandbox is not enabled on the API."}
      </p>
    );
  }
  if (data?.length === 0) {
    return <p className="text-muted-foreground text-sm">No listings yet. Publish a car to see it appear here.</p>;
  }

  return (
    <div className="grid gap-4">
      {(data ?? []).map((listing) => (
        <Card key={listing.externalId} className="gap-3">
          <CardHeader>
            <CardTitle className="font-mono text-sm">{listing.externalId}</CardTitle>
            <CardDescription>Updated {formatDateTime(listing.updatedAt)}</CardDescription>
            <CardAction>
              <StatusBadge status={listing.active ? "ACTIVE" : "UNPUBLISHED"} />
            </CardAction>
          </CardHeader>
          <CardContent className="grid items-start gap-3 lg:grid-cols-3">
            <JsonBlock title="Listing" value={listing.content} />
            <JsonBlock title="Photos" value={listing.photos} />
            <JsonBlock title="Availability" value={listing.availability} />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function JsonBlock({ title, value }: { title: string; value: unknown }) {
  return (
    <div className="grid gap-1">
      <span className="text-muted-foreground text-xs font-medium">{title}</span>
      <pre className="bg-muted max-h-72 overflow-auto rounded-md p-3 text-xs">
        {value === undefined || value === null ? "—" : JSON.stringify(value, null, 2)}
      </pre>
    </div>
  );
}
