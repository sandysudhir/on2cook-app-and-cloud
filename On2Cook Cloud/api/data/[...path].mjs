import { handleDataProxy } from "../_lib/ncb-proxy.mjs";

export default async function handler(request, response) {
  const pathValue = Array.isArray(request.query.path) ? request.query.path.join("/") : request.query.path || "";
  const search = new URL(request.url, "https://placeholder.local").searchParams;
  search.delete("path");
  await handleDataProxy(request, response, pathValue, search.toString(), false);
}
