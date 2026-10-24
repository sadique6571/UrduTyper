import { createRemoteJWKSet, jwtVerify } from "jose";

const jwks = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
const pattern = /^[a-zA-Z0-9_-]{1,80}$/;
const allowedContentTypes = new Set(["application/json", "image/png", "image/jpeg", "image/webp"]);

export default {
  async fetch(request, env) {
    try {
      if (!env.FIREBASE_PROJECT_ID || !env.PROJECTS) return json({ error: "Server not configured" }, 503);
      const authorization = request.headers.get("authorization") || "";
      if (!authorization.startsWith("Bearer ")) return json({ error: "Missing Firebase ID token" }, 401);
      const { payload } = await jwtVerify(authorization.slice(7), jwks, {
        issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
        audience: env.FIREBASE_PROJECT_ID,
        algorithms: ["RS256"],
      });
      const uid = payload.sub;
      if (typeof uid !== "string" || !uid || uid.length > 128 || !payload.iat || !payload.exp) return json({ error: "Invalid token" }, 401);
      const url = new URL(request.url);
      const match = new RegExp("^/v1/projects/([A-Za-z0-9_-]{1,80})/(document|preview)$").exec(url.pathname);
      if (!match) return json({ error: "Not found" }, 404);
      const [, projectId, kind] = match;
      if (!pattern.test(projectId)) return json({ error: "Invalid project ID" }, 400);
      const key = `users/${uid}/projects/${projectId}/${kind === "document" ? "project.json" : "preview"}`;
      if (request.method === "GET") {
        const entry = await env.PROJECTS.getWithMetadata(key, "arrayBuffer");
        if (entry.value === null) return json({ error: "Not found" }, 404);
        const headers = new Headers({ "content-type": entry.metadata?.contentType || (kind === "document" ? "application/json" : "application/octet-stream"), "cache-control": "private, no-store" });
        return new Response(entry.value, { headers });
      }
      if (request.method === "DELETE") {
        await env.PROJECTS.delete(key);
        return json({ deleted: true });
      }
      if (request.method === "PUT") {
        const contentType = (request.headers.get("content-type") || "").split(";")[0];
        if (!allowedContentTypes.has(contentType) || (kind === "document" && contentType !== "application/json") || (kind === "preview" && contentType === "application/json")) return json({ error: "Unsupported content type" }, 415);
        const length = Number(request.headers.get("content-length"));
        if (!Number.isFinite(length) || length <= 0 || length > 5_000_000) return json({ error: "File must be 1 byte to 5 MB" }, 413);
        const body = await request.arrayBuffer();
        if (body.byteLength !== length || body.byteLength > 10_000_000) return json({ error: "Invalid file size" }, 413);
        if (kind === "document") {
          try { const doc = JSON.parse(new TextDecoder().decode(body)); if (doc.schemaVersion !== 1 || !Array.isArray(doc.layers)) return json({ error: "Invalid project document" }, 400); }
          catch { return json({ error: "Invalid JSON" }, 400); }
        }
        await env.PROJECTS.put(key, body, { metadata: { contentType } });
        return json({ saved: true, projectId, kind });
      }
      return json({ error: "Method not allowed" }, 405);
    } catch (error) {
      if (error?.code?.startsWith?.("ERR_JWT") || error?.code?.startsWith?.("ERR_JWS")) return json({ error: "Invalid authentication" }, 401);
      return json({ error: "Internal error" }, 500);
    }
  },
};
