# Flashback timeline visualization

Flashback 0.41.1 does not expose a stable public event-track extension API.

CineWolf therefore keeps:

- native Flashback markers untouched
- a CineWolf-owned mini-timeline / overlay for events and montage shots
- `FlashbackTimelineExtensionAdapter` as the extension investigation boundary

Preferred order remains: public API → public hooks → overlay → narrow render mixin.
