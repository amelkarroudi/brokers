"use client";

import { useState } from "react";
import { useSWRConfig } from "swr";
import { toast } from "sonner";

import { Field } from "@/components/field";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { api, errorMessage } from "@/lib/api";
import { channelName, fromLocalInput, toLocalInput } from "@/lib/format";
import type { Vehicle } from "@/lib/types";

interface SimulationResult {
  externalReservationId: string;
  receipt: { status: string; duplicate: boolean };
}

/**
 * Sandbox tool: sends a signed webhook exactly as the channel would, through the real webhook
 * pipeline, so you can watch the car get blocked on the other channel.
 */
export function SimulatePanel({ vehicle }: { vehicle: Vehicle }) {
  const { mutate } = useSWRConfig();
  const [channel, setChannel] = useState("BOOKING");
  const [type, setType] = useState("CREATED");
  const [result, setResult] = useState<SimulationResult | null>(null);
  const inTwoDays = toLocalInput(new Date(Date.now() + 2 * 86_400_000).toISOString());
  const inFiveDays = toLocalInput(new Date(Date.now() + 5 * 86_400_000).toISOString());

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const text = (name: string) => String(data.get(name) ?? "").trim();
    try {
      const response = await api<SimulationResult>("/api/sandbox/reservations", "POST", {
        vehicleId: vehicle.id,
        channel,
        type,
        externalReservationId: text("externalReservationId") || undefined,
        pickupAt: text("pickupAt") ? fromLocalInput(text("pickupAt")) : undefined,
        returnAt: text("returnAt") ? fromLocalInput(text("returnAt")) : undefined,
        customerName: text("customerName") || undefined,
        customerEmail: text("customerEmail") || undefined,
        totalAmount: text("totalAmount") || undefined,
      });
      setResult(response);
      toast.success(`Webhook ${response.receipt.status.toLowerCase()}`);
      await mutate((key) => typeof key === "string" && key.startsWith("/api/"));
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Simulate a customer reservation</CardTitle>
        <CardDescription>
          Sends a signed webhook as if a customer booked this car on the channel. The car is then blocked on the other
          channel automatically. Requires the sandbox to be enabled on the API.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {result && (
          <Alert>
            <AlertDescription>
              Reservation <code className="font-mono">{result.externalReservationId}</code>: webhook{" "}
              {result.receipt.status.toLowerCase()}
              {result.receipt.duplicate ? " (duplicate)" : ""}. Use this id to modify or cancel it.
            </AlertDescription>
          </Alert>
        )}
        <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Channel">
            <Select value={channel} onValueChange={setChannel}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="BOOKING">{channelName("BOOKING")}</SelectItem>
                <SelectItem value="RENTALCARS">{channelName("RENTALCARS")}</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Event">
            <Select value={type} onValueChange={setType}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="CREATED">New reservation</SelectItem>
                <SelectItem value="MODIFIED">Change dates</SelectItem>
                <SelectItem value="CANCELLED">Cancel</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field label="Reservation id" htmlFor="externalReservationId" hint="Required to change or cancel.">
            <Input id="externalReservationId" name="externalReservationId" defaultValue={result?.externalReservationId} />
          </Field>
          <Field label="Pick-up" htmlFor="pickupAt">
            <Input id="pickupAt" name="pickupAt" type="datetime-local" defaultValue={inTwoDays} />
          </Field>
          <Field label="Return" htmlFor="returnAt">
            <Input id="returnAt" name="returnAt" type="datetime-local" defaultValue={inFiveDays} />
          </Field>
          <Field label={`Total (${vehicle.currency})`} htmlFor="totalAmount">
            <Input id="totalAmount" name="totalAmount" type="number" step="0.01" min="0" defaultValue={vehicle.dailyRate * 3} />
          </Field>
          <Field label="Customer name" htmlFor="customerName">
            <Input id="customerName" name="customerName" defaultValue="Test Customer" />
          </Field>
          <Field label="Customer email" htmlFor="customerEmail">
            <Input id="customerEmail" name="customerEmail" type="email" defaultValue="customer@example.com" />
          </Field>
          <div className="flex items-end">
            <Button type="submit" className="w-full">
              Send webhook
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
