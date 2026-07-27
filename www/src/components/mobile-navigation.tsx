"use client";

import Link from "next/link";
import { useEffect, useId, useState } from "react";

import { CloseIcon, MenuIcon } from "@/components/icons";
import { navigation } from "@/lib/site";

export function MobileNavigation() {
  const [open, setOpen] = useState(false);
  const menuId = useId();

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <div className="mobile-navigation">
      <button
        aria-controls={menuId}
        aria-expanded={open}
        aria-label={open ? "Close navigation menu" : "Open navigation menu"}
        className="mobile-menu-button"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        {open ? <CloseIcon size={23} /> : <MenuIcon size={23} />}
      </button>
      <div
        className={`mobile-menu-panel ${open ? "mobile-menu-panel-open" : ""}`}
        id={menuId}
      >
        <nav aria-label="Mobile navigation">
          {navigation.map((item) => (
            <Link href={item.href} key={item.href} onClick={() => setOpen(false)}>
              {item.label}
            </Link>
          ))}
          <Link className="button button-primary" href="/download" onClick={() => setOpen(false)}>
            Download CineWolf
          </Link>
        </nav>
      </div>
    </div>
  );
}
