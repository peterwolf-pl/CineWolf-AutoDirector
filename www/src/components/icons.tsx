import type { ReactNode, SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function IconFrame({
  size = 20,
  children,
  ...props
}: IconProps & { children: ReactNode }) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      viewBox="0 0 24 24"
      width={size}
      {...props}
    >
      {children}
    </svg>
  );
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </IconFrame>
  );
}

export function DownloadIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M12 3v12" />
      <path d="m7 10 5 5 5-5" />
      <path d="M5 21h14" />
    </IconFrame>
  );
}

export function MenuIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M4 6h16M4 12h16M4 18h16" />
    </IconFrame>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m6 6 12 12M18 6 6 18" />
    </IconFrame>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </IconFrame>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m5 12 4.2 4.2L19 6.5" />
    </IconFrame>
  );
}

export function CameraIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M4 7h11l3-3 2 2-3 3v8H4z" />
      <circle cx="10" cy="13" r="3" />
    </IconFrame>
  );
}

export function PlayIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m9 5 10 7-10 7z" fill="currentColor" stroke="none" />
    </IconFrame>
  );
}

export function ChevronDownIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m6 9 6 6 6-6" />
    </IconFrame>
  );
}

export function ExternalIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M14 4h6v6" />
      <path d="m20 4-9 9" />
      <path d="M19 14v5H5V5h5" />
    </IconFrame>
  );
}
