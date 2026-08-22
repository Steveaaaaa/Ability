# Ability 数据驱动架构（草案 v1）

目标版本：Minecraft 1.21.1 / NeoForge 21.1.x
临时模组命名空间：`ability`（确定正式 mod id 后统一替换）

## 1. 设计原则

1. 服务端是经验、等级、技能点、能力解锁和能力效果的唯一权威。
2. 技能、能力和经验来源由数据包定义，支持 `/reload` 和整合包覆盖。
3. Java 只定义可执行的机制类型，例如属性修改、伤害处理、战利品替换、主动施法。
4. JSON 只负责组合机制并提供数值，不引入通用脚本语言。
5. 玩家存档只保存不可从数据定义可靠推导的状态；能力当前阶级由定义和玩家进度实时推导。
6. 所有跨模组目标尽量使用物品、方块、实体和伤害类型标签，而不是写死对象 ID。

## 2. 三类数据包注册表

建议使用 NeoForge 自定义 datapack registry，并为客户端提供精简或相同的网络 Codec：

| 注册表 | 文件路径 | 用途 |
|---|---|---|
| `ability:skills` | `data/<namespace>/ability/skills/<id>.json` | 技能分类、经验曲线、显示信息 |
| `ability:abilities` | `data/<namespace>/ability/abilities/<id>.json` | 能力购买条件、阶级数值、执行类型 |
| `ability:experience_sources` | `data/<namespace>/ability/experience_sources/<id>.json` | 行为与经验奖励的映射 |

同 ID 文件遵循数据包优先级覆盖，整合包可以替换内置定义；新增命名空间可以直接追加内容。

## 3. 技能定义

每个技能包含：

- 显示翻译键、图标、颜色和排序值。
- 最高等级。
- `xp_to_next`：从 0 级开始，每一级升到下一级所需经验；数组长度必须等于最高等级。
- `skill_points_per_level`：每次真实升级授予的技能点。

玩家保存累计经验 `total_xp`。当前等级通过经验曲线计算。数据包修改曲线后，显示等级会重新计算；已经发放的技能点不回收，避免更新整合包后出现负数技能点。技能点按技能 ID 分别记录；玩家提升哪个技能，就只能使用该技能发放的点数购买其所属能力。

## 4. 能力定义

能力由以下部分组成：

- `skill`：所属技能 ID。
- `purchase`：首次购买所需所属技能等级、技能点和额外前置条件。
- `ranks.unlock_skill_levels`：每一阶实际生效所需的所属技能等级。
- `ranks.values`：每阶参数，键必须使用语义名称，例如 `damage_multiplier`，不使用 `X/Y/Z`。
- `effect.type`：Java 注册的能力类型 ID。
- `effect.config`：不随阶级变化的机制配置，例如目标选择器、物品条件和冷却策略。

能力是否“已购买”与是否“当前可生效”分开判断。若整合包提高前置等级，玩家仍保留购买记录，但条件不满足时能力暂停生效。

### 前置条件

首版支持以下条件类型：

- `ability:skill_level`：另一技能达到指定等级。
- `ability:ability_purchased`：已购买指定能力。
- `ability:advancement`：完成进度。
- `ability:all_of` / `ability:any_of` / `ability:not`：条件组合。

条件解析器使用按 `type` 分派的 Java 注册表，附属模组可增加新的条件类型。

## 5. 经验来源

经验来源将游戏事件映射到技能经验：

- `trigger.type` 指向 Java 注册的触发器类型，例如 `break_block`、`kill_entity`、`harvest_crop`。
- `base_xp` 是基础经验。
- `trigger.config` 描述标签、工具、实体类别、成熟度等筛选条件。
- `conditions` 描述维度、游戏模式或其他附加要求。
- `anti_abuse` 描述是否拒绝玩家放置方块、同目标冷却、每日软上限等防刷策略。

触发器只收集标准化上下文，统一经验管线按以下顺序处理：

