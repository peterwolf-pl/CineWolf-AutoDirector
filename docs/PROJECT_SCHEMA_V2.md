# Project schema v2

`CineWolfProjectV2` binds a cinematic project to a stable `ReplayIdentity`.

## Identity

Stable id is derived from metadata id + file name + duration fingerprints. Absolute private paths are not stored by default.

## Contents

- analysis settings/summary
- montage preset summary and planned shots
- events (compact)
- shot edits and locks
- required integrations
- timeline/UI state
- warnings and planning reasons

Raw replay samples are not stored.
