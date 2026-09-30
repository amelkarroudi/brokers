"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import useSWR from "swr";
import { AlertTriangle } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { channelName, formatDateTime, formatMoney } from "@/lib/format";
import type { Page, Reservation } from "@/lib/types";

export default function ReservationsPage() {
  return (
    <Suspense>
      <Reservations />
    </Suspense>
  );
}

function Reservations() {
  const searchParams = useSearchParams();
  const [status, setStatus] = useState("ALL");
  const [conflictOnly, setConflictOnly] = useState(searchParams.get("conflictOnly") === "true");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams({ page: String(page), size: "20", conflictOnly: String(conflictOnly) });
  if (status !== "ALL") {
    params.set("status", status);
  }
  const { data } = useSWR<Page<Reservation>>(`/api/reservations?${params}`, { refreshInterval: 5000 });

  return (
    <>
      <PageHeader
        title="Reservations"
        description="Bookings made by customers on your channels. Each one blocks the car on every other channel."
      />

      <div className="flex flex-wrap items-center gap-4">
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
            <SelectItem value="ALL">All reservations</SelectItem>
            <SelectItem value="CONFIRMED">Confirmed</SelectItem>
            <SelectItem value="CANCELLED">Cancelled</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex items-center gap-2">
          <Checkbox
            id="conflictOnly"
            checked={conflictOnly}
            onCheckedChange={(checked) => {
              setConflictOnly(checked === true);
              setPage(0);
            }}
          />
          <Label htmlFor="conflictOnly">Only overbookings</Label>
        </div>
      </div>

      {data && data.totalItems === 0 ? (
        <EmptyState
          title="No reservations"
          description="Reservations appear here as soon as a channel sends them. Try the “Test reservation” tab on a published car."
        />
      ) : (
        <Card className="py-0">
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Reservation</TableHead>
                  <TableHead>Channel</TableHead>
                  <TableHead>Pick-up</TableHead>
                  <TableHead>Return</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data?.items ?? []).map((reservation) => (
                  <TableRow key={reservation.id}>
                    <TableCell className="pl-4">
                      <Link href={`/vehicles/${reservation.vehicleId}`} className="font-mono text-xs underline-offset-4 hover:underline">
                        {reservation.externalReservationId}
                      </Link>
                    </TableCell>
                    <TableCell>{channelName(reservation.channel)}</TableCell>
                    <TableCell>{formatDateTime(reservation.pickupAt)}</TableCell>
                    <TableCell>{formatDateTime(reservation.returnAt)}</TableCell>
                    <TableCell>
                      <div>{reservation.customerName ?? "—"}</div>
                      <div className="text-muted-foreground text-xs">{reservation.customerEmail}</div>
                    </TableCell>
                    <TableCell>{formatMoney(reservation.totalAmount, reservation.currency)}</TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <StatusBadge status={reservation.status} />
                        {reservation.hasConflict && (
                          <Badge variant="destructive">
                            <AlertTriangle /> Overbooked
                          </Badge>
                        )}
                      </div>
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
