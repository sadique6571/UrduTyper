// Poster backup for UrduTyper on Cloudflare R2. Zero dependencies, so it
// deploys as-is in the Cloudflare dashboard editor as well as via wrangler.
// Firebase Authentication stays for login; the app sends a Firebase ID token
// and this Worker verifies it (RS256 against Google's securetoken JWKS),
// then scopes every object to that user's UID:
//   users/{uid}/posters/{filename}
// Uploads, lists and deletes go through this Worker (authenticated).
// Downloads use the bucket's public URL (returned by the list endpoint),
// so the app's image loader needs no auth headers.

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

export default {
  async fetch(request, env) {
    try {
      if (!env.FIREBASE_PROJECT_ID || !env.POSTERS) {
        return json({ error: "Server not configured" }, 503);
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

      const url = new URL(request.url);
      const prefix = `users/${uid}/posters/`;

      // GET /v1/posters -> authenticated list of this user's posters.
      if (url.pathname === "/v1/posters" && request.method === "GET") {
        if (!env.PUBLIC_BASE_URL || env.PUBLIC_BASE_URL.includes("REPLACE_WITH")) {
          return json({ error: "Server not configured" }, 503);
        }
        const listed = await env.POSTERS.list({ prefix, limit: 1000 });
        const base = env.PUBLIC_BASE_URL.replace(/\/$/, "");
        const posters = (listed.objects || []).map((o) => ({
          name: o.key.slice(prefix.length),
          url: `${base}/${o.key}`,
          size: o.size,
          uploaded: o.uploaded ? o.uploaded.toISOString() : null,
        }));
        return json({ posters });
      }

      // GET /v1/storage-usage -> this user's bytes + whole-bucket bytes.
      // Sums only; no object keys are returned, so one user never learns
      // another user's filenames.
      if (url.pathname === "/v1/storage-usage" && request.method === "GET") {
        let totalBytes = 0;
        let usedBytes = 0;
        let cursor = undefined;
        for (let page = 0; page < 10; page++) {
          const listed = await env.POSTERS.list({ prefix: undefined, limit: 1000, cursor });
          for (const o of listed.objects || []) {
            totalBytes += o.size || 0;
            if (o.key.startsWith(prefix)) usedBytes += o.size || 0;
          }
          if (!listed.truncated) break;
          cursor = listed.cursor;
        }
        return json({ usedBytes, totalBytes, limitBytes: 10_000_000_000 });
      }

      const match = /^\/v1\/posters\/([A-Za-z0-9_.\-]+)$/.exec(url.pathname);
      if (!match) return json({ error: "Not found" }, 404);
      const filename = match[1];
      if (!validName(filename)) return json({ error: "Invalid filename" }, 400);
      const key = prefix + filename;

      if (request.method === "GET") {
        const obj = await env.POSTERS.get(key);
        if (obj === null) return json({ error: "Not found" }, 404);
        return new Response(obj.body, {
          headers: {
            "content-type": obj.httpMetadata?.contentType || "application/octet-stream",
            "cache-control": "private, no-store",
          },
        });
      }

      if (request.method === "DELETE") {
        await env.POSTERS.delete(key);
        return json({ deleted: true, name: filename });
      }

      if (request.method === "PUT") {
        const contentType = (request.headers.get("content-type") || "").split(";")[0];
        if (!ALLOWED_TYPES.has(contentType)) {
          return json({ error: "Unsupported content type" }, 415);
        }
        const length = Number(request.headers.get("content-length"));
        if (!Number.isFinite(length) || length <= 0 || length > MAX_BYTES) {
          return json({ error: "File must be 1 byte to 10 MB" }, 413);
        }
        const body = await request.arrayBuffer();
        if (body.byteLength !== length || body.byteLength > MAX_BYTES) {
          return json({ error: "Invalid file size" }, 413);
        }
        await env.POSTERS.put(key, body, { httpMetadata: { contentType } });
        return json({ saved: true, name: filename });
      }

      return json({ error: "Not found" }, 404);
    } catch (e) {
      return json({ error: "Server error" }, 500);
    }
  },
};
