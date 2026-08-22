# Ability

一个面向 Minecraft 1.21.1 / NeoForge 的 RPG 能力模组。

玩家通过挖掘、战斗、农耕、采集等行为积累对应技能经验并提升等级，再使用技能点解锁或强化能力。项目优先采用数据驱动设计，方便整合包作者调整经验来源、解锁条件、能力等级和数值参数。

## 当前进度

- 已整理 10 个技能分类和 36 个能力设计。
- 已搭建 Minecraft 1.21.1 / NeoForge 21.1.248 工程。
- 已实现技能、能力和经验来源三个数据包注册表及其 Codec。
- 已实现玩家技能经验、等级、分技能技能点和已购能力的持久化附件。
- 已实现通用经验结算服务，并提供进度查询与经验调试命令。
- 已接入挖矿、击杀敌对生物和收获成熟作物的经验事件，并提供放置方块过滤、目标冷却与每日软上限。
- 已实现数据驱动的方块掉落效果运行时，并完整接入“伴生矿”五阶能力。
- 已提供 JSON Schema、内置示例数据和轻量校验脚本。

## 目录

```text
src/        NeoForge 运行时代码和内置资源
docs/       架构说明和技能 ID 映射
schemas/    数据格式 JSON Schema
examples/   独立数据包示例
tools/      开发期校验工具
```

## 构建

需要 Java 21：

```bash
./gradlew build
```

构建产物位于 `build/libs/ability-0.1.0.jar`。

## 数据校验

需要 Node.js 18 或更高版本：

```bash
node tools/validate-examples.mjs
```

## 开发期命令

- `/ability progress <skill>`：查看自己的技能等级与累计经验。
- `/ability add_xp <targets> <skill> <amount>`：为目标玩家添加技能经验，需要权限等级 2。
- `/ability purchase <ability>`：消耗技能点购买满足条件的能力。
- `/ability rank <ability>`：查看能力的购买、生效和当前阶级状态。

例如：`/ability add_xp @s ability:mining 100`。

## 目标版本

- Minecraft 1.21.1
- NeoForge 21.1.x
