const ENV = {
  NCB_INSTANCE: process.env.NCB_INSTANCE || "",
  NCB_AUTH_API_URL: process.env.NCB_AUTH_API_URL || "https://app.nocodebackend.com/api/user-auth",
  NCB_DATA_API_URL: process.env.NCB_DATA_API_URL || "https://app.nocodebackend.com/api/data",
  NCB_APP_URL: process.env.NCB_APP_URL || "https://app.nocodebackend.com",
  NCB_SECRET_KEY: process.env.NCB_SECRET_KEY || ""
};

export function sendJson(response, statusCode, payload) {
  response.status(statusCode).setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cache-Control", "no-store");
  response.send(JSON.stringify(payload));
}

export function getRequestOrigin(request) {
  const proto = request.headers["x-forwarded-proto"] || "https";
  const host = request.headers.host || "localhost";
  return `${proto}://${host}`;
}

async function collectBody(request) {
  if (request.body !== undefined && request.body !== null) {
    if (typeof request.body === "string") return request.body;
    return JSON.stringify(request.body);
  }
  return await new Promise((resolve, reject) => {
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
        cookie.startsWith("better-auth.session_data=") ||
        cookie.startsWith("__Secure-better-auth.session_token=") ||
        cookie.startsWith("__Secure-better-auth.session_data=") ||
        cookie.startsWith("__Host-better-auth.session_token=") ||
        cookie.startsWith("__Host-better-auth.session_data=")
    )
    .join("; ");
}

function rewriteSetCookieForCurrentHost(cookie) {
  const segments = cookie.split(";").map((part) => part.trim()).filter(Boolean);
  const filtered = segments.filter((segment) => !segment.toLowerCase().startsWith("domain="));
  const hasPath = filtered.some((segment) => segment.toLowerCase().startsWith("path="));
  if (!hasPath) filtered.push("Path=/");
  return filtered.join("; ");
}

function getSetCookies(headers) {
  if (typeof headers.getSetCookie === "function") {
    return headers.getSetCookie();
  }
  const raw = headers.get("set-cookie");
  return raw ? [raw] : [];
}

function upstreamHeaders(request, includeCookies = true) {
  const cookieHeader = includeCookies ? extractAuthCookies(request.headers.cookie || "") : "";
  const headers = {
    "Content-Type": "application/json",
    "X-Database-Instance": ENV.NCB_INSTANCE,
    Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`,
    Origin: getRequestOrigin(request)
  };
  if (cookieHeader) headers.Cookie = cookieHeader;
  return headers;
}

export async function getSessionUser(request) {
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

export async function handleCloudStatus(request, response) {
  try {
    const session = await getSessionUser(request);
    const providersRes = await fetch(
      `${ENV.NCB_AUTH_API_URL}/providers?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}`,
      {
        headers: {
          "X-Database-Instance": ENV.NCB_INSTANCE,
          Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`
        }
      }
    );
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

export async function handleAuthProviders(request, response) {
  try {
    const upstream = await fetch(
      `${ENV.NCB_AUTH_API_URL}/providers?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}`,
      {
        headers: {
          "X-Database-Instance": ENV.NCB_INSTANCE,
          Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`
        }
      }
    );
    const data = await upstream.text();
    response.status(upstream.status);
    response.setHeader(
      "Content-Type",
      upstream.headers.get("content-type") || "application/json; charset=utf-8"
    );
    response.setHeader("Cache-Control", "no-store");
    response.send(data);
  } catch (error) {
    sendJson(response, 500, { error: error instanceof Error ? error.message : "Auth provider lookup failed." });
  }
}

export async function handleAuthProxy(request, response, pathSuffix = "", search = "") {
  try {
    const upstreamUrl = `${ENV.NCB_AUTH_API_URL}/${pathSuffix}?instance=${encodeURIComponent(ENV.NCB_INSTANCE)}${
      search ? `&${search}` : ""
    }`;
    const body = ["GET", "HEAD"].includes(request.method) ? undefined : await collectBody(request);
    const upstream = await fetch(upstreamUrl, {
      method: request.method,
      headers: {
        "Content-Type": "application/json",
        "X-Database-Instance": ENV.NCB_INSTANCE,
        Authorization: `Bearer ${ENV.NCB_SECRET_KEY}`,
        Cookie: extractAuthCookies(request.headers.cookie || ""),
        Origin: getRequestOrigin(request)
      },
      body: body || undefined
    });
    const text = await upstream.text();
    response.status(upstream.status);
    response.setHeader(
      "Content-Type",
      upstream.headers.get("content-type") || "application/json; charset=utf-8"
    );
    response.setHeader("Cache-Control", "no-store");
    const setCookies = getSetCookies(upstream.headers).map(rewriteSetCookieForCurrentHost);
    if (setCookies.length > 0) {
      response.setHeader("Set-Cookie", setCookies);
    }
    response.send(text);
  } catch (error) {
    sendJson(response, 500, { error: error instanceof Error ? error.message : "Auth proxy failed." });
  }
}

export async function handleDataProxy(request, response, pathSuffix = "", search = "", isPublic = false) {
  try {
    const sessionUser = isPublic ? null : await getSessionUser(request);
    if (!isPublic && !sessionUser) {
      sendJson(response, 401, { error: "Unauthorized" });
      return;
    }

    let body = ["GET", "HEAD", "DELETE"].includes(request.method) ? "" : await collectBody(request);
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

    const upstreamUrl = `${ENV.NCB_DATA_API_URL}/${pathSuffix}?Instance=${encodeURIComponent(ENV.NCB_INSTANCE)}${
      search ? `&${search}` : ""
    }`;
    const upstream = await fetch(upstreamUrl, {
      method: request.method,
      headers: upstreamHeaders(request, !isPublic),
      body: body || undefined
    });
    const text = await upstream.text();
    response.status(upstream.status);
    response.setHeader(
      "Content-Type",
      upstream.headers.get("content-type") || "application/json; charset=utf-8"
    );
    response.setHeader("Cache-Control", "no-store");
    response.send(text);
  } catch (error) {
    sendJson(response, 500, { error: error instanceof Error ? error.message : "Data proxy failed." });
  }
}
