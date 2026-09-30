"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  CalendarCheck,
  Car,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  MapPin,
  PlugZap,
  RefreshCw,
  Webhook,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

const NAVIGATION = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/connections", label: "Channels", icon: PlugZap },
  { href: "/locations", label: "Locations", icon: MapPin },
  { href: "/vehicles", label: "Fleet", icon: Car },
  { href: "/reservations", label: "Reservations", icon: CalendarCheck },
  { href: "/sync", label: "Sync log", icon: RefreshCw },
  { href: "/webhooks", label: "Webhooks", icon: Webhook },
  { href: "/sandbox", label: "Sandbox", icon: FlaskConical },
];

export function AppSidebar() {
  const pathname = usePathname();
  const { me, logout } = useAuth();

  return (
    <aside className="bg-sidebar flex w-full flex-col border-b md:h-screen md:w-60 md:border-r md:border-b-0">
      <div className="px-5 py-4">
        <p className="text-lg font-semibold">Brokers</p>
        <p className="text-muted-foreground truncate text-xs">{me?.organization.name}</p>
      </div>
      <nav className="flex gap-1 overflow-x-auto px-3 md:flex-1 md:flex-col">
        {NAVIGATION.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex items-center gap-2 rounded-md px-3 py-2 text-sm whitespace-nowrap transition-colors",
                active ? "bg-accent text-accent-foreground font-medium" : "text-muted-foreground hover:bg-accent/60",
              )}
            >
              <Icon className="size-4" />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="hidden border-t p-3 md:block">
        <p className="truncate px-2 text-xs font-medium">{me?.member.fullName}</p>
        <p className="text-muted-foreground truncate px-2 pb-2 text-xs">{me?.member.email}</p>
        <Button variant="ghost" size="sm" className="w-full justify-start" onClick={logout}>
          <LogOut /> Sign out
        </Button>
      </div>
    </aside>
  );
}
