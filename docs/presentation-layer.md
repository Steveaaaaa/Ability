# 表现层底座

能力运行时只负责在服务端确认结果，然后通过 `AbilityPresentationService` 发送表现提示。提示包含能力 ID、提示 ID、START/PULSE/STOP、来源与目标实体、世界坐标、方向、能力阶级、持续实例 ID 和随机种子，不包含具体颜色、声音或素材路径。

客户端资源包在 `assets/<namespace>/ability_presentations/<ability_path>.json` 中定义表现。例如 `assets/ability/ability_presentations/dodge.json` 对应能力 `ability:dodge`。资源包可以覆盖同一路径，因此整合包能够替换表现而不修改服务端能力代码。

## JSON 格式

```json
{
  "cues": {
    "ability:activate": {
      "duration_ticks": 20,
      "emission_interval_ticks": 2,
      "particles": [
        {
          "type": "minecraft:end_rod",
          "anchor": "source",
          "count": 8,
          "offset": [0.0, 0.5, 0.0],
          "spread": [0.4, 0.6, 0.4],
          "velocity": [0.0, 0.02, 0.0],
          "direction_speed": 0.1,
          "force": false
        }
      ],
      "sound": {
        "event": "minecraft:block.note_block.pling",
        "anchor": "source",
        "volume": 0.8,
        "pitch": 1.0,
        "pitch_random": 0.1
      },
      "animation": "ability:example_animation"
    }
  }
}
```

服务端提示可以覆盖 `duration_ticks`，用于眩晕等实际持续时间由服务端数值决定的状态。重复发送同一实例的 START 会刷新结束时间而不重新播放开始声音或动画；STOP 会提前移除实例。

持续提示还支持跟随实体的环绕精灵：

```json
{
  "orbiting_sprites": [
    {
      "texture": "ability:textures/particle/stun_star.png",
      "anchor": "target",
      "minimum_count": 3,
      "maximum_count": 5,
      "count_bias": 2.0,
      "count_width_multiplier": 1.5,
      "minimum_radius": 0.32,
      "maximum_radius": 0.9,
      "radius_base": 0.2,
      "radius_width_multiplier": 0.3,
      "height_offset": 0.3,
      "size": 0.34,
      "rotations_per_second": 0.6,
      "bob_amplitude": 0.04,
      "bob_cycles_per_second": 1.2,
      "fade_ticks": 2,
      "full_bright": true
    }
  ]
}
```

数量和圆环半径按照实体碰撞箱宽度计算并限制在配置范围内。精灵始终朝向镜头，使用普通深度测试，因此会被方块遮挡；`full_bright` 只控制光照，不会让图像穿墙。

`anchor` 可为 `position`、`source` 或 `target`。实体不存在时自动回退到提示包携带的世界坐标。当前粒子预设接受所有已注册的 `SimpleParticleType`；需要方块碎屑、带颜色尘埃或自定义贴图粒子时，再按素材方案注册对应粒子类型和客户端工厂。声音字段引用已注册的 `SoundEvent`；自定义声音会在确定素材清单后统一注册。动画字段引用 Player Animation Library 动画资源。

PULSE 立即播放一次。START 会立即播放，并在有效持续时间内按 `emission_interval_ticks` 重复发射粒子；同一实例的 STOP 会提前结束。声音和动画只在开始时播放，避免持续提示每次发射都重新触发。

“跃斩”的蓄力视野变化直接读取本地蓄力进度，在保留玩家 FOV 设置的基础上最多平滑收窄 10%，释放或取消后快速恢复。服务端确认空中命中后发送 `ability:charged_leap / ability:impact` 提示；客户端以命中点下方最近地表为基准，只采集高度差不超过 1 格、没有流体、方块实体或树叶的暴露模型方块，渲染短暂抬起的完整假方块。实际世界方块、碰撞与掉落不变。冲击气浪使用由中心扩展至 5 格边界的地面粒子圆环，并对整套效果设置数量与距离上限。

