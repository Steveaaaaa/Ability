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

通用被动属性效果 `ability:attribute_modifier` 也已实现。`effect.config.modifiers` 中的每一项声明
`attribute`、`operation` 和用于读取阶级数值的 `amount_key`；`ranks.values` 使用对应键提供数值。
支持 `add_value`、`add_multiplied_base` 和 `add_multiplied_total` 三种原版运算。高阶可以只覆盖发生
变化的键，其余数值继承前一阶。一项能力可同时修改多个属性，属性 ID 可指向其他模组注册的属性。
运行时使用确定 ID 的临时修饰符，每秒核对一次，并在购买、升级、重生、切换维度或数据包重载时
立即核对；能力失效、定义移除或目标属性改变时会撤销旧修饰符，不写入玩家持久化 NBT。

通用伤害效果分为攻击侧的 `ability:damage_modifier` 和受击侧的 `ability:damage_reduction`。
两者的 `effect.config` 都可用 `damage_type_tags`、`attacker_entity_type_tags`、
`target_entity_type_tags` 进行任一标签匹配，并用 `directness` 选择 `any`、`direct` 或 `indirect`。
攻击侧阶级值使用 `damage_multiplier` 与 `flat_damage`；受击侧使用 `damage_multiplier` 与
`flat_reduction`。倍率是最终乘数，例如 `1.2` 表示增伤 20%，`0.8` 表示减伤 20%。

同一次结算先汇总攻击者能力，再汇总受击玩家能力。每一侧的倍率相乘、固定值相加，计算顺序为：

`max(0, (原始伤害 × 攻击倍率乘积 + 固定增伤合计) × 受击倍率乘积 - 固定减免合计)`

伤害效果在护甲、附魔和药水减伤前的 `LivingIncomingDamageEvent` 阶段执行，因此修改后的伤害仍会
正常参与原版防御结算。高阶参数与属性效果一样支持稀疏覆盖。

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

死亡是否保留全部 RPG 进度作为服务端配置，默认保留。1.21.1 的 Attachment 不会自动同步到客户端，
因此使用版本化的 `ability:player_progress` payload 同步服务端快照。同步发生在经验变化、能力购买、
登录、重生、切换维度和数据包重载后；客户端只保存只读缓存，不参与等级、点数或能力状态结算。

同步快照包含所有已注册技能的累计经验、服务端计算等级、已发放和已消费技能点、旧版迁移余额，
以及已购买能力 ID。网络协议与快照结构分别设有版本号，集合长度和计数值在解码时受限并校验。
客户端界面可从生存或创造背包的附加页签进入；技能栏与能力区分别支持滚轮浏览。购买请求等待
服务端权威结果期间会锁定购买按钮，以避免重复请求；数据包重载后失效的选中技能会自动回退到
当前注册表中的第一项。

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

当前校验器会检查所属技能、条件内技能/能力/进度引用、等级上限、已注册的 condition/trigger/effect
类型及其 Codec 参数，并对 `ability_purchased` 前置图执行循环检测。命令默认显示前 20 条诊断，避免
大型数据包错误时刷屏，同时保留总错误数。服务器启动及数据包重载后也会自动执行同一套校验，并将
完整诊断写入服务端日志。

## 9. 推荐代码模块

```text
data/       Codec、datapack registry、引用解析和校验
progress/   玩家 Attachment、经验管线、升级和技能点
ability/    能力类型注册、条件、阶级解析和生命周期
trigger/    挖掘、击杀、种植等事件适配器
network/    定义与玩家进度同步
command/    调试、校验、经验与等级管理命令
client/     只读客户端缓存、背包页签和基础技能/能力交互界面
```

## 10. 暂不在 v1 数据模型中实现

- 任意数学表达式或脚本。
- 允许数据包直接订阅任意 Java/NeoForge 事件。
- 运行时由客户端决定经验或伤害结果。
- 用显示文本解析逻辑；翻译文本只负责展示。
