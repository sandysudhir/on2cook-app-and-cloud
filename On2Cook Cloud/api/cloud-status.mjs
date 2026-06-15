import { handleCloudStatus } from "./_lib/ncb-proxy.mjs";

export default async function handler(request, response) {
  await handleCloudStatus(request, response);
}
