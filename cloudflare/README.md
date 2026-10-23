# Cloudflare R2 backup API (foundation)

Firebase Authentication stays in the Android client. Android obtains a fresh Firebase **ID token** and sends `Authorization: Bearer <token>` to the Worker. The Worker verifies Firebase's RS256 signature, issuer and project audience using Google's public keys, then accesses the private R2 binding under `users/<uid>/projects/<project-id>/...`.

## Setup
1. Create a private R2 bucket, configure `wrangler.toml` with its bucket name.
2. In Cloudflare Workers set `FIREBASE_PROJECT_ID` to the actual Firebase project ID.
3. Run `npm install` and `npm run deploy` from `cloudflare/worker`.
4. Implement Android Worker HTTP client, offline work queue, token refresh, conflict resolution, and a per-user project index. These are **not implemented yet**.

## API prototype
- PUT/GET/DELETE `/v1/projects/{id}/document` for editable JSON (`schemaVersion:1`, `layers:[]`).
- PUT/GET/DELETE `/v1/projects/{id}/preview` for PNG/JPEG/WebP binary preview.
- Max request size: 10 MB; Content-Length required.

Security note: R2 credentials stay in Cloudflare, never in the APK. Set R2 bucket private. This is a prototype, not production-ready; add quotas, rate limits, strict schema validation, per-user indexing, audit logs and integration tests before shipping.
