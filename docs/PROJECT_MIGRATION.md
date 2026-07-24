# Project migration

`ProjectMigrationManager` migrates schema 0/1 montage projects to schema 2.

## Steps

1. Validate source JSON
2. Copy `.bak-<timestamp>` backup
3. Detect schema version
4. Convert legacy `MontageProject` fields
5. Attach `ReplayIdentity`
6. Validate output
7. Write `*.v2.json` and report

Original files are never destroyed.
