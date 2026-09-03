# 参与 FantasyPower 开发

感谢你愿意花时间反馈或改进 FantasyPower。提交内容前，请先确认本地代码基于仓库的最新版本。

## 报告问题

先搜索一下现有 Issue，避免同一个问题被重复记录。新 Issue 最好包含：

- FantasyPower、NeoForge 和 Player Animation Library 的版本
- 单人游戏还是专用服务器
- 可以稳定复现问题的步骤
- `latest.log`、崩溃报告或截图

日志里可能包含服务器地址、用户名或本机路径，上传前可以先检查并隐去不想公开的信息。

## 提交改动

1. 用 Java 21 构建项目。
2. 尽量让一次提交只处理一件事，方便检查和回退。
3. 修改 Java 代码后运行 `./gradlew test build`。
4. 修改能力、技能或经验来源 JSON 后，再运行 `node tools/validate-examples.mjs`。
5. 在 Pull Request 中说明改了什么、为什么要改，以及你实际做过哪些测试。

FantasyPower 允许数据包覆盖内置内容，但不支持用数据包新增能力。如果改动涉及数据格式，请同时更新 Schema、示例和相关文档。

新增贴图、模型、声音或动画前，请说明素材来源和授权方式。不要提交来源不明、未经许可或由其他模组直接提取的资源。
