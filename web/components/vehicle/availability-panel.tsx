"use client";

import { useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Trash2 } from "lucide-react";

import { Field } from "@/components/field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, errorMessage } from "@/lib/api";
import { formatDateTime, fromLocalInput, humanize } from "@/lib/format";
import type { AvailabilityBlock, Vehicle } from "@/lib/types";

/** Periods when the car cannot be rented. Reservation blocks come from the channels. */
export function AvailabilityPanel({ vehicle }: { vehicle: Vehicle }) {
  const path = `/api/vehicles/${vehicle.id}/availability`;
  const { data: blocks, mutate } = useSWR<AvailabilityBlock[]>(path, { refreshInterval: 5000 });
  const [reason, setReason] = useState("MANUAL");

  async function onCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      await api(path, "POST", {
        startsAt: fromLocalInput(String(data.get("startsAt"))),
        endsAt: fromLocalInput(String(data.get("endsAt"))),
        reason,
        note: data.get("note") || undefined,
      });
      toast.success("Blocked on every channel");
      form.reset();
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  async function onDelete(block: AvailabilityBlock) {
    try {
      await api(`${path}/${block.id}`, "DELETE");
      toast.success("Period released");
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Block a period</CardTitle>
          <CardDescription>For maintenance or private use. Every channel stops selling the car during this time.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onCreate} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_10rem_1fr_auto] lg:items-end">
            <Field label="From" htmlFor="startsAt">
              <Input id="startsAt" name="startsAt" type="datetime-local" required />
            </Field>
            <Field label="Until" htmlFor="endsAt">
              <Input id="endsAt" name="endsAt" type="datetime-local" required />
            </Field>
            <Field label="Reason">
              <Select value={reason} onValueChange={setReason}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MANUAL">Manual</SelectItem>
                  <SelectItem value="MAINTENANCE">Maintenance</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="Note" htmlFor="note">
              <Input id="note" name="note" maxLength={500} />
            </Field>
            <Button type="submit" disabled={vehicle.status === "ARCHIVED"}>
              Block
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="pb-0">
        <CardHeader>
          <CardTitle className="text-base">Upcoming blocked periods</CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">From</TableHead>
                <TableHead>Until</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Note</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(blocks ?? []).map((block) => (
                <TableRow key={block.id}>
                  <TableCell className="pl-6">{formatDateTime(block.startsAt)}</TableCell>
                  <TableCell>{formatDateTime(block.endsAt)}</TableCell>
                  <TableCell>
                    <Badge variant={block.reason === "RESERVATION" ? "default" : "secondary"}>{humanize(block.reason)}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{block.note ?? "—"}</TableCell>
                  <TableCell className="text-right">
                    {block.reason !== "RESERVATION" && (
                      <Button size="icon" variant="ghost" onClick={() => onDelete(block)}>
                        <Trash2 />
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {blocks?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground pl-6">
                    The car is available for the whole period.
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
