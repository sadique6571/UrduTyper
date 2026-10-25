# UrduTyper backup: Cloudflare Worker + Workers KV

Firebase Authentication continues to handle email/Google login. The Android CloudflareBackupClient sends a Firebase ID token. The Worker verifies signature, issuer and audience and stores each user's content under their Firebase UID.

## Cloudflare setup
1. Dashboard → Storage & databases → Workers KV → Create namespace. Name: `urdu-typer-projects`.
2. Copy its namespace ID to `cloudflare/worker/wrangler.toml` under `[[kv_namespaces]]` as `id`. Binding must remain `PROJECTS`.
3. From `cloudflare/worker`: `npm install`, then `npx wrangler login`, then `npm run deploy`. Alternatively connect GitHub to Workers Builds with root directory `cloudflare/worker`.
4. `FIREBASE_PROJECT_ID` is `urdutyper2`; change it if migrating Firebase projects.
5. Configure the HTTPS Worker URL inside Android app when integrating sync.

## Prototype API
Authenticated `GET/PUT/DELETE /v1/projects/{projectId}/document` for schemaVersion 1 editable JSON, plus `.../preview` for JPEG/PNG/WebP preview.
Every key is scoped to `users/{firebase_uid}/projects/{projectId}/...`. No R2 bucket or credentials are needed.

## Important limitations
**Workers KV is eventually consistent**: recently written data may be temporarily stale across locations. It limits writes to the same key to one per second; frequent editor autosave should write to local storage first, then debounce uploads.
Per-value limit is 25 MiB, but this prototype caps a request at **5 MB**; it is intended for JSON projects and compressed previews, **not original full-size image archives**.
Free tier (at Oct 2026): 1 GB storage, 1,000 writes/day, 100,000 reads/day; paid usage may incur costs. Consider R2 later for large images.
Production readiness still requires authenticated user listing, retry/backoff, offline drafts, KV conflict/version handling, strict content validation, quotas and abuse prevention. Real deployment and end-to-end Firebase sign-in testing have not happened.
