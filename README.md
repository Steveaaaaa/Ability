# Ability

一个面向 Minecraft 1.21.1 / NeoForge 的 RPG 能力模组。

玩家通过挖掘、战斗、农耕、采集等行为积累对应技能经验并提升等级，再使用技能点解锁或强化能力。项目优先采用数据驱动设计，方便整合包作者调整经验来源、解锁条件、能力等级和数值参数。

## 当前进度

- 已整理 10 个技能分类和 36 个能力设计。
- 已完成技能、能力和经验来源的数据模型草案。
- 已提供 JSON Schema、示例数据和轻量校验脚本。
- NeoForge 工程与 Java 运行时代码尚待搭建。

## 目录

```text
docs/       架构说明和技能 ID 映射
schemas/    数据格式 JSON Schema
examples/   数据包示例
tools/      开发期校验工具
```

## 校验示例

需要 Node.js 18 或更高版本：

```bash
node tools/validate-examples.mjs
```

## 目标版本

- Minecraft 1.21.1
- NeoForge 21.1.x

