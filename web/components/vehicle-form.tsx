"use client";

import { useState } from "react";
import useSWR from "swr";

import { Field } from "@/components/field";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { errorMessage } from "@/lib/api";
import type { FuelType, Location, Transmission, Vehicle } from "@/lib/types";

const FUEL_TYPES: FuelType[] = ["PETROL", "DIESEL", "HYBRID", "ELECTRIC", "LPG"];

/** Create and edit form for a car. Sends the full representation the API expects. */
export function VehicleForm({
  vehicle,
  submitLabel,
  onSubmit,
}: {
  vehicle?: Vehicle;
  submitLabel: string;
  onSubmit: (body: Record<string, unknown>) => Promise<void>;
}) {
  const { data: locations } = useSWR<Location[]>("/api/locations");
  const [locationId, setLocationId] = useState(vehicle?.location.id ?? "");
  const [transmission, setTransmission] = useState<Transmission>(vehicle?.transmission ?? "MANUAL");
  const [fuelType, setFuelType] = useState<FuelType>(vehicle?.fuelType ?? "PETROL");
  const [airConditioning, setAirConditioning] = useState(vehicle?.airConditioning ?? true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    const optionalNumber = (name: string) => (text(name) ? Number(text(name)) : undefined);
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        locationId,
        reference: text("reference"),
        make: text("make"),
        model: text("model"),
        year: Number(text("year")),
        acrissCode: text("acrissCode"),
        transmission,
        fuelType,
        seats: Number(text("seats")),
        doors: Number(text("doors")),
        bags: Number(text("bags")),
        airConditioning,
        mileageLimitKm: optionalNumber("mileageLimitKm"),
        minDriverAge: Number(text("minDriverAge")),
        dailyRate: text("dailyRate"),
        deposit: text("deposit") || undefined,
      });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {error && (
        <Alert variant="destructive" className="sm:col-span-2 lg:col-span-3">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Field label="Location">
        <Select value={locationId} onValueChange={setLocationId} required>
          <SelectTrigger>
            <SelectValue placeholder={locations?.length === 0 ? "Add a location first" : "Choose a branch"} />
          </SelectTrigger>
          <SelectContent>
            {(locations ?? []).map((location) => (
              <SelectItem key={location.id} value={location.id}>
                {location.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
      <Field label="Fleet reference" htmlFor="reference" hint="Your own identifier, e.g. plate or fleet number.">
        <Input id="reference" name="reference" defaultValue={vehicle?.reference} required />
      </Field>
      <Field label="ACRISS code" htmlFor="acrissCode" hint="Industry car class, e.g. CDMR.">
        <Input id="acrissCode" name="acrissCode" defaultValue={vehicle?.acrissCode} maxLength={4} required />
      </Field>
      <Field label="Make" htmlFor="make">
        <Input id="make" name="make" defaultValue={vehicle?.make} required />
      </Field>
      <Field label="Model" htmlFor="model">
        <Input id="model" name="model" defaultValue={vehicle?.model} required />
      </Field>
      <Field label="Year" htmlFor="year">
        <Input id="year" name="year" type="number" min={1990} max={2100} defaultValue={vehicle?.year ?? new Date().getFullYear()} required />
      </Field>
      <Field label="Transmission">
        <Select value={transmission} onValueChange={(value) => setTransmission(value as Transmission)}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="MANUAL">Manual</SelectItem>
            <SelectItem value="AUTOMATIC">Automatic</SelectItem>
          </SelectContent>
        </Select>
      </Field>
      <Field label="Fuel">
        <Select value={fuelType} onValueChange={(value) => setFuelType(value as FuelType)}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {FUEL_TYPES.map((fuel) => (
              <SelectItem key={fuel} value={fuel}>
                {fuel.charAt(0) + fuel.slice(1).toLowerCase()}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
      <div className="flex items-end gap-2 pb-2">
        <Checkbox id="airConditioning" checked={airConditioning} onCheckedChange={(checked) => setAirConditioning(checked === true)} />
        <Label htmlFor="airConditioning">Air conditioning</Label>
      </div>
      <Field label="Seats" htmlFor="seats">
        <Input id="seats" name="seats" type="number" min={1} max={60} defaultValue={vehicle?.seats ?? 5} required />
      </Field>
      <Field label="Doors" htmlFor="doors">
        <Input id="doors" name="doors" type="number" min={2} max={6} defaultValue={vehicle?.doors ?? 5} required />
      </Field>
      <Field label="Large bags" htmlFor="bags">
        <Input id="bags" name="bags" type="number" min={0} max={20} defaultValue={vehicle?.bags ?? 2} required />
      </Field>
      <Field label={`Daily rate${vehicle ? ` (${vehicle.currency})` : ""}`} htmlFor="dailyRate">
        <Input id="dailyRate" name="dailyRate" type="number" step="0.01" min="0.01" defaultValue={vehicle?.dailyRate} required />
      </Field>
      <Field label="Deposit" htmlFor="deposit">
        <Input id="deposit" name="deposit" type="number" step="0.01" min="0" defaultValue={vehicle?.deposit} />
      </Field>
      <Field label="Minimum driver age" htmlFor="minDriverAge">
        <Input id="minDriverAge" name="minDriverAge" type="number" min={18} max={99} defaultValue={vehicle?.minDriverAge ?? 21} required />
      </Field>
      <Field label="Mileage limit per day (km)" htmlFor="mileageLimitKm" hint="Leave empty for unlimited mileage.">
        <Input id="mileageLimitKm" name="mileageLimitKm" type="number" min={1} defaultValue={vehicle?.mileageLimitKm} />
      </Field>
      <div className="flex items-end justify-end sm:col-span-2 lg:col-span-3">
        <Button type="submit" disabled={submitting || !locationId}>
          {submitting ? "Saving…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
