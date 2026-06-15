import { handleDataProxy, sendJson } from "../_lib/ncb-proxy.mjs";

export default async function handler(request, response) {
  if (!["GET", "POST"].includes(request.method)) {
    sendJson(response, 405, { error: "Public data route only supports GET and POST." });
    return;
  }
  const pathValue = Array.isArray(request.query.path) ? request.query.path.join("/") : request.query.path || "";
  const search = new URL(request.url, "https://placeholder.local").searchParams;
  search.delete("path");
  await handleDataProxy(request, response, pathValue, search.toString(), true);
}