“直取要害”在服务端每次增加破绽标记时发送持久 `ability:weak_point / ability:marks` 进度提示和一次 `mark_hit` 命中提示。客户端将 0–255 的归一化进度映射为最多五枚环绕刻痕；刻痕由带间隔的方形色块拼成，方向按 45° 分档，形成硬边锯齿像素风。进度越高，刻痕越接近赤金色，旋转越快且半径越小。达到阈值时服务端先停止持久提示，再发送 `trigger`；客户端让五枚刻痕向本次命中位置收束，并以红、金、近白三层像素十字锐光爆开。标记实例按攻击者、目标和标记 ID 隔离，避免多人攻击同一目标时互相覆盖。

## 开发预览

内置 `ability:debug / ability:test` 只用于确认管线工作，不代表正式能力美术。拥有权限等级 2 的玩家可执行：

```text
/ability presentation_preview ability:debug ability:test
```

修改资源包 JSON 后可使用 F3+T 重载并再次预览。正式接入某项能力时，能力逻辑调用表现服务发送提示，客户端资源再决定粒子、音效与动画组合。

## 能力界面

能力界面采用受《我的世界：地下城》启发的三栏信息层级：左侧技能分类、中央独立能力槽、右侧能力详情与购买操作。视觉底座由代码绘制深色石板、暖金双层边框、像素切角、稀疏石纹、锁定遮罩与菱形阶级格，因此在专用美术尚未完成时仍能保持一致风格。标题金线、经验条和当前选中顶点带有低频像素流光；已解锁能力按数据 ID 错峰出现短促金色像素闪烁，悬停只产生一像素抬升。中央石纹上有少量缓慢漂浮的暗金尘点，位置使用渲染帧 `partialTick` 进行亚像素插值，而非停留在 20 Hz 逻辑刻更新；能力槽之间不绘制连接线，不暗示任何前置顺序。

左侧技能分类使用紧凑的 24 像素高列表并始终显示滚动轨道；分类超出可见区域时，金色滑块按可见比例缩短并随滚轮移动。中央能力数量由注册表动态决定，按当前窗口宽高计算列数与页容量，不完整的末行会自动居中；超出容量时提供明确的上一页、下一页按钮，并继续支持滚轮整页浏览。添加或删除能力不需要调整 GUI 坐标。右侧能力说明、解锁条件和购买操作分区显示，并会在可用高度不足时自动切换紧凑排版，保证说明区至少保留一行有效裁剪空间；说明区拥有独立的逐行滚动位置和滚动条，超长解锁条件也会自动折行。背包界面的能力与环球旅行者入口使用相同的深色切角和暖金悬停描边。

技能和能力图标现在统一采用旋转 45° 的菱形槽位，并以
`assets/ability/textures/gui/dungeon_icon_frame.png` 像素边框直接作为按钮轮廓；旧的代码绘制褐色菱形边框不再叠加。所有状态下边框尺寸保持一致，选中项仅在原边框四个顶点内增加亮金标记。技能图标路径为
`assets/<namespace>/textures/gui/skill_icons/<path>.png`，能力图标路径为
`assets/<namespace>/textures/gui/ability_icons/<path>.png`。推荐提供 32×32 或更高的正方形像素素材，并把关键图案控制在画布中央约 70% 的安全区内；完整素材会与槽位一起旋转，边框始终在最上层统一显示。资源不存在时自动回退到定义中 `display.icon` 指向的正向物品图标，外侧仍使用同一菱形边框。替换图标不需要修改 Java 或数据定义。

如果素材本身已经是最终菱形朝向，可分别放入
`textures/gui/skill_icons/diamond/<path>.png` 或
`textures/gui/ability_icons/diamond/<path>.png`。该路径优先于普通方形素材，界面不会再次旋转图案，只叠加统一边框。

## 黑曜石加固美术资源

强化铁傀儡的完整侵蚀皮肤路径为
`assets/ability/textures/entity/iron_golem/obsidian_reinforcement.png`。护盾外壳的可替换透明纹理路径为
`assets/ability/textures/entity/iron_golem/obsidian_shield.png`，推荐使用 32×32 或 64×64 的像素纹理：主体保持大面积透明，只保留暗色像素纹理和少量高光。未提供护盾纹理时会自动使用原版黑曜石纹理作为后备，不影响护盾外壳、呼吸、分层轮廓、生成脉冲和受击波纹。
