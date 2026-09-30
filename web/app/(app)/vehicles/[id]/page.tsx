"use client";

import { use } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Archive, EyeOff, RefreshCw, Rocket } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { VehicleForm } from "@/components/vehicle-form";
import { AvailabilityPanel } from "@/components/vehicle/availability-panel";
import { ChannelsPanel } from "@/components/vehicle/channels-panel";
import { PhotosPanel } from "@/components/vehicle/photos-panel";
import { SimulatePanel } from "@/components/vehicle/simulate-panel";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api, errorMessage } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import type { Vehicle } from "@/lib/types";

export default function VehiclePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const path = `/api/vehicles/${id}`;
  const { data: vehicle, mutate } = useSWR<Vehicle>(path, { refreshInterval: 4000 });

  async function run(action: string, success: string, method: "POST" | "DELETE" = "POST") {
    try {
      await api(action, method);
      toast.success(success);
      await mutate();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  if (!vehicle) {
    return <Skeleton className="h-96 w-full" />;
  }

  const archived = vehicle.status === "ARCHIVED";
  return (
    <>
      <PageHeader
        title={`${vehicle.make} ${vehicle.model} ${vehicle.year}`}
        description={`${vehicle.reference} · ${vehicle.acrissCode} · ${vehicle.location.name} · ${formatMoney(vehicle.dailyRate, vehicle.currency)} per day`}
        actions={
          <>
            <StatusBadge status={vehicle.status} />
            {vehicle.status === "DRAFT" && (
              <Button onClick={() => run(`${path}/publish`, "Publishing to every connected channel")}>
                <Rocket /> Publish
              </Button>
            )}
            {vehicle.status === "ACTIVE" && (
              <>
                <Button variant="outline" onClick={() => run(`${path}/sync`, "Full resync queued")}>
                  <RefreshCw /> Resync
                </Button>
                <Button variant="outline" onClick={() => run(`${path}/unpublish`, "Taking the car off sale")}>
                  <EyeOff /> Unpublish
                </Button>
              </>
            )}
            {!archived && (
              <Button
                variant="ghost"
                onClick={() => {
                  if (!confirm("Archive this car? It will be removed from every channel.")) {
                    return;
                  }
                  run(path, "Car archived", "DELETE");
                }}
              >
                <Archive /> Archive
              </Button>
            )}
          </>
        }
      />

      <Tabs defaultValue="channels">
        <TabsList>
          <TabsTrigger value="channels">Channels</TabsTrigger>
          <TabsTrigger value="details">Details</TabsTrigger>
          <TabsTrigger value="photos">Photos ({vehicle.photos.length})</TabsTrigger>
          <TabsTrigger value="availability">Availability</TabsTrigger>
          <TabsTrigger value="simulate">Test reservation</TabsTrigger>
        </TabsList>
        <TabsContent value="channels">
          <ChannelsPanel vehicle={vehicle} />
        </TabsContent>
        <TabsContent value="details">
          <Card>
            <CardContent>
              {archived ? (
                <p className="text-muted-foreground text-sm">Archived cars cannot be edited.</p>
              ) : (
                <VehicleForm
                  key={vehicle.updatedAt}
                  vehicle={vehicle}
                  submitLabel="Save changes"
                  onSubmit={async (body) => {
                    await mutate(await api<Vehicle>(path, "PUT", body), false);
                    toast.success(vehicle.status === "ACTIVE" ? "Saved. Channels will be updated." : "Saved");
                  }}
                />
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="photos">
          <PhotosPanel vehicle={vehicle} onChange={(updated) => mutate(updated, false)} />
        </TabsContent>
        <TabsContent value="availability">
          <AvailabilityPanel vehicle={vehicle} />
        </TabsContent>
        <TabsContent value="simulate">
          <SimulatePanel vehicle={vehicle} />
        </TabsContent>
      </Tabs>
    </>
  );
}
