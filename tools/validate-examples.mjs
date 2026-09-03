import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const namespace = "fantasypower";
const resourceLocation = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const semanticParameter = /^[a-z][a-z0-9_]*$/;
const translationKey = /^[a-z0-9_.-]+$/;
const builtInRoot = path.join(root, "src/main/resources/data/fantasypower/fantasypower");
const exampleRoot = path.join(root, "examples/datapack/data/fantasypower/fantasypower");

const effectTypes = new Set([
  "associated_ore", "attribute_modifier", "blast_excavation", "ceiling_wire", "charged_leap",
  "chorus_transmutation", "cold_current", "companion_gift", "composite", "conditional_mob_effect",
  "counter_sniper", "crushing_blow", "damage_modifier", "damage_reduction", "dangerous_charge",
  "dodge", "enchanted_edge", "exhaustion", "fine_feed", "frugality", "greed", "harvest",
  "iron_cavalry", "loot_injection", "obsidian_reinforcement", "primer", "retaliatory_flame",
  "stealth", "support_aura", "survival_skills", "weak_point", "well_prepared", "wolf_pack",
  "world_traveler",
].map((id) => `${namespace}:${id}`));
const triggerTypes = new Set([
  "break_block", "kill_entity", "harvest_crop", "breed_animal", "place_block", "travel",
  "ranged_kill", "take_damage", "enchant_item",
].map((id) => `${namespace}:${id}`));
const conditionTypes = new Set([
  "skill_level", "ability_purchased", "advancement", "all_of", "any_of", "not",
  "game_mode", "not_game_mode", "dimension",
].map((id) => `${namespace}:${id}`));
const expectedSkills = new Set([
  "agility", "archery", "building", "combat", "defense", "farming", "gathering", "husbandry", "magic", "mining",
].map((id) => `${namespace}:${id}`));
const expectedAbilities = new Set([
  "ambush", "associated_ore", "blast_excavation", "breakthrough", "ceiling_wire", "charged_leap",
  "chorus_transmutation", "cold_current", "counter_sniper", "crushing_blow", "dangerous_charge", "dodge",
  "enchanted_edge", "energetic", "exhaustion", "fine_feed", "frugality", "gravel_panning", "greed", "harvest",
  "iron_cavalry", "long_journey", "lucky_cat", "obsidian_reinforcement", "primer", "rapid_thrust",
  "retaliatory_flame", "sniffer_treasure", "stealth", "support_aura", "survival_skills", "survivor", "weak_point",
  "well_prepared", "wolf_pack", "world_traveler",
].map((id) => `${namespace}:${id}`));
const expectedSources = new Set([
  "breed_animals", "enchant_items", "harvest_mature_crops", "kill_hostile_mobs", "mine_ores",
  "place_building_blocks", "ranged_kill_hostile_mobs", "take_final_damage", "travel_on_foot",
].map((id) => `${namespace}:${id}`));

function validateCatalogIds(actual, expected, label) {
  assert.deepEqual([...actual.keys()].sort(), [...expected].sort(), `${label} catalog does not match the built-in manifest`);
}

async function readDefinitions(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const definitions = new Map();
  for (const entry of entries.filter((value) => value.isFile() && value.name.endsWith(".json")).sort((a, b) => a.name.localeCompare(b.name))) {
    const file = path.join(directory, entry.name);
    const id = `${namespace}:${entry.name.slice(0, -5)}`;
    definitions.set(id, { id, file, value: JSON.parse(await fs.readFile(file, "utf8")) });
  }
  return definitions;
}

function assertObject(value, label) {
  assert(value !== null && typeof value === "object" && !Array.isArray(value), `${label} must be an object`);
}

