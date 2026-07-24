# Third-party integrations

CineWolf loads third-party providers through the public API v2 manager.

## Lifecycle

1. Integration declares id, display name, version, required API version.
2. Manager validates id format and API compatibility.
3. Registration runs inside a safe context.
4. Diagnostics record counts, warnings, and errors.
5. A failing integration is marked failed; CineWolf continues.

## Built-in soft vehicle integrations

| Provider ID | Purpose |
| --- | --- |
| `peterwolf.minecart_chain` | Train/locomotive anchors and framing |
| `peterwolf.planes` | Aircraft anchors and flight framing |
| `peterwolf.zipline` | Zip-line rider/cable framing |
| `builtin` | Vanilla minecart/boat/mount/etc. |
| `soft-mod` | Heuristic namespace detection |
| `generic` | Always-available fallback |

No hard runtime dependency on PeterWolf vehicle mods is required.
