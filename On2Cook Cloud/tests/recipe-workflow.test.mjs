import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const appSource = readFileSync(new URL("../src/app.js", import.meta.url), "utf8");
const styleSource = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

test("recipe navigation separates library destinations from recipe actions", () => {
  const renderStart = appSource.indexOf("function renderRecipesTab(");
  const renderEnd = appSource.indexOf("function renderMoreTab(", renderStart);
  const renderSource = appSource.slice(renderStart, renderEnd);

  assert.match(renderSource, /Kitchen Recipes/);
  assert.match(renderSource, /Custom Recipes/);
  assert.match(renderSource, /Import Recipe/);
  assert.doesNotMatch(renderSource, /Final Modified/);
  assert.doesNotMatch(renderSource, />Scale<\/button>/);
  assert.match(styleSource, /\.recipe-library-tabs[\s\S]*?grid-template-columns:\s*repeat\(3/);
});

test("scaling targets one recipe and returns the saved copy to Custom Recipes", () => {
  const scaleStart = appSource.indexOf("async function saveScaledRecipe(");
  const scaleEnd = appSource.indexOf("function recipeJsonToProMinutes", scaleStart);
  const scaleSource = appSource.slice(scaleStart, scaleEnd);

  assert.match(appSource, /recipeScaleId/);
  assert.match(scaleSource, /draft\.ui\.recipeMode\s*=\s*"final"/);
  assert.match(scaleSource, /draft\.ui\.recipeScaleId\s*=\s*""/);
  assert.match(appSource, /data-form="scale-recipe"/);
  assert.match(appSource, /Save as Custom Recipe/);
});

test("saving a custom recipe preserves its base and sibling custom versions", () => {
  const saveStart = appSource.indexOf("function saveProfessionalRecipe()");
  const saveEnd = appSource.indexOf("async function saveLiveDraftToLibrary", saveStart);
  const saveSource = appSource.slice(saveStart, saveEnd);

  assert.match(saveSource, /filter\(\(recipe\) => recipe\.id !== finalRecipe\.id\)/);
  assert.match(saveSource, /baseDraft\.selected = false/);
  assert.doesNotMatch(saveSource, /recipe\.baseRecipeId === baseRecipe\.id/);
  assert.match(saveSource, /Give the custom recipe a new name before saving/);
});
