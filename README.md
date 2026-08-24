# Ability

一个面向 Minecraft 1.21.1 / NeoForge 的 RPG 能力模组。

玩家通过挖掘、战斗、农耕、采集等行为积累对应技能经验并提升等级，再使用技能点解锁或强化能力。项目优先采用数据驱动设计，方便整合包作者调整经验来源、解锁条件、能力等级和数值参数。

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
- `/ability purchase <ability>`：消耗所属分类技能点购买能力或提升一阶；首次使用表格价格，后续阶级使用数据定义的逐阶价格。
- `/ability rank <ability>`：查看能力的购买、生效和当前阶级状态。
- `/ability validate`：检查技能、能力、经验来源、配置类型、引用与前置依赖环，需要权限等级 2。
- `/ability presentation_preview <ability> <cue>`：在自己位置预览客户端表现定义，需要权限等级 2；内置测试为 `ability:debug ability:test`。

例如：`/ability add_xp @s ability:mining 100`。

## 目标版本

- Minecraft 1.21.1
- NeoForge 21.1.x
- Player Animation Library 1.1.5+（必需前置）
