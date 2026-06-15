# Recipe Text Compare Report

Date: 2026-06-12

This report compares three representations for the 10 seeded order recipes:

1. Original recipe text inside each ZIP archive in `data/order_recipes`
2. The exact raw text the web app currently uploads to the device
3. The app's internal database representation after parsing and storing the recipe

## Summary

- Device upload payload:
  - All 10 recipes currently upload byte-for-byte identical text from the ZIP archive
- App database copy:
  - 4 recipes remain byte-for-byte identical
  - 6 recipes differ from the original archive text

## Why the upload matches

The web app now uploads `rawRecipeText` directly instead of reserializing `recipeJson`.

Relevant code:

- `src/ble-transport.js`
- `src/data-store.js`
- `src/zip-reader.js`

## Why the database copy can differ

The app parses JSON and stores a normalized `recipeJson` object. That can change:

- escaped `\u0026` sequences into literal `&`
- trailing spaces in the `name[0]` field
- formatting due to JSON serialization

## Results

| Recipe | Upload vs ZIP | DB vs ZIP | Notes |
|---|---|---|---|
| ALOO GOBI | Identical | Identical | No text change |
| BUTTER CHICKEN | Identical | Different | `\u0026` normalized to `&` |
| CABBAGE PORIYAL | Identical | Different | `\u0026` normalized to `&`; trailing space removed from `name[0]` |
| CAULIFLOWER SOUP | Identical | Identical | No text change |
| CHANA DAL | Identical | Different | `\u0026` normalized to `&`; trailing space removed from `name[0]` |
| CHEESE MAGGI | Identical | Identical | No text change |
| BROCCOLI CHEESE | Identical | Different | `\u0026` normalized to `&` |
| CHICKEN BIRYANI | Identical | Identical | No text change |
| CHICKEN CURRY | Identical | Different | `\u0026` normalized to `&` |
| CHICKEN STEW | Identical | Different | `\u0026` normalized to `&` |

## Exact differences found

These are not hypothetical differences. They are present in the current local code path after parsing and storing the recipe as `recipeJson`.

- `BUTTER CHICKEN`
  - Original archive text contains: `Cream \u0026 Butter`
  - Database copy contains: `Cream & Butter`

- `BROCCOLI CHEESE`
  - Original archive text contains: `Processed Cheese \u0026 Chilli Flakes`
  - Database copy contains: `Processed Cheese & Chilli Flakes`
  - Original archive text contains: `Dijon Mustard \u0026 Balsamic Vinegar`
  - Database copy contains: `Dijon Mustard & Balsamic Vinegar`

- `CABBAGE PORIYAL`
  - Original archive text `name[0]` has a trailing space
  - Database copy removes that trailing space
  - Archive text also contains escaped `\u0026` sequences that become literal `&`

- `CHANA DAL`
  - Original archive text `name[0]` has a trailing space
  - Database copy removes that trailing space
  - Archive text also contains escaped `\u0026` sequences that become literal `&`

- `CHICKEN CURRY`
  - Original archive text contains: `Garlic \u0026 Ginger`
  - Database copy contains: `Garlic & Ginger`

- `CHICKEN STEW`
  - Original archive text contains: `Curry Cut Chicken \u0026 Vegetables Indian Spices`
  - Database copy contains: `Curry Cut Chicken & Vegetables Indian Spices`
  - Original archive text contains: `Coconut Milk Thin \u0026 Thick`
  - Database copy contains: `Coconut Milk Thin & Thick`

## Root cause in code

- The BLE upload path uses the untouched archive text when `rawRecipeText` is present:
  - [src/ble-transport.js](C:/Users/baps/Downloads/on2cook-web-pwa/src/ble-transport.js:763)
- The app database path parses JSON and rewrites `recipe.name[0]` to the sanitized firmware name:
  - [src/data-store.js](C:/Users/baps/Downloads/on2cook-web-pwa/src/data-store.js:19)
- The original raw text is preserved separately, but the stored `recipeJson` object is not byte-for-byte preserved:
  - [src/data-store.js](C:/Users/baps/Downloads/on2cook-web-pwa/src/data-store.js:38)
  - [src/data-store.js](C:/Users/baps/Downloads/on2cook-web-pwa/src/data-store.js:51)

## Important interpretation

If you are testing the recipe text that is actually being shipped over BLE to the device, the current payload matches the ZIP source exactly for all 10 seeded recipes.

If you are testing recipes exported from the app database, synced through the app database layer, or reused from the app's stored `recipeJson`, some recipes are no longer byte-for-byte identical to the original archive text.

That means:

- a device-side run problem is unlikely to be caused by the current BLE upload text for these 10 recipes
- a database/export/import problem can still be caused by the app's normalized database representation
