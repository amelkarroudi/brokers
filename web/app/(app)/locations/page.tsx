"use client";

import { useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { Field } from "@/components/field";
import { PageHeader } from "@/components/page-header";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, errorMessage } from "@/lib/api";
import type { Location } from "@/lib/types";

type Editing = { mode: "create" } | { mode: "edit"; location: Location } | null;

export default function LocationsPage() {
  const { data: locations, mutate } = useSWR<Location[]>("/api/locations");
  const [editing, setEditing] = useState<Editing>(null);

  async function remove(location: Location) {
    if (!confirm(`Delete ${location.name}?`)) {
      return;
    }
    try {
      await api(`/api/locations/${location.id}`, "DELETE");
      toast.success("Location deleted");
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <>
      <PageHeader
        title="Locations"
        description="Branches where customers pick up and return cars. Channels show this address on every listing."
        actions={
          <Button onClick={() => setEditing({ mode: "create" })}>
            <Plus /> Add location
          </Button>
        }
      />

      {locations && locations.length === 0 ? (
        <EmptyState title="No locations yet" description="Add your first branch before adding cars." />
      ) : (
        <Card className="py-0">
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>Airport</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {(locations ?? []).map((location) => (
                  <TableRow key={location.id}>
                    <TableCell className="pl-4 font-mono text-xs">{location.code}</TableCell>
                    <TableCell>{location.name}</TableCell>
                    <TableCell>
                      {location.city}, {location.countryCode}
                    </TableCell>
                    <TableCell>{location.iataCode ?? "—"}</TableCell>
                    <TableCell className="text-right">
                      <Button size="icon" variant="ghost" onClick={() => setEditing({ mode: "edit", location })}>
                        <Pencil />
                      </Button>
                      <Button size="icon" variant="ghost" onClick={() => remove(location)}>
                        <Trash2 />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <LocationDialog
        editing={editing}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await mutate();
        }}
      />
    </>
  );
}

function LocationDialog({ editing, onClose, onSaved }: { editing: Editing; onClose: () => void; onSaved: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const location = editing?.mode === "edit" ? editing.location : undefined;

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const optional = (name: string) => String(form.get(name) ?? "").trim() || undefined;
    const number = (name: string) => (optional(name) ? Number(optional(name)) : undefined);
    const body = {
      code: form.get("code"),
      name: form.get("name"),
      addressLine: form.get("addressLine"),
      city: form.get("city"),
      postalCode: optional("postalCode"),
      countryCode: form.get("countryCode"),
      iataCode: optional("iataCode"),
      latitude: number("latitude"),
      longitude: number("longitude"),
    };
    setError(null);
    try {
      if (location) {
        await api(`/api/locations/${location.id}`, "PUT", body);
      } else {
        await api("/api/locations", "POST", body);
      }
      toast.success("Location saved");
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <Dialog open={editing !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{location ? "Edit location" : "Add location"}</DialogTitle>
        </DialogHeader>
        <form key={location?.id ?? "new"} onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2">
          {error && (
            <Alert variant="destructive" className="sm:col-span-2">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <Field label="Code" htmlFor="code">
            <Input id="code" name="code" defaultValue={location?.code} placeholder="CMN-T1" required />
          </Field>
          <Field label="Name" htmlFor="name">
            <Input id="name" name="name" defaultValue={location?.name} placeholder="Casablanca Airport T1" required />
          </Field>
          <Field label="Address" htmlFor="addressLine" className="grid gap-2 sm:col-span-2">
            <Input id="addressLine" name="addressLine" defaultValue={location?.addressLine} required />
          </Field>
          <Field label="City" htmlFor="city">
            <Input id="city" name="city" defaultValue={location?.city} required />
          </Field>
          <Field label="Postal code" htmlFor="postalCode">
            <Input id="postalCode" name="postalCode" defaultValue={location?.postalCode} />
          </Field>
          <Field label="Country (ISO)" htmlFor="countryCode">
            <Input id="countryCode" name="countryCode" defaultValue={location?.countryCode} placeholder="MA" maxLength={2} required />
          </Field>
          <Field label="Airport code (IATA)" htmlFor="iataCode">
            <Input id="iataCode" name="iataCode" defaultValue={location?.iataCode} placeholder="CMN" maxLength={3} />
          </Field>
          <Field label="Latitude" htmlFor="latitude">
            <Input id="latitude" name="latitude" type="number" step="0.000001" defaultValue={location?.latitude} />
          </Field>
          <Field label="Longitude" htmlFor="longitude">
            <Input id="longitude" name="longitude" type="number" step="0.000001" defaultValue={location?.longitude} />
          </Field>
          <DialogFooter className="sm:col-span-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit">Save</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