function validateDisplay(display, label) {
  assertObject(display, label);
  assert.match(display.name, translationKey, `${label}.name must be a translation key`);
  assert.match(display.description, translationKey, `${label}.description must be a translation key`);
  assert.match(display.icon, resourceLocation, `${label}.icon must be a resource location`);
  if (display.color !== undefined) assert.match(display.color, /^#[0-9A-Fa-f]{6}$/, `${label}.color must be #RRGGBB`);
  if (display.sort_order !== undefined) assert(Number.isInteger(display.sort_order), `${label}.sort_order must be an integer`);
}

function validateTypedConfig(value, allowedTypes, label) {
  assertObject(value, label);
  assert.match(value.type, resourceLocation, `${label}.type must be a resource location`);
  assert(allowedTypes.has(value.type), `${label}.type is not registered: ${value.type}`);
  assertObject(value.config, `${label}.config`);
}

function validateCondition(condition, label, skillIds, abilityIds, depth = 0) {
  assert(depth < 64, `${label} exceeds maximum nesting depth`);
  validateTypedConfig(condition, conditionTypes, label);
  const config = condition.config;
  switch (condition.type) {
    case `${namespace}:skill_level`:
      assert(skillIds.has(config.skill), `${label}.config.skill is unknown: ${config.skill}`);
      assert(Number.isInteger(config.level) && config.level >= 0, `${label}.config.level must be non-negative`);
      break;
    case `${namespace}:ability_purchased`:
      assert(abilityIds.has(config.ability), `${label}.config.ability is not a built-in ability: ${config.ability}`);
      break;
    case `${namespace}:all_of`:
    case `${namespace}:any_of`:
      assert(Array.isArray(config.conditions), `${label}.config.conditions must be an array`);
      config.conditions.forEach((nested, index) => validateCondition(nested, `${label}.config.conditions[${index}]`, skillIds, abilityIds, depth + 1));
      break;
    case `${namespace}:not`:
      validateCondition(config.condition, `${label}.config.condition`, skillIds, abilityIds, depth + 1);
      break;
    default:
      break;
  }
}

function validateSkill({ id, value: skill }) {
  assert.equal(skill.schema_version ?? 1, 1, `${id}.schema_version`);
  validateDisplay(skill.display, `${id}.display`);
  assert(Number.isInteger(skill.max_level) && skill.max_level >= 1, `${id}.max_level`);
  assert(Array.isArray(skill.xp_to_next) && skill.xp_to_next.length === skill.max_level, `${id}.xp_to_next length must equal max_level`);
  assert(skill.xp_to_next.every((value) => Number.isInteger(value) && value > 0), `${id}.xp_to_next values must be positive integers`);
  assert(Number.isInteger(skill.skill_points_per_level) && skill.skill_points_per_level >= 0, `${id}.skill_points_per_level`);
}

function validateAbility({ id, value: ability }, skillIds, abilityIds) {
  assert.equal(ability.schema_version ?? 1, 1, `${id}.schema_version`);
  assert(skillIds.has(ability.skill), `${id}.skill is unknown: ${ability.skill}`);
  validateDisplay(ability.display, `${id}.display`);
  assertObject(ability.purchase, `${id}.purchase`);
  assert(Number.isInteger(ability.purchase.skill_level) && ability.purchase.skill_level >= 0, `${id}.purchase.skill_level`);
  assert(Number.isInteger(ability.purchase.skill_points) && ability.purchase.skill_points >= 0, `${id}.purchase.skill_points`);
  const requirements = ability.purchase.requirements ?? [];
  assert(Array.isArray(requirements), `${id}.purchase.requirements must be an array`);
  requirements.forEach((condition, index) => validateCondition(condition, `${id}.purchase.requirements[${index}]`, skillIds, abilityIds));

  assertObject(ability.ranks, `${id}.ranks`);
  const levels = ability.ranks.unlock_skill_levels;
  const costs = ability.ranks.skill_point_costs;
  const values = ability.ranks.values;
  assert(Array.isArray(levels) && levels.length > 0, `${id} must define at least one rank`);
  assert(Array.isArray(costs) && costs.length === levels.length, `${id} skill-point cost count must equal rank count`);
  assert(Array.isArray(values) && values.length === levels.length, `${id} rank value count must equal rank count`);
  assert.equal(costs[0], ability.purchase.skill_points, `${id} first rank cost must equal purchase.skill_points`);
  levels.forEach((level, index) => {
    assert(Number.isInteger(level) && level >= 0, `${id}.ranks.unlock_skill_levels[${index}]`);
    if (index > 0) assert(level >= levels[index - 1], `${id} unlock levels must be non-decreasing`);
  });
  costs.forEach((cost, index) => assert(Number.isInteger(cost) && cost >= 0, `${id}.ranks.skill_point_costs[${index}]`));
  values.forEach((rank, index) => {
    assertObject(rank, `${id}.ranks.values[${index}]`);
    Object.keys(rank).forEach((key) => assert.match(key, semanticParameter, `${id} rank parameter must use snake_case: ${key}`));
  });

  validateTypedConfig(ability.effect, effectTypes, `${id}.effect`);
  validateGolemConfig(id, ability.effect);
}

function validateGolemConfig(id, effect) {
  const config = effect.config;
  if (effect.type === `${namespace}:cold_current`) {
    assert(Array.isArray(config.stage_ticks) && config.stage_ticks.length === 4, `${id}.effect.config.stage_ticks must contain four values`);
    config.stage_ticks.forEach((tick, index) => {
      assert(Number.isInteger(tick) && tick > 0, `${id}.effect.config.stage_ticks[${index}]`);
      if (index > 0) assert(tick > config.stage_ticks[index - 1], `${id}.effect.config.stage_ticks must be strictly increasing`);
    });
    for (const key of ["attack_damage_flat", "max_health_flat", "armor_flat", "armor_toughness_flat", "projectile_damage"]) {
      assert(Number.isFinite(config[key]) && config[key] >= 0, `${id}.effect.config.${key} must be non-negative`);
    }
  } else if (effect.type === `${namespace}:crushing_blow`) {
    assert(Number.isInteger(config.impact_tick) && config.impact_tick > 0, `${id}.effect.config.impact_tick`);
    assert(Number.isInteger(config.release_duration_ticks) && config.release_duration_ticks > config.impact_tick, `${id}.effect.config.release_duration_ticks must exceed impact_tick`);
    assert(Number.isFinite(config.heal_percent) && config.heal_percent >= 0 && config.heal_percent <= 100, `${id}.effect.config.heal_percent`);
    assert(Number.isFinite(config.radius) && config.radius >= 0, `${id}.effect.config.radius`);
  } else if (effect.type === `${namespace}:obsidian_reinforcement`) {
    assert(Number.isInteger(config.charge_threshold) && config.charge_threshold > 0, `${id}.effect.config.charge_threshold`);
  }
}

function validateExperienceSource({ id, value: source }, skillIds, abilityIds) {
  assert.equal(source.schema_version ?? 1, 1, `${id}.schema_version`);
  assert(skillIds.has(source.skill), `${id}.skill is unknown: ${source.skill}`);
  assert(Number.isInteger(source.base_xp) && source.base_xp >= 0, `${id}.base_xp`);
  validateTypedConfig(source.trigger, triggerTypes, `${id}.trigger`);
  const conditions = source.conditions ?? [];
  assert(Array.isArray(conditions), `${id}.conditions must be an array`);
  conditions.forEach((condition, index) => validateCondition(condition, `${id}.conditions[${index}]`, skillIds, abilityIds));
  assertObject(source.anti_abuse, `${id}.anti_abuse`);
  assert(Number.isInteger(source.anti_abuse.target_cooldown_ticks) && source.anti_abuse.target_cooldown_ticks >= 0, `${id}.anti_abuse.target_cooldown_ticks`);
}

const skills = await readDefinitions(path.join(builtInRoot, "skills"));
const abilities = await readDefinitions(path.join(builtInRoot, "abilities"));
const sources = await readDefinitions(path.join(builtInRoot, "experience_sources"));
validateCatalogIds(skills, expectedSkills, "skill");
validateCatalogIds(abilities, expectedAbilities, "ability");
validateCatalogIds(sources, expectedSources, "experience source");
const skillIds = new Set(skills.keys());
const abilityIds = new Set(abilities.keys());
skills.forEach(validateSkill);
abilities.forEach((definition) => validateAbility(definition, skillIds, abilityIds));
sources.forEach((definition) => validateExperienceSource(definition, skillIds, abilityIds));

const exampleSkills = await readDefinitions(path.join(exampleRoot, "skills"));
const exampleAbilities = await readDefinitions(path.join(exampleRoot, "abilities"));
const exampleSources = await readDefinitions(path.join(exampleRoot, "experience_sources"));
const effectiveSkillIds = new Set([...skillIds, ...exampleSkills.keys()]);
exampleSkills.forEach((definition) => {
  assert(skillIds.has(definition.id), `example skill must override a built-in ID: ${definition.id}`);
  validateSkill(definition);
});
exampleAbilities.forEach((definition) => {
  assert(abilityIds.has(definition.id), `example ability must override a built-in ID: ${definition.id}`);
  validateAbility(definition, effectiveSkillIds, abilityIds);
});
exampleSources.forEach((definition) => {
  assert(sources.has(definition.id), `example experience source must override a built-in ID: ${definition.id}`);
  validateExperienceSource(definition, effectiveSkillIds, abilityIds);
});

console.log(`Validated ${skills.size} skills, ${abilities.size} abilities, ${sources.size} experience sources, and ${exampleSkills.size + exampleAbilities.size + exampleSources.size} override examples.`);
