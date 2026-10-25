// Poster backup for UrduTyper on Cloudflare Workers KV (no-card path).
// Zero dependencies, so it deploys as-is in the Cloudflare dashboard editor
// as well as via wrangler. Firebase Authentication stays for login; the app
// sends a Firebase ID token and this Worker verifies it (RS256 against
// Google's securetoken JWKS), then scopes every poster to that user's UID:
//   users/{uid}/posters/{filename}
// List, upload, delete and storage-usage are authenticated. Single-file
// downloads are public by URL (posters are shareable art, same rationale as
// a public bucket URL), so the app's image loader needs no auth headers.
// Free-tier limits that bound this design: 1 GB KV storage, 1,000 writes/day,
// 100,000 reads/day, 25 MB per value (posters are capped at 10 MB here).

const JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });

const NAME_PATTERN = /^[A-Za-z0-9_.-]{1,120}$/;
const ALLOWED_EXT = new Set([".jpg", ".jpeg", ".png", ".webp"]);
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const MAX_BYTES = 10_000_000;
const LIMIT_BYTES = 1_000_000_000; // KV free tier: 1 GB

function b64urlDecode(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

let jwksCache = null;
let jwksAt = 0;

async function getJwks() {
  const now = Date.now();
  if (jwksCache && now - jwksAt < 3600_000) return jwksCache;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new Error("jwks");
  jwksCache = await res.json();
  jwksAt = now;
  return jwksCache;
}

// Returns the Firebase UID when the ID token is valid, else null.
async function verifyIdToken(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  let header;
  let payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[1])));
  } catch {
    return null;
  }
  if (header.alg !== "RS256" || !header.kid) return null;
  const jwks = await getJwks();
  const jwk = (jwks.keys || []).find((k) => k.kid === header.kid);
  if (!jwk) return null;
  let key;
  try {
    key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
  } catch {
    return null;
  }
  const data = new TextEncoder().encode(parts[0] + "." + parts[1]);
  let ok = false;
  try {
    ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlDecode(parts[2]), data);
  } catch {
    return false;
  }
  if (!ok) return null;
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (payload.aud !== projectId) return null;
  if (typeof payload.exp !== "number" || payload.exp <= nowSec) return null;
  if (typeof payload.iat !== "number") return null;
  if (typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 128) return null;
  return payload.sub;
}

function validName(name) {
  if (!NAME_PATTERN.test(name)) return false;
  const dot = name.lastIndexOf(".");
  if (dot < 0) return false;
  return ALLOWED_EXT.has(name.slice(dot).toLowerCase());
}

// Paginated KV list; KV list() returns { keys: [{ name, metadata }], ... }.
async function listAll(kv, prefix) {
  const keys = [];
  let cursor = undefined;
  for (let page = 0; page < 10; page++) {
    const res = await kv.list({ prefix, limit: 1000, cursor });
    keys.push(...(res.keys || []));
    if (res.list_complete) break;
    cursor = res.cursor;
  }
  return keys;
}

export default {
  async fetch(request, env) {
    try {
      if (!env.FIREBASE_PROJECT_ID || !env.POSTERS_KV) {
        return json({ error: "Server not configured" }, 503);
      }
      const url = new URL(request.url);
      const origin = url.origin;

      // Public download: GET /v1/posters/{filename}?uid={uid} needs no token,
      // so the app's image loader (plain URLs) works unchanged. The uid in
      // the URL is just the key scope, and posters are shareable art, so
      // knowing the URL grants that one file — the same exposure as a
      // public bucket URL. The list endpoint (authenticated) is the only
      // place these URLs are handed out.
      const downloadMatch = /^\/v1\/posters\/([A-Za-z0-9_.\-]+)$/.exec(url.pathname);
      if (downloadMatch && request.method === "GET" && url.searchParams.has("uid")) {
        const uidParam = url.searchParams.get("uid") || "";
        const name = downloadMatch[1];
        if (!/^[A-Za-z0-9_-]{1,128}$/.test(uidParam) || !validName(name)) {
          return json({ error: "Not found" }, 404);
        }
        const stored = await env.POSTERS_KV.getWithMetadata(
          `users/${uidParam}/posters/${name}`,
          "arrayBuffer"
        );
        if (stored.value === null) return json({ error: "Not found" }, 404);
        return new Response(stored.value, {
          headers: {
            "content-type": (stored.metadata && stored.metadata.contentType) || "image/jpeg",
            "cache-control": "public, max-age=86400",
          },
        });
      }

      const authorization = request.headers.get("authorization") || "";
      if (!authorization.startsWith("Bearer ")) {
        return json({ error: "Missing Firebase ID token" }, 401);
      }
      let uid = null;
      try {
        uid = await verifyIdToken(authorization.slice(7), env.FIREBASE_PROJECT_ID);
      } catch {
        uid = null;
      }
      if (!uid) return json({ error: "Invalid token" }, 401);

      const prefix = `users/${uid}/posters/`;

      // GET /v1/posters -> authenticated list of this user's posters.
      if (url.pathname === "/v1/posters" && request.method === "GET") {
        const keys = await listAll(env.POSTERS_KV, prefix);
        const posters = keys.map((k) => ({
          name: k.name.slice(prefix.length),
          url: `${origin}/v1/posters/${encodeURIComponent(k.name.slice(prefix.length))}?uid=${encodeURIComponent(uid)}`,
          size: (k.metadata && k.metadata.size) || 0,
          uploaded: (k.metadata && k.metadata.uploaded) || null,
        }));
        return json({ posters });
      }

      // GET /v1/storage-usage -> this user's bytes + whole-namespace bytes.
      // Sums only; no key names are returned, so one user never learns
      // another user's filenames.
      if (url.pathname === "/v1/storage-usage" && request.method === "GET") {
        const keys = await listAll(env.POSTERS_KV, undefined);
        let totalBytes = 0;
        let usedBytes = 0;
        for (const k of keys) {
          const size = (k.metadata && k.metadata.size) || 0;
          totalBytes += size;
          if (k.name.startsWith(prefix)) usedBytes += size;
        }
        return json({ usedBytes, totalBytes, limitBytes: LIMIT_BYTES });
      }

      const match = /^\/v1\/posters\/([A-Za-z0-9_.\-]+)$/.exec(url.pathname);
      if (match) {
        const name = match[1];
        if (!validName(name)) return json({ error: "Invalid filename" }, 400);
        const key = prefix + name;

        if (request.method === "PUT") {
          const contentType = request.headers.get("content-type") || "";
          if (!ALLOWED_TYPES.has(contentType)) {
            return json({ error: "Only JPEG, PNG or WebP posters are accepted" }, 415);
          }
          const declared = request.headers.get("content-length");
          if (declared !== null && Number(declared) > MAX_BYTES) {
            return json({ error: "Poster is larger than 10 MB" }, 413);
          }
          const bytes = await request.arrayBuffer();
          if (bytes.byteLength > MAX_BYTES) {
            return json({ error: "Poster is larger than 10 MB" }, 413);
          }
          await env.POSTERS_KV.put(key, bytes, {
            metadata: {
              size: bytes.byteLength,
              contentType,
              uploaded: new Date().toISOString(),
            },
          });
          return json({ ok: true, name, size: bytes.byteLength });
        }

        if (request.method === "DELETE") {
          await env.POSTERS_KV.delete(key);
          return json({ ok: true, name });
        }

        return json({ error: "Method not allowed" }, 405);
      }

      return json({ error: "Not found" }, 404);
    } catch (e) {
      return json({ error: "Worker error: " + (e && e.message ? e.message : "unknown") }, 500);
    }
  },
};