`事件 -> 匹配经验来源 -> 防刷检查 -> 条件检查 -> 倍率修正 -> 发放经验 -> 升级与技能点 -> 同步客户端`

## 6. Java 能力类型边界

以下内容适合数据驱动：

- 解锁等级、技能点成本、跨技能前置。
- 每阶倍率、范围、概率、持续时间、冷却和物品/实体标签。
- 多个已注册效果的组合。
- 经验来源、基础经验、倍率与防刷参数。

以下内容必须由 Java 实现并注册类型：

- 新输入方式：蓄力跳跃、双击跳跃、闪避按键等。
- 新事件语义：破绽标记、免死、眩晕、召唤物强化状态。
- 改写方块破坏、伤害结算、掉落或实体 AI 的流程。
- 需要网络包、持续状态机或高频缓存的机制。

建议的首批能力类型：

- `ability:attribute_modifier`
- `ability:damage_modifier`
- `ability:damage_reduction`
- `ability:loot_injection`
- `ability:effect_aura`
- `ability:active_action`
- `ability:entity_upgrade`
- `ability:composite`

当前已实现方块掉落效果注册表，并以 `ability:associated_ore` 作为第一种效果类型。效果的固定目标由
`effect.config` 定义，各阶概率由 `ranks.values` 定义；稀疏的高阶参数会覆盖并继承先前阶级，避免升级后
丢失较低阶已经解锁的效果。

复杂技能可以有专用类型，例如 `ability:weak_point`、`ability:charged_leap`，但仍从 JSON 读取所有可平衡参数。

## 7. 玩家持久化数据

使用玩家实体 Data Attachment，并通过 Codec 持久化：

```json
{
  "data_version": 2,
  "skills": {
    "ability:mining": {
      "total_xp": 1250,
      "granted_skill_points": 12,
      "spent_skill_points": 6
    }
  },
  "purchased_abilities": ["ability:associated_ore"],
  "legacy_unassigned_skill_points": 0
}
```

版本 1 存档中的全局剩余技能点会迁移到一次性的 `legacy_unassigned_skill_points`。购买能力时优先消耗这部分旧余额；新获得的技能点不会进入该余额，因此旧余额耗尽后所有点数都严格按技能隔离。

运行时派生而不持久化：当前等级、能力阶级、属性修饰结果、可用技能点。短冷却默认只存在内存中；跨下线冷却才保存绝对游戏时间。

死亡是否保留全部 RPG 进度作为服务端配置，默认保留。1.21.1 的 Attachment 不会自动同步到客户端，因此登录、重生、数据重载和进度变化时发送自定义 payload。

## 8. 校验与重载

服务器应用重载前必须完成全量校验：

- ID、翻译键和引用对象合法。
- `xp_to_next` 长度等于 `max_level`。
- `unlock_skill_levels` 严格递增，长度等于 `values` 长度。
- 能力所属技能和所有前置引用存在。
- Java 中已注册对应的 effect、condition 和 trigger 类型。
- 参数满足具体类型的 Codec 范围要求。
- 前置图不存在循环依赖。

任一文件失败时记录文件 ID 和字段路径；不能悄悄使用部分默认值。开发环境提供 `/ability validate` 输出完整诊断。

## 9. 推荐代码模块

```text
data/       Codec、datapack registry、引用解析和校验
progress/   玩家 Attachment、经验管线、升级和技能点
ability/    能力类型注册、条件、阶级解析和生命周期
trigger/    挖掘、击杀、种植等事件适配器
network/    定义与玩家进度同步
command/    调试、校验、经验与等级管理命令
client/     只读客户端缓存；GUI 后续接入
```

## 10. 暂不在 v1 数据模型中实现

- 任意数学表达式或脚本。
- 允许数据包直接订阅任意 Java/NeoForge 事件。
- 运行时由客户端决定经验或伤害结果。
- 用显示文本解析逻辑；翻译文本只负责展示。
