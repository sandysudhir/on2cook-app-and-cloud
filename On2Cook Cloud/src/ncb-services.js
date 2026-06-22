function asArray(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.records)) return payload.records;
  if (Array.isArray(payload?.result)) return payload.result;
  return [];
}

async function requestJson(url, options = {}) {
  const cleanUrl = String(url || "").trim();
  if (!cleanUrl || cleanUrl === "undefined" || cleanUrl === "null" || /\/undefined(?:[?#]|$)/i.test(cleanUrl)) {
    console.warn("[On2Cook] Skipping cloud request because URL is missing.", { url });
    throw new Error("Cloud request URL is missing.");
  }
  const response = await fetch(cleanUrl, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (error) {
      console.warn("[On2Cook] Cloud endpoint returned non-JSON content.", {
        url: cleanUrl,
        status: response.status,
        preview: text.slice(0, 160),
        error
      });
      throw new Error("Cloud service is unavailable. Please try again later.");
    }
  }
  if (!response.ok) {
    throw new Error(data?.error || data?.message || `Request failed: ${response.status}`);
  }
  return data;
}

export function recipeSignatureFromJson(recipeJson) {
  const text = typeof recipeJson === "string" ? recipeJson : JSON.stringify(recipeJson || {});
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `sig_${(hash >>> 0).toString(16)}`;
}

function recipeDescription(recipe) {
  return String(recipe?.recipeJson?.description || recipe?.description || "").trim();
}

function recipeCuisine(recipe) {
  return String(recipe?.recipeJson?.cuisine || recipe?.cuisine || "").trim();
}

function recipeCategory(recipe) {
  return String(recipe?.category || recipe?.recipeJson?.category || "Recipes").trim();
}

function recipeServings(recipe) {
  const value = Number(recipe?.recipeJson?.servings || recipe?.servings || 0);
  return Number.isFinite(value) ? value : 0;
}

function recipeVisibility(recipe) {
  return recipe?.selected === false ? "private" : "kitchen";
}

function recipeStatus(recipe) {
  if (recipe?.cloudDeleted) return "archived";
  return recipe?.type === "final" ? "active" : "draft";
}

function toCloudRecipePayload(recipe, existingRow = null) {
  return {
    ...(existingRow?.id ? { id: existingRow.id } : {}),
    title: recipe.displayName,
    description: recipeDescription(recipe),
    cuisine: recipeCuisine(recipe),
    category: recipeCategory(recipe),
    servings: recipeServings(recipe),
    visibility: recipeVisibility(recipe),
    status: recipeStatus(recipe),
    firmware_recipe_json: JSON.stringify(recipe.recipeJson || {}),
    mobile_hidden: recipe.selected === false,
    cloud_deleted: Boolean(recipe.cloudDeleted),
    last_synced_at: new Date().toISOString(),
    base_recipe_name: recipe.baseRecipeId || recipe.displayName,
    base_zip_name: recipe.zipName || "",
    recipe_signature: recipe.recipeSignature || recipeSignatureFromJson(recipe.recipeJson),
    created_at: existingRow?.created_at || recipe.createdAt || new Date().toISOString(),
    updated_at: new Date().toISOString()
  };
}

export const authService = {
  async getStatus() {
    return requestJson("/api/cloud-status");
  },
  async getProviders() {
    const result = await requestJson("/api/auth-providers", { credentials: "omit" });
    return result?.providers || {};
  },
  async signInEmail({ email, password }) {
    return requestJson("/api/auth/sign-in/email", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });
  },
  async signUpEmail({ email, password, name }) {
    return requestJson("/api/auth/sign-up/email", {
      method: "POST",
      body: JSON.stringify({ email, password, name })
    });
  },
  async signOut() {
    return requestJson("/api/auth/sign-out", {
      method: "POST",
      body: JSON.stringify({})
    });
  }
};

export const profileService = {
  async list() {
    return asArray(await requestJson("/api/data/read/profiles"));
  },
  async getMine(sessionUser) {
    const rows = await this.list();
    return rows.find((row) => row.user_id === sessionUser?.id || row.email === sessionUser?.email) || null;
  },
  async upsertCurrentProfile(sessionUser, localUser, extra = {}) {
    const existing = await this.getMine(sessionUser);
    const payload = {
      user_id: sessionUser.id,
      email: sessionUser.email || localUser?.email || "",
      full_name: extra.full_name || localUser?.displayName || sessionUser.name || "",
      mobile_phone: extra.mobile_phone || "",
      whatsapp_phone: extra.whatsapp_phone || "",
      role: extra.role || localUser?.role || "operator",
      status: extra.status || "active",
      facility_id: localUser?.facilityId || "",
      facility_name: extra.facility_name || "On2Cook Demo Kitchen",
      franchise_id: "",
      franchise_name: "",
      reports_to_user_id: "",
      manager_mode: Boolean(localUser?.managerMode),
      can_add_recipes: Boolean(extra.can_add_recipes ?? localUser?.canAddRecipes),
      can_edit_recipes: Boolean(extra.can_edit_recipes ?? localUser?.canEditRecipes),
      can_manage_recipe_access: Boolean(extra.can_manage_recipe_access ?? localUser?.canManageRecipeAccess),
      created_at: existing?.created_at || new Date().toISOString(),
      updated_at: new Date().toISOString()
    };
    if (existing?.id) {
      return requestJson(`/api/data/update/profiles/${existing.id}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
    }
    return requestJson("/api/data/create/profiles", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  async createManagedProfile(localUser, managerProfile = null) {
    const payload = {
      email: localUser.email || "",
      full_name: localUser.displayName || "",
      mobile_phone: localUser.mobilePhone || "",
      whatsapp_phone: localUser.whatsappPhone || "",
      role: localUser.role || "operator",
      status: localUser.status || "invited",
      facility_id: localUser.facilityId || managerProfile?.facility_id || "",
      facility_name: managerProfile?.facility_name || "On2Cook Demo Kitchen",
      franchise_id: managerProfile?.franchise_id || "",
      franchise_name: managerProfile?.franchise_name || "",
      reports_to_user_id: managerProfile?.user_id || "",
      manager_mode: Boolean(localUser.managerMode),
      can_add_recipes: Boolean(localUser.canAddRecipes),
      can_edit_recipes: Boolean(localUser.canEditRecipes),
      can_manage_recipe_access: Boolean(localUser.canManageRecipeAccess),
      created_at: localUser.createdAt || new Date().toISOString(),
      updated_at: new Date().toISOString()
    };
    return requestJson("/api/data/create/profiles", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }
};

export const recipeService = {
  async listMine() {
    return asArray(await requestJson("/api/data/read/recipes"));
  },
  async listVersions() {
    return asArray(await requestJson("/api/data/read/recipe_versions"));
  },
  async createVersionFromRow(row, note = "Cloud sync overwrite") {
    if (!row?.id) return null;
    return requestJson("/api/data/create/recipe_versions", {
      method: "POST",
      body: JSON.stringify({
        recipe_id: row.id,
        title: row.title,
        firmware_recipe_json: row.firmware_recipe_json,
        change_note: note,
        version_tag: new Date().toISOString(),
        created_at: new Date().toISOString()
      })
    });
  },
  async upsertLocalRecipe(recipe, existingRows = []) {
    const signature = recipe.recipeSignature || recipeSignatureFromJson(recipe.recipeJson);
    const existing =
      existingRows.find((row) => String(row.id) === String(recipe.cloudRecordId || "")) ||
      existingRows.find((row) => row.recipe_signature === signature) ||
      existingRows.find((row) => String(row.title || "").trim() === String(recipe.displayName || "").trim()) ||
      null;
    const payload = toCloudRecipePayload(
      {
        ...recipe,
        recipeSignature: signature
      },
      existing
    );
    if (existing?.id) {
      if (existing.firmware_recipe_json && existing.firmware_recipe_json !== payload.firmware_recipe_json) {
        await this.createVersionFromRow(existing);
      }
      const updated = await requestJson(`/api/data/update/recipes/${existing.id}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      return { row: updated, cloudId: existing.id, signature };
    }
    const created = await requestJson("/api/data/create/recipes", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const createdRow = Array.isArray(created) ? created[0] : created;
    return { row: createdRow, cloudId: createdRow?.id || null, signature };
  },
  async hideOnMobile(rowId) {
    return requestJson(`/api/data/update/recipes/${rowId}`, {
      method: "PUT",
      body: JSON.stringify({
        mobile_hidden: true,
        last_synced_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      })
    });
  },
  async cloudDelete(rowId) {
    return requestJson(`/api/data/update/recipes/${rowId}`, {
      method: "PUT",
      body: JSON.stringify({
        cloud_deleted: true,
        updated_at: new Date().toISOString()
      })
    });
  }
};

export const deviceService = {
  async list() {
    return asArray(await requestJson("/api/data/read/devices"));
  },
  async upsertFromState(devices) {
    const existingRows = await this.list();
    const results = [];
    for (const device of devices) {
      const deviceId = device.browserDeviceId || `slot-${device.slot}`;
      const existing = existingRows.find((row) => row.device_id === deviceId) || null;
      const payload = {
        device_id: deviceId,
        bluetooth_name: device.bluetoothName || "",
        display_name: device.displayName || `On2Cook-0${device.slot}`,
        serial_photo_url: device.serialPhotoDataUrl || "",
        status: device.connection === "connected" ? "connected" : device.connection === "disconnected" ? "disconnected" : "offline",
        firmware_version: device.telemetry?.firmwareVersion || "",
        inventory_recipe_names: JSON.stringify(device.availableRecipeNames || []),
        allowed_recipe_ids: JSON.stringify(device.allowedRecipeIds || []),
        last_seen_at: device.lastUpdatedAt || "",
        last_log_sync_at: "",
        notes: device.lastMessage || "",
        created_at: existing?.created_at || new Date().toISOString(),
        updated_at: new Date().toISOString()
      };
      const result = existing?.id
        ? await requestJson(`/api/data/update/devices/${existing.id}`, {
            method: "PUT",
            body: JSON.stringify(payload)
          })
        : await requestJson("/api/data/create/devices", {
            method: "POST",
            body: JSON.stringify(payload)
          });
      results.push(result);
    }
    return results;
  }
};

export const cookLogService = {
  async list() {
    return asArray(await requestJson("/api/data/read/cook_logs"));
  },
  async append(entry) {
    return requestJson("/api/data/create/cook_logs", {
      method: "POST",
      body: JSON.stringify({
        ...entry,
        created_at: entry.created_at || new Date().toISOString()
      })
    });
  }
};

export const syncService = {
  async syncState(state) {
    const status = await authService.getStatus();
    const sessionUser = status?.session;
    if (!sessionUser?.id) {
      throw new Error("Sign in to On2Cook Cloud before syncing.");
    }

    const localUser = state.users.find((user) => user.id === state.currentUserId) || null;
    const profile = await profileService.upsertCurrentProfile(sessionUser, localUser);

    const recipeRows = await recipeService.listMine();
    const recipeMappings = [];
    for (const recipe of state.recipes) {
      const result = await recipeService.upsertLocalRecipe(recipe, recipeRows);
      recipeMappings.push({
        localId: recipe.id,
        cloudId: result.cloudId,
        signature: result.signature
      });
    }

    await deviceService.upsertFromState(state.devices || []);

    return {
      profile,
      sessionUser,
      recipeMappings,
      recipeCount: recipeMappings.length,
      deviceCount: state.devices.length
    };
  },
  async restoreRecipes() {
    const status = await authService.getStatus();
    const sessionUser = status?.session;
    if (!sessionUser?.id) {
      throw new Error("Sign in to On2Cook Cloud before restoring.");
    }
    const rows = await recipeService.listMine();
    return rows.filter((row) => !row.cloud_deleted);
  }
};
