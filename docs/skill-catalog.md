# Excel 技能目录映射（临时 ID）

这些 ID 用于后续 JSON 与 Java，不影响最终中文显示名。正式 mod id 确定后替换 `ability` 命名空间。

| 技能分类 | 技能 ID | 能力名称与临时 ID |
|---|---|---|
| 畜牧 | `ability:husbandry` | 援护 `support_aura`、精饲 `fine_feed`、狼群 `wolf_pack`、铁骑 `iron_cavalry` |
| 挖掘 | `ability:mining` | 爆破掘进 `blast_excavation`、贪婪 `greed`、伴生矿 `associated_ore` |
| 采集 | `ability:gathering` | 嗅探奇物 `sniffer_treasure`、招财猫 `lucky_cat`、沙里淘金 `gravel_panning` |
| 攻击 | `ability:combat` | 直取要害 `weak_point`、迅刺 `rapid_thrust`、攻其不备 `ambush`、跃斩 `charged_leap` |
| 防御 | `ability:defense` | 同态复仇 `retaliatory_flame`、准备万全 `well_prepared`、求生技巧 `survival_skills`、生还者 `survivor` |
| 建造 | `ability:building` | 紫颂果嬗变术 `chorus_transmutation`、威亚 `ceiling_wire`、环球旅行者/铁砧修补（待确认）`world_traveler` |
| 农耕 | `ability:farming` | 精力充沛 `energetic`、俭省 `frugality`、收割 `harvest` |
| 敏捷 | `ability:agility` | 长旅 `long_journey`、潜匿 `stealth`、闪避 `dodge`、突破 `breakthrough` |
| 魔法 | `ability:magic` | 压碎/重锤（待确认）`crushing_blow`、缠魔剑锋 `enchanted_edge`、黑曜石加固 `obsidian_reinforcement`、寒流 `cold_current` |
| 射击 | `ability:archery` | 危险装药 `dangerous_charge`、反狙击 `counter_sniper`、底火 `primer`、衰竭 `exhaustion` |

## 待确认项

1. 建造页“环球旅行者”与“其他说明”的“铁砧修补”可能不是同一技能。
2. 魔法页“压碎”与“其他说明”的“重锤”命名不一致。
3. “突破”未列入“其他说明”的汇总表。

## 已接入运行时

- 挖掘：伴生矿 `associated_ore`。
- 采集：沙里淘金 `gravel_panning`；使用通用掉落替换和按阶级解锁的带权池。
- 攻击：迅刺 `rapid_thrust`；使用通用属性修饰器同时调整攻击速度和攻击力。
- 攻击：攻其不备 `ambush`；目标尚未锁定战斗目标时使用通用伤害倍率。
- 攻击：直取要害 `weak_point`；独立叠加破绽标记，阈值攻击增伤、眩晕并消耗饥饿值。
- 攻击：跃斩 `charged_leap`；蓄力跳跃后以空中攻击触发范围伤害、眩晕和一次落地保护，第六阶解锁二段跳。
- 防御：生还者 `survivor`；使用 `ability:undead` 实体类型标签筛选伤害来源，整合包可扩展该标签。
- 防御：求生技巧 `survival_skills`；每秒解除剩余时间严格小于阶级阈值的有害状态效果。
- 防御：同态复仇 `retaliatory_flame`；身处火焰环境时点燃范围内敌人，灵魂火焰与岩浆额外造成周期火焰伤害。
- 农耕：精力充沛 `energetic`；食物与饱和度总量超过阶级阈值时续加速度 II、跳跃提升 I。
- 农耕：收割 `harvest`；使用锄近战攻击时按能力阶级及当前食物状态增加伤害，并在有效命中后增加消耗度。
- 农耕：俭省 `frugality`；降低自然回血以及“收割”“直取要害”的饥饿消耗。
- 敏捷：长旅 `long_journey`；Y 坐标位于阶级化区间之外时续加速度 I。
- 敏捷：突破 `breakthrough`；以组合效果同时实现高生命目标固定增伤与低生命承伤速度增益。
- 敏捷：潜匿 `stealth`；静默且未承伤/攻击达到阶级时间后隐身，下一次有效伤害增幅并消耗标记。
- 敏捷：闪避 `dodge`；疾跑键与后退键配合左/右移动触发服务端侧移，并在短窗口内减伤。
- 射击：反狙击 `counter_sniper`；远距离实际承伤标记攻击者，下一次有效伤害增幅并消耗标记。
- 射击：危险装药 `dangerous_charge`；弩射烟花爆炸伤害按阶级提高 60%–90%。
- 射击：底火 `primer`；副手火焰弹右键蓄力后发射火球，并按阶级强化爆炸伤害。
- 射击：衰竭 `exhaustion`；瞬间伤害箭命中非亡灵目标后立即结算并清除其中毒与凋零效果。

“生还者”表格中的六阶数值按“每超过解锁等级 2 级”推算会在 32 级结束，超过当前防御技能 30 级
上限。内置数据保留六阶数值，将最后两阶安排在 29、30 级解锁，避免最终阶永远无法生效。

“突破”从敏捷 22 级开始共有十阶，按每级强化推算至 31 级。为保留表格中的全部十阶数值，敏捷技能
上限由 30 调整为 31，并沿用现有经验曲线增量补充第 31 级所需的 2880 经验。

“直取要害”同样从攻击 22 级开始共有十阶，攻击技能上限相应由 30 调整为 31，第 31 级经验需求
同样按现有曲线补为 2880。

“跃斩”从攻击 18 级开始每超过解锁等级 2 级强化一次。表格明确标注当前配置最多强化至 7/10 级，
并说明 8–10 阶蓝色字段在不修改配置时不可达到；因此内置定义只录入攻击 18–30 级可达的七阶
140%–205% 伤害与 1–2.6 秒眩晕，不擅自开放不可达阶级。

“反狙击”表格只给出了 1–5 阶倍率（120%、130%、140%、145%、150%），但说明文字写有最高十阶。
内置数据暂时只实现有明确数值的五阶，避免推测缺失的 6–10 阶倍率。

“衰竭”从射击 18 级开始每 2 级强化一次。当前射击技能上限为 30，因此内置定义录入可达的七阶，
中毒/凋零支持等级上限依次为 1/1、1/2、2/2、2/3、3/3、3/4、4/4；表格中的 8–10 阶继续保留为
调整技能等级配置后的扩展目标，不在当前配置中提前开放。
