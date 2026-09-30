"use client";

import useSWR from "swr";

import { StatusBadge } from "@/components/status-badge";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { channelName, formatDateTime, humanize } from "@/lib/format";
import type { Channel, Page, SyncJob, Vehicle } from "@/lib/types";

const CHANNELS: Channel[] = ["BOOKING", "RENTALCARS"];

/** Where the car is listed and the latest pushes made for it. */
export function ChannelsPanel({ vehicle }: { vehicle: Vehicle }) {
  const { data: jobs } = useSWR<Page<SyncJob>>(`/api/sync-jobs?vehicleId=${vehicle.id}&size=10`, { refreshInterval: 4000 });

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 md:grid-cols-2">
        {CHANNELS.map((channel) => {
          const listing = vehicle.listings.find((candidate) => candidate.channel === channel);
          return (
            <Card key={channel} className="gap-3">
              <CardHeader>
                <CardTitle>{channelName(channel)}</CardTitle>
                <CardDescription>
                  {listing?.externalId ? `Listing ${listing.externalId}` : "Not listed yet"}
                </CardDescription>
                <CardAction>
                  <StatusBadge status={listing?.status ?? "NOT_CONNECTED"} />
                </CardAction>
              </CardHeader>
              <CardContent className="text-muted-foreground grid gap-1 text-xs">
                <span>Last synced {formatDateTime(listing?.lastSyncedAt)}</span>
                {listing?.lastError && <span className="text-destructive break-all">{listing.lastError}</span>}
              </CardContent>
            </Card>
          );
        })}
      </div>

      <Card className="gap-2 pb-0">
        <CardHeader>
          <CardTitle className="text-base">Recent sync activity</CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">When</TableHead>
                <TableHead>Channel</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Why</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(jobs?.items ?? []).map((job) => (
                <TableRow key={job.id}>
                  <TableCell className="pl-6">{formatDateTime(job.createdAt)}</TableCell>
                  <TableCell>{channelName(job.channel)}</TableCell>
                  <TableCell>{humanize(job.type)}</TableCell>
                  <TableCell className="text-muted-foreground">{humanize(job.trigger)}</TableCell>
                  <TableCell>
                    <StatusBadge status={job.status} />
                  </TableCell>
                </TableRow>
              ))}
              {jobs?.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground pl-6">
                    Nothing yet. Publish the car to push it to your channels.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
