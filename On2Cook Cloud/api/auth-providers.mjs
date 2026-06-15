import { handleAuthProviders } from "./_lib/ncb-proxy.mjs";

export default async function handler(request, response) {
  await handleAuthProviders(request, response);
}
