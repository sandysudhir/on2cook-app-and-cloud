import { createReadStream, existsSync, statSync } from "node:fs";
import { readFile } from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = __dirname;
const ENV_PATH = path.join(ROOT_DIR, ".env.local");

function parseEnvFile(raw) {
  const env = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const separator = trimmed.indexOf("=");
    if (separator === -1) continue;
    const key = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim().replace(/^['"]|['"]$/g, "");
    env[key] = value;
  }
  return env;
}

const envText = existsSync(ENV_PATH) ? await readFile(ENV_PATH, "utf8") : "";
const ENV = {
  PORT: "5180",
  NCB_INSTANCE: "",
  NCB_AUTH_API_URL: "https://app.nocodebackend.com/api/user-auth",
  NCB_DATA_API_URL: "https://app.nocodebackend.com/api/data",
  NCB_APP_URL: "https://app.nocodebackend.com",
  NCB_SECRET_KEY: "",
  ...parseEnvFile(envText)
};

const MIME_TYPES = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".zip": "application/zip"
};

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(JSON.stringify(payload));
}

function sendText(response, statusCode, body) {
  response.writeHead(statusCode, {
    "Content-Type": "text/plain; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(body);
}

function collectRequestBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function extractAuthCookies(cookieHeader = "") {
  return cookieHeader
    .split(";")
    .map((cookie) => cookie.trim())
    .filter(
      (cookie) =>
        cookie.startsWith("better-auth.session_token=") ||
        cookie.startsWith("better-auth.session_data=")
    )
    .join("; ");
}

function transformSetCookieForLocalhost(cookie) {
  const segments = cookie.split(";").map((part) => part.trim());
  const [nameValue, ...attrs] = segments;
  const normalizedName = nameValue
    .replace(/^__Secure-/, "")
    .replace(/^__Host-/, "");
  const filtered = attrs.filter((attr) => {
    const lower = attr.toLowerCase();
    return !lower.startsWith("domain=") && lower !== "secure" && !lower.startsWith("samesite=");
  });
  filtered.push("SameSite=Lax");
  return [normalizedName, ...filtered].join("; ");
}

function getSetCookies(headers) {
  if (typeof headers.getSetCookie === "function") {
    return headers.getSetCookie();
  }
  const raw = headers.get("set-cookie");
  if (!raw) return [];
  return [raw];
}

function upstreamHeaders(request, includeCookies = true) {
  const cookieHeader = includeCookies ? extractAuthCookies(request.headers.cookie || "") : "";
  const headers = {
    "Content-Type": "application/json",
    "X-Database-Instance": ENV.NCB_INSTANCE,
    Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`,
    Origin: `http://${request.headers.host}`
  };
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  return headers;
}

async function getSessionUser(request) {
  const cookies = extractAuthCookies(request.headers.cookie || "");
  if (!cookies) return null;
  const url = `${ENV.NCB_AUTH_API_URL}/get-session?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}`;
  const response = await fetch(url, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      "X-Database-Instance": ENV.NCB_INSTANCE,
      Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`,
      Cookie: cookies
    }
  });
  if (!response.ok) return null;
  const data = await response.json();
  return data?.user || null;
}

async function proxyAuth(request, response, pathname) {
  const pathSuffix = pathname.replace(/^\/api\/auth\//, "");
  const search = new URL(request.url, `http://${request.headers.host}`).search || "";
  const upstreamUrl = `${ENV.NCB_AUTH_API_URL}/${pathSuffix}?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}${search ? `&${search.slice(1)}` : ""}`;
  const body = ["GET", "HEAD"].includes(request.method) ? undefined : await collectRequestBody(request);
  const upstream = await fetch(upstreamUrl, {
    method: request.method,
    headers: {
      "Content-Type": "application/json",
      "X-Database-Instance": ENV.NCB_INSTANCE,
      Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`,
      Cookie: extractAuthCookies(request.headers.cookie || ""),
      Origin: `http://${request.headers.host}`
    },
    body: body || undefined
  });
  const text = await upstream.text();
  const headers = {
    "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  };
  const setCookies = getSetCookies(upstream.headers).map(transformSetCookieForLocalhost);
  if (setCookies.length > 0) {
    headers["Set-Cookie"] = setCookies;
  }
  response.writeHead(upstream.status, headers);
  response.end(text);
}

async function proxyData(request, response, pathname, isPublic = false) {
  const pathSuffix = pathname.replace(isPublic ? /^\/api\/public-data\// : /^\/api\/data\//, "");
  const sessionUser = isPublic ? null : await getSessionUser(request);
  if (!isPublic && !sessionUser) {
    sendJson(response, 401, { error: "Unauthorized" });
    return;
  }

  let body = ["GET", "HEAD", "DELETE"].includes(request.method) ? "" : await collectRequestBody(request);
  if (!isPublic && request.method === "POST" && pathSuffix.startsWith("create/") && body) {
    try {
      const parsed = JSON.parse(body);
      delete parsed.user_id;
      parsed.user_id = sessionUser.id;
      body = JSON.stringify(parsed);
    } catch {}
  }
  if (!isPublic && request.method === "PUT" && body) {
    try {
      const parsed = JSON.parse(body);
      delete parsed.user_id;
      body = JSON.stringify(parsed);
    } catch {}
  }

  const search = new URL(request.url, `http://${request.headers.host}`).search || "";
  const upstreamUrl = `${ENV.NCB_DATA_API_URL}/${pathSuffix}?Instance=${encodeURIComponent(ENV.NCB_INSTANCE)}${search ? `&${search.slice(1)}` : ""}`;
  const upstream = await fetch(upstreamUrl, {
    method: request.method,
    headers: upstreamHeaders(request, !isPublic),
    body: body || undefined
  });
  const text = await upstream.text();
  response.writeHead(upstream.status, {
    "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(text);
}

async function handleCloudStatus(request, response) {
  try {
    const session = await getSessionUser(request);
    const providersRes = await fetch(`${ENV.NCB_AUTH_API_URL}/providers?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}`, {
      headers: {
        "X-Database-Instance": ENV.NCB_INSTANCE,
        Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`
      }
    });
    const providers = providersRes.ok ? await providersRes.json() : { providers: {} };
    sendJson(response, 200, {
      instance: ENV.NCB_INSTANCE,
      session,
      providers: providers.providers || {},
      ready: Boolean(ENV.NCB_INSTANCE && ENV.NCB_SECRET_KEY)
    });
  } catch (error) {
    sendJson(response, 500, {
      error: error instanceof Error ? error.message : "Cloud status failed."
    });
  }
}

function resolveStaticPath(pathname) {
  const cleanPath = pathname === "/" ? "/index.html" : pathname;
  const resolved = path.normalize(path.join(ROOT_DIR, cleanPath));
  if (!resolved.startsWith(ROOT_DIR)) return null;
  if (existsSync(resolved) && statSync(resolved).isFile()) return resolved;
  return null;
}

function serveStatic(response, filePath) {
  const extension = path.extname(filePath).toLowerCase();
  response.writeHead(200, {
    "Content-Type": MIME_TYPES[extension] || "application/octet-stream",
    "Cache-Control": extension === ".html" ? "no-store" : "public, max-age=60"
  });
  createReadStream(filePath).pipe(response);
}

const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const pathname = decodeURIComponent(url.pathname);

    if (pathname === "/api/cloud-status") {
      await handleCloudStatus(request, response);
      return;
    }
    if (pathname === "/api/auth-providers") {
      const upstream = await fetch(`${ENV.NCB_AUTH_API_URL}/providers?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}`, {
        headers: {
          "X-Database-Instance": ENV.NCB_INSTANCE,
          Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`
        }
      });
      const data = await upstream.text();
      response.writeHead(upstream.status, {
        "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
        "Cache-Control": "no-store"
      });
      response.end(data);
      return;
    }
    if (pathname.startsWith("/api/auth/")) {
      await proxyAuth(request, response, pathname);
      return;
    }
    if (pathname.startsWith("/api/data/")) {
      await proxyData(request, response, pathname, false);
      return;
    }
    if (pathname.startsWith("/api/public-data/")) {
      if (!["GET", "POST"].includes(request.method)) {
        sendJson(response, 405, { error: "Public data route only supports GET and POST." });
        return;
      }
      await proxyData(request, response, pathname, true);
      return;
    }

    const filePath = resolveStaticPath(pathname);
    if (!filePath) {
      sendText(response, 404, "Not found");
      return;
    }
    serveStatic(response, filePath);
  } catch (error) {
    sendJson(response, 500, {
      error: error instanceof Error ? error.message : "Server error"
    });
  }
});

server.listen(Number(ENV.PORT || 5180), "127.0.0.1", () => {
  console.log(`On2Cook Cloud server listening on http://127.0.0.1:${ENV.PORT || 5180}`);
});
