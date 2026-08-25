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

“直取要害”在服务端每次增加破绽标记时发送持久 `ability:weak_point / ability:marks` 进度提示和一次 `mark_hit` 命中提示。客户端将 0–255 的归一化进度映射为最多五枚环绕刻痕；进度越高，刻痕越接近赤金色，旋转越快且半径越小。达到阈值时服务端先停止持久提示，再发送 `trigger`；客户端让五枚刻痕向本次命中位置收束，并以红、金、近白三层十字锐光爆开。标记实例按攻击者、目标和标记 ID 隔离，避免多人攻击同一目标时互相覆盖。

## 开发预览

内置 `ability:debug / ability:test` 只用于确认管线工作，不代表正式能力美术。拥有权限等级 2 的玩家可执行：

```text
/ability presentation_preview ability:debug ability:test
```

修改资源包 JSON 后可使用 F3+T 重载并再次预览。正式接入某项能力时，能力逻辑调用表现服务发送提示，客户端资源再决定粒子、音效与动画组合。
