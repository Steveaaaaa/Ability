import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resourceLocation = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const semanticParameter = /^[a-z][a-z0-9_]*$/;

async function readJson(relativePath) {
  return JSON.parse(await fs.readFile(path.join(root, relativePath), "utf8"));
}

function assertTypedConfig(value, label) {
  assert.equal(typeof value, "object", `${label} must be an object`);
  assert.match(value.type, resourceLocation, `${label}.type must be a resource location`);
  assert.equal(typeof value.config, "object", `${label}.config must be an object`);
}

const skill = await readJson("examples/datapack/data/ability/ability/skills/mining.json");
assert.equal(skill.schema_version, 1);
assert.equal(skill.xp_to_next.length, skill.max_level, "skill XP curve length must equal max_level");
assert(skill.xp_to_next.every((value) => Number.isInteger(value) && value > 0));
assert.match(skill.display.icon, resourceLocation);

const abilityPaths = [
  "examples/datapack/data/ability/ability/abilities/associated_ore.json",
  "examples/datapack/data/ability/ability/abilities/efficient_mining_example.json",
  "examples/datapack/data/ability/ability/abilities/projectile_damage_example.json",
  "examples/datapack/data/ability/ability/abilities/damage_reduction_example.json",
  "examples/datapack/data/ability/ability/abilities/block_loot_example.json",
  "examples/datapack/data/ability/ability/abilities/entity_loot_example.json",
];
for (const abilityPath of abilityPaths) {
  const ability = await readJson(abilityPath);
  assert.equal(ability.schema_version, 1);
  assert.match(ability.skill, resourceLocation);
  assert.equal(
    ability.ranks.unlock_skill_levels.length,
    ability.ranks.values.length,
    "ability rank unlock and value counts must match",
  );
  assert(
    ability.ranks.unlock_skill_levels.every((level, index, levels) => index === 0 || level > levels[index - 1]),
    "ability rank unlock levels must be strictly increasing",
  );
  for (const [index, values] of ability.ranks.values.entries()) {
    for (const key of Object.keys(values)) {
      assert.match(key, semanticParameter, `rank ${index + 1} parameter must use a semantic snake_case name`);
    }
  }
  ability.purchase.requirements.forEach((condition, index) => assertTypedConfig(condition, `requirement ${index}`));
  assertTypedConfig(ability.effect, "effect");
}

const source = await readJson("examples/datapack/data/ability/ability/experience_sources/mine_ores.json");
assert.equal(source.schema_version, 1);
assert.match(source.skill, resourceLocation);
assert(Number.isInteger(source.base_xp) && source.base_xp >= 0);
assertTypedConfig(source.trigger, "trigger");
source.conditions.forEach((condition, index) => assertTypedConfig(condition, `experience condition ${index}`));
assert(source.anti_abuse.target_cooldown_ticks >= 0);

console.log("Validated 8 data definitions and their cross-field constraints.");
