# On2Cook Login And Permission Model

## NoCodeBackend connection

The web app uses the local PHP proxy in `api/` to call NoCodeBackend. The token is configured server-side and must not be exposed in browser JavaScript.

Current NoCodeBackend MCP access is not exposed in this Codex runtime, so backend table creation must be done from the NoCodeBackend dashboard or from a runtime where the `nocodebackend` MCP server is available.

## Required profile fields

Every authenticated user must have a `profiles` row with:

- `email`
- `full_name`
- `mobile_phone`
- `whatsapp_phone`
- `role`
- `status`
- `facility_id`
- `facility_name`
- `franchise_id`
- `franchise_name`
- `reports_to_user_id`
- `manager_mode`
- `can_add_recipes`
- `can_edit_recipes`
- `can_manage_recipe_access`

The permission columns are stored as `0` or `1` integers for broad NoCodeBackend compatibility.

## Roles

- `main_admin`: can add people, select global recipes, import recipes, edit/customize recipes, assign recipes to devices, and manage permissions.
- `kitchen_manager`: can work only with selected kitchen recipes. Can edit/customize recipes when `can_edit_recipes = 1`.
- `operator`: can work only with selected kitchen recipes. Can run recipes and operate orders/devices, but cannot import, add, or edit recipes.
- `owner`: treated like `kitchen_manager`.
- `cook`: treated like `operator`.

## Recipe Access Rules

The cloud is the master source of truth. A user logging in from a new phone should restore recipes from the cloud.

Base recipes from the global library are available only to users with global recipe selection rights. Once selected into the kitchen recipe list, they become visible to kitchen managers and operators assigned to that facility.

Operators can run recipes only. Kitchen managers can optimize/edit selected recipes, including ingredients, quantity, and final recipe variants. New edited recipes must be saved as a separate recipe name.

## Managed User Creation Limitation

The current `/api/data/create/profiles` proxy injects the signed-in session user id. This is correct for creating the current user's profile, but a full invitation flow that creates login accounts for another person requires NoCodeBackend admin/user-invite support. Until that admin API is wired, the app stores managed people locally and attempts to create a profile row for tracking, while the invited person still needs an auth account.
