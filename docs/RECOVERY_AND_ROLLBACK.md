# Recovery and rollback

## Timeline writes

1. Validate paths/time mappings/conflicts
2. Capture transaction snapshot
3. Write through one Flashback history entry when possible
4. On failure, mark diagnostic rollback state and keep native undo/CineWolf undo aligned

## Projects

- Debounced autosave: current / previous / recovery copies
- Migration backups: `.bak-<timestamp>`
- Quarantine for malformed project files
