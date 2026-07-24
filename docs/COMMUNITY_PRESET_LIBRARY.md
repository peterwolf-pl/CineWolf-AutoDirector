# Community preset library

Local-only library under `config/cinewolf-autodirector/library/`.

## Non-goals

No accounts, cloud storage, online catalogs, or network access.

## Features

- Index built-in + imported bundles
- Search/filter by text, category, tags, aspect ratio, duration, favourites
- Local favourites and ratings
- Bundle import/export with schema validation and checksum
- Built-in presets remain read-only

## Security

Bundles reject:

- path traversal
- scripts / dangerous content
- excessive size or nesting
- filesystem paths in string parameters
- unbounded numeric values
- checksum mismatches when a checksum is present
