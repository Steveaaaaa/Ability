<p align="center">
  <img src="art/fantasypower-curseforge-logo.png" width="180" alt="FantasyPower logo">
</p>

# FantasyPower

FantasyPower 是一个运行在 Minecraft 1.21.1 / NeoForge 上的 RPG 能力模组。

挖矿、战斗、种田或探索都会带来对应分类的经验。升级后得到的技能点可以拿来学习新能力，也可以继续强化已经掌握的能力。十个分类各有自己的成长路线，技能点互不通用，所以不必把所有玩法都塞进同一套等级里。

模组目前提供 10 个技能分类和 36 项能力，包括主动技能、被动加成、战利品变化和傀儡强化。能力界面可以直接查看说明、等级要求和技能点消耗；如果还有条件没满足，把鼠标停在购买按钮上就能看到完整列表。

## 安装

客户端和服务端需要安装相同版本的以下内容：

| 依赖 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Player Animation Library | 1.1.5 或更高 |
| FantasyPower | 0.1.0 |

把模组 JAR 放进游戏实例的 `mods/` 文件夹即可。单人游戏只需要安装在本地；进入服务器时，客户端和服务端都要安装。

## 配置

客户端配置在 `config/fantasypower-client.toml`。其中可以把“跃斩”的蓄力方式从独立按键改为空格键；默认按键是 `V`，也可以在游戏的按键设置中重新绑定。

服务端配置按存档保存，文件位于：

```text
saves/<存档>/serverconfig/fantasypower-server.toml
```

专用服务器对应 `<世界目录>/serverconfig/fantasypower-server.toml`。这里可以调整全局经验倍率，以及玩家死亡后是否保留：

- 各分类的经验和技能点记录
- 已学习能力及其阶级
- 每日经验获取计数
- 每日一次能力的使用状态
- “环球旅行者”的容器绑定和过滤列表

这些选项默认都是保留，与模组原本的行为一致。

## 调整内置数据

FantasyPower 的等级曲线、经验来源、购买条件和能力数值放在数据包中。整合包可以用相同 ID 覆盖内置定义，例如修改某项能力的冷却、范围、概率或跨分类等级要求。

这里的数据驱动指的是“调整 FantasyPower 已有内容”，不是用 JSON 编写全新的能力。额外创建的能力定义不会进入能力界面和运行时。

格式说明见 [数据结构](docs/data-driven-architecture.md)，内置 ID 可查阅 [技能与能力目录](docs/skill-catalog.md)。`examples/datapack/` 中还有一份可直接加载的覆盖示例。

## 从源码构建

需要 Java 21。克隆仓库后运行：

```bash
./gradlew build
```

Windows 可以使用：

```powershell
.\gradlew.bat build
```

生成的 JAR 位于 `build/libs/`。

修改数据后，可以用 Node.js 18+ 运行仓库自带的检查：

```bash
node tools/validate-examples.mjs
```

进入游戏后，管理员还可以执行 `/fantasypower validate` 检查服务器实际加载的内置数据。

## 常用命令

- `/fantasypower progress <skill>`：查看自己的分类等级、经验和技能点。
- `/fantasypower add_xp <targets> <skill> <amount>`：给玩家增加指定分类的经验。
- `/fantasypower purchase <ability>`：购买或升级能力。
- `/fantasypower rank <ability>`：查看能力的购买阶级与当前生效状态。
- `/fantasypower validate`：检查内置定义、引用和前置依赖。

管理类命令需要权限等级 2。完整的视觉表现开发说明放在 [表现层文档](docs/presentation-layer.md)。

## 反馈问题

遇到崩溃、能力没有生效或界面异常时，请在 [Issues](https://github.com/Steveaaaaa/Ability/issues) 中提交反馈，并附上 FantasyPower、NeoForge 和 Player Animation Library 的版本。若游戏生成了 `latest.log` 或崩溃报告，也请一并上传。

## 许可证

代码和构建工具使用 [MPL-2.0](LICENSE)。Schema 与覆盖示例使用 MIT；原创贴图、模型、动画等美术资源保留所有权利，但可以随未经修改的官方 FantasyPower JAR 一同放入整合包。细则见 [LICENSES.md](LICENSES.md)。
