# QinhRuins 生态对接

QinhRuins（QR）是**地基/编排插件**，本身不计算属性、不重造怪物系统。难度交给 MythicMobs，奖励是物品（属性归 QI/AttributePlus），货币走 QinhCoreLib 经济桥。本文说明如何把 QR 接进现有服务器生态。

---

## 1. 钥石（Keystone）—— 物品源对接

钥石已注册为 **QinhCoreLib 物品源**，任何走 CoreLib 物品引用的插件都能直接发钥石，无需 QR 专属代码。

引用格式：

```
qinhruins:<层数>
```

例如 `qinhruins:1`（T1 钥石）、`qinhruins:8`（T8 钥石）。层数范围由 `config.yml` 的 `realm.tiers.max` 决定。

### 获取途径示例

| 来源 | 怎么配 |
|------|--------|
| 管理员发放 | `/qr keystone give <层数> [玩家]` |
| 秘境净化掉落 | 自动：净化后掉同层钥石，`next-tier-chance` 概率掉更高层 |
| MythicMobs 掉落 | Drop 表里引用支持 CoreLib 物品源的掉落项 → `qinhruins:3` |
| 商店 / GUI | 任何用 CoreLib `ItemManagerAPI.getHookItem("qinhruins:1")` 的插件 |
| 任务奖励 | 同上，发 `qinhruins:<层数>` |
| QinhItems 配方 | 把 `qinhruins:1` 作为配方产物或材料 |

> 钥石外观可在 `realm.keystone` 配置：换材质、改名、或填 `ref` 指向一个 QI 物品引用覆盖外观。

---

## 2. 难度 —— 委托 MythicMobs

秘境刷怪走 MythicMobs：QR 只决定**刷哪个怪种、几只、什么等级**，怪的血量/伤害/技能全由 MM 自己缩放。

- 蓝图刷怪点的 `mob` 字段填 MM 怪 ID。
- 词缀 `mob-level-bonus` 加到传给 MM 的 level；`mob-count-mult` 让 QR 多刷几只。
- QR **不**给怪挂 buff、不算 stat —— 那是 MM 的活。

没装 MM 时自动降级为原版刷怪。

---

## 3. 奖励 —— 物品 + 经济桥

### 独占战利品
`loottables/realm.yml` 定义净化奖励，`item` 字段支持全部 CoreLib 物品源：

```yaml
entries:
  - item: "qinhitems:神血结晶"   # QI
  - item: "mm-传说碎片"          # MythicMobs 物品
  - item: "vanilla:DIAMOND"      # 原版
    unique: true                 # 受"秘藏"词缀加权
```

物品的属性是 QI/AttributePlus 的事，QR 只负责把物品发到玩家手里。

### 条件战利品（按成长度/占位符分级）

战利品表支持**条件分组**：在 `entries`（始终掉落）之外，加 `groups`，每组带一个占位符比较条件，满足才掉。可按玩家成长度、秘境层数等任意 PAPI 占位符分级配置奖励。所有满足条件的组都会贡献掉落（叠加）。

```yaml
rolls: 1
entries:                         # 基础掉落（无条件，始终生效）
  - { item: "vanilla:DIAMOND", weight: 10, amount: "1-3" }
groups:
  veteran:                       # 组名随意
    condition: "%qinhruins_realm_tier% >= 5"   # 占位符 + 比较符 + 值
    rolls: 2
    entries:
      - { item: "qi-神血结晶", weight: 5, amount: 1, unique: true }
  rich:
    condition: "%vault_eco_balance% >= 100000"
    rolls: 1
    entries:
      - { item: "qi-土豪专属", weight: 1, amount: 1 }
containers: [CHEST, BARREL]      # 可选：声明此表适用的容器类型（GUI 容器刷新阶段用）
```

比较符支持 `>= <= > < == !=`，左右两侧都先经 PlaceholderAPI 解析；两边都是数字则数值比较，否则字符串比较（仅 `==`/`!=`）。`condition` 留空 = 始终生效。

### 通关大奖 —— 老虎机抽奖

通关（击杀目标完成）后，**不掉落物品**（掉落交给 MythicMobs），而是给在场玩家一次**老虎机摇奖**资格：玩家 `/qr reward` 打开 GUI，点按钮摇奖，按其成长度从奖池中加权抽一件直接进背包。一次性、持久（重启不丢，摇过即消耗）。

- **奖池**：遗迹模板 `template.yml` 的 `reward.clear-table: <表名>`，复用条件战利品表——不同成长度 `groups` 条件不同 → 奖池和概率自然不同。
- 摇中由权重决定（`weight`），成长度 gate 由 `groups.condition` 决定。
- 无 `reward.clear-table` 的遗迹则没有通关大奖。

```yaml
# templates/<模板>/template.yml
reward:
  clear-table: boss_jackpot      # loottables/boss_jackpot.yml（条件分组=按成长度分奖池）
```

### 容器战利品（vessel）—— 每人独立、自取、保留神秘感

不用逐个标坐标：**在结构里摆原版容器**（箱子/木桶/潜影盒等）即自动生效。玩家右键容器，打开的是**属于他自己**的一份界面，里面按其成长度（含上面的条件分组）滚出战利品——**自取想要的，剩下的留在里面，之后不再变化**（保留开箱神秘感，不是直接塞背包）。

- **归属**：容器是否属于某遗迹，由**结构（原理图）的实际占地范围**自动判定（容器在遗迹结构 AABB 内即属于它），**不靠配半径**。
- **用哪张表**：遗迹模板 `template.yml` 里 `loot.container-table: <表名>`，表的 `containers` 字段声明哪些容器类型生效。
- **刷新**：`config.yml` 的 `vessel.refresh-mode` —— `per-player-once`（每人仅一次）或 `timed`（冷却后重滚）。
- 与**标记宝箱**共存：标记宝箱=精确指定位置+专属表（如 BOSS 室）；容器战利品=随便摆箱子批量自动。

```yaml
# templates/<模板>/template.yml
loot:
  container-table: dungeon_loot   # loottables/dungeon_loot.yml
```
```yaml
# loottables/dungeon_loot.yml
containers: [CHEST, BARREL]
entries:
  - { item: "vanilla:GOLD_INGOT", weight: 10, amount: "1-4" }
groups:
  deep:
    condition: "%qinhruins_realm_tier% >= 8"
    rolls: 2
    entries: [ { item: "qi-秘宝", weight: 3, unique: true } ]
```

### 货币
"鎏金"词缀的货币奖励、`/qr reroll` 的重铸花费都走 **QinhCoreLib 经济桥**，自动适配 Vault / ExcellentEconomy / PlayerPoints：

```yaml
realm:
  reward:
    currency-base: "tier * 100"
    currency-provider: ""    # 留空=auto，或 vault / ee / playerpoints
    currency-id: ""          # ExcellentEconomy 必填 money/silver/gold
```

---

## 4. 成长度 / 队伍 —— 委托 QinhClass / MMOCore

- 战利品的 `min-growth` 门槛、数量缩放读玩家**成长度**：自动探测 QinhClass → MMOCore → 原版等级兜底（`engines.growth`）。
- 组队进入读**队伍**来源：QinhClass / MMOCore / 内置队伍兜底（`engines.party`）。

---

## 5. 占位符（PlaceholderAPI）

计分板 / BossBar / 其它插件可读秘境状态：

| 占位符 | 含义 |
|--------|------|
| `%qinhruins_realm_active%` | 玩家是否在秘境内（true/false） |
| `%qinhruins_realm_tier%` | 当前秘境层数 |
| `%qinhruins_realm_name%` | 秘境名称 |
| `%qinhruins_realm_affixes%` | 词缀名（空格分隔） |
| `%qinhruins_realm_affix_count%` | 词缀数量 |
| `%qinhruins_realm_mobs%` | 存活怪数 |
| `%qinhruins_realm_total%` | 总怪数 |
| `%qinhruins_realm_time%` | 剩余时间（m:ss，无限时显示 ∞） |

遗迹探索（非秘境）另有 `%qinhruins_stage%`、`%qinhruins_objective%`、`%qinhruins_progress%` 等。

---

## 6. 可编程机关（Mechanisms）

在模板的 `blueprint.yml` 加 `mechanisms:` 段，用「触发器 → 动作」编排遗迹内的交互装置（开门、伏击、陷阱、阶段奖励）。坐标都相对锚点原点。纯世界交互，不碰属性/怪物 stat。

### 触发器（trigger.type）
| 类型 | 说明 | 关键字段 |
|------|------|----------|
| `interact` | 右键某方块 | `x/y/z`（按钮/拉杆/任意方块坐标） |
| `block-break` | 破坏某方块（挖穿裂墙） | `x/y/z` |
| `redstone` | 方块被红石充能（上升沿） | `x/y/z`（红石线/元件所在格） |
| `region-enter` | 玩家踏入立体区域 | `from{x,y,z}` / `to{x,y,z}` |
| `timer` | 区域内有玩家时每 N 秒 | `interval`（秒） |
| `stage` | 击杀目标推进到某阶段 | `stage`（阶段号） |

### 动作（action.type）
| 类型 | 说明 | 关键参数 |
|------|------|----------|
| `fill` | 把区域方块替换为材质 | `material` + `from`/`to`（开门=填 AIR） |
| `spawn` | 刷怪（走 MythicMobs） | `mob` `count` `level` + `x/y/z` |
| `message` | 给半径内玩家发消息 | `text` |
| `title` | 标题/副标题 | `title` `subtitle` |
| `sound` | 播放音效 | `sound` `volume` `pitch` |
| `effect` | 原版药水效果 | `effect` `level` `seconds` |
| `command` | 执行命令（`{player}` 占位） | `command` `as`(console/player) |
| `loot` | 掉落战利品表 | `table` + `x/y/z` |
| `teleport` | 传送（陷阱/密室） | `x/y/z` `yaw` `pitch` `target`(trigger/all) |
| `particle` | 粒子特效 | `particle` `count` `spread` + `x/y/z` |
| `give` | 给生态物品进背包 | `item`(CoreLib 引用) `amount` |
| `npc` | 生成 Citizens NPC（向导/商人/剧情角色，软依赖 Citizens） | `name` `skin`(可选玩家名皮肤) `yaw` + `x/y/z` |

> `npc` 动作需服务器装 **Citizens**，否则该动作静默跳过。同一锚点同名 NPC 只生成一次（防区域反复触发刷出一堆），`/qr remove` 锚点时自动清除本会话生成的 NPC。建议给 `npc` 动作配 `once`。叙事用法：`region` 触发器 + `npc` 动作 = 玩家进遗迹门口就出现一个向导。

### 公共字段
`once`（仅触发一次，**持久化**：重启后不会重新武装，`/qr remove` 锚点才清除记录）、`cooldown`（冷却秒）、`require-stage`（达到该阶段才生效）、`radius`（消息/音效/效果/粒子的作用半径）。

### 用编辑器创作（无需手写 YAML）

机关的难点是坐标，编辑器用「左键记录坐标点 → 命令引用这些点」的流：

```
/qr editor start <模板>            # 进入编辑（需附近有该模板的参考遗迹）
/qr editor mech new gate           # 新建机关，切到 MECH 模式
# —— 左键拉杆方块（记录点#1）
/qr editor mech trigger interact   # 用最后记录的点作交互方块
# —— 左键门的两个对角（记录点#2、#3）
/qr editor mech action fill material=AIR region   # region=用最后两个点
/qr editor mech action message text=&a石门开启
/qr editor mech flag once true
/qr editor mech done               # 提交到蓝图
/qr editor save                    # 写回 blueprint.yml 并生效
```

动作坐标来源：`region`/`pos`（用记录的点）或直接打 `fx/fy/fz/tx/ty/tz`、`x/y/z`。`message`/`title`/`command` 等文本参数可含空格（`text=...` 之后到下一个 `key=` 为止）。编辑器会**保留**你手写的机关，不会覆盖。

### 直接写 YAML

```yaml
mechanisms:
  # 踏入祭坛区域 → 伏击 + 警告
  - id: ambush
    once: true
    radius: 24
    trigger:
      type: region-enter
      from: { x: -2, y: 0, z: -2 }
      to:   { x: 2, y: 3, z: 2 }
    actions:
      - type: title
        title: "&c陷阱触发！"
        subtitle: "&7守卫从暗处涌出"
      - type: spawn
        mob: SkeletalKnight
        count: 4
        level: 10
        x: 0
        y: 1
        z: 0
      - type: sound
        sound: ENTITY_WITHER_SPAWN

  # 右键拉杆 → 开门（填 AIR）
  - id: gate
    once: true
    trigger:
      type: interact
      x: 3
      y: 1
      z: 0
    actions:
      - type: fill
        material: AIR
        from: { x: 2, y: 1, z: 6 }
        to:   { x: 4, y: 3, z: 6 }
      - type: message
        text: "&a石门缓缓开启……"

  # 通关阶段 2 → 给奖励命令
  - id: stage2-reward
    trigger:
      type: stage
      stage: 2
    actions:
      - type: command
        command: "give {player} diamond 3"
```

---

## 7. 程序化结构生成（瓦片拼装 · S3 实验中）

把多个小结构（瓦片 tile）按连接点自动拼成一座多房间遗迹，每次布局不同。结构留在世界里（**不是副本/instance**，只是更大的可探索遗迹）。

> 当前为 Phase 1：仅 `StructureRotation.NONE`（不旋转瓦片），瓦片需在各个朝向都手标连接点才能分支。旋转支持在后续阶段加入。

### 概念
- **瓦片** = 一个已保存的遗迹模板（`/qr save`），尺寸自动从 `.nbt` 读取。
- **连接点（connector）** = 瓦片某个朝向边上的门洞开口，相对瓦片原点的坐标 + 朝向（north=-Z / south=+Z / east=+X / west=-X）。
- 规划器从起始瓦片出发，在开放连接点上接朝向相反的瓦片，AABB 防重叠，直到达到 `max-rooms` 或无处可接。

### 调色板文件 `palettes/<id>.yml`

```yaml
max-rooms: 12
start: hub          # 起始瓦片模板名（留空=按权重随机）
tiles:
  - template: hub          # 对应 templates/hub/（已 /qr save 的结构）
    weight: 1
    max-count: 1           # 整座结构里最多出现几次
    role: start
    connectors:
      - { side: north, x: 3, y: 0, z: 0 }   # 门洞在北面，瓦片内坐标(3,0,0)
      - { side: east,  x: 6, y: 0, z: 3 }
  - template: corridor-ns  # 南北走廊（两端都有连接点才能贯通）
    weight: 3
    max-count: 20
    connectors:
      - { side: north, x: 1, y: 0, z: 0 }
      - { side: south, x: 1, y: 0, z: 4 }
  - template: room-cap     # 死胡同房间（只有一个连接点封口）
    weight: 2
    max-count: 20
    connectors:
      - { side: south, x: 2, y: 0, z: 5 }
```

### 试算与生成

```
/qr genstruct <调色板> dry     # 只打印布局（瓦片+坐标），不改世界 —— 先验证坐标
/qr genstruct <调色板>          # 在脚下生成（随机 seed）
/qr genstruct <调色板> 12345    # 用固定 seed 生成（可复现）
```

建议先在平坦测试世界 `dry` 看布局，再实际生成。

### 固定 or 程序化——服主自选（统一模型）

生成方式**不是做死的**：每个遗迹模板自己决定走固定单结构还是程序化拼装，只差 `template.yml` 里一行：

```yaml
# templates/<模板>/template.yml
structure:
  file: structure.nbt   # 固定遗迹：用这个单结构
  # palette: dungeon     # ← 加上这行 = 程序化：忽略 file，改用调色板拼装
```

- **不写 `palette`** → 固定样式遗迹（粘贴单个 `.nbt`，现有行为不变）。
- **写了 `palette`** → 程序化遗迹：整座多瓦片结构注册成**一个锚点**，继承全部生命周期——指引、刷怪/宝箱/机关（相对锚点的西北底角）、`/qr remove` 快照还原、自然生成（`generation` 段照常控制群系/概率）。

也就是说固定遗迹和程序化遗迹在同一套模型下共存，服主按需逐个遗迹选择。`/qr genstruct ... dry` 仍可独立试算任意调色板。

---

## 8. 运维 / 诊断

### 配置自动迁移
插件每次启动会比对你的 `config.yml` 与内置默认：**缺失的新配置项自动补上**（旧服更新插件不必手动加新键），已有的值与注释**原样保留**不覆盖。`config-version` 字段由插件维护，请勿手改。

### 性能统计 `/qr profile`
压测/排查卡顿用。统计结构粘贴与快照（生成时备份地形 / `/qr remove` 还原）的**主线程耗时**：
```
/qr profile          # 看各项 次数/均值/峰值/累计 毫秒
/qr profile reset    # 清零，便于针对某次压测单独取样
```
- `paste.place` 偏高 → 大结构单发卡顿，考虑调小 `generation.spread-place-threshold` 让它分帧铺。
- `snapshot.capture` / `snapshot.restore` 峰值高 → 大结构快照贵，调小 `cleanup.max-snapshot-volume`（超体积跳过快照）或关 `cleanup.snapshot-restore`。

### 导入 `.schem` 的保真
`/qr import` 解析 Sponge `.schem` 时会保留**告示牌文字**与**刷怪笼的怪物类型**（`SpawnData`）。容器内容（箱子里的物品）**故意不保留**——遗迹的箱子交给战利品表（vessel / 标记宝箱）动态生成。旗帜花纹、头颅皮肤等 NBT 不保留（底色/方块本身仍在）。

### 告示牌标记的怪物等级
扫描结构内 `[mob]` 标记牌时，数量行可写 `数量*等级`（如 `5*10` = 5 只 10 级），等级喂给 MythicMobs 做 stat 缩放（普通原版怪忽略等级）。

## 架构红线

QR **不碰属性、不碰怪物 stat**。难度→MM，奖励→物品（属性归 QI/AP），货币→经济桥，玩家削弱→原版药水+规则。任何"真·数值属性"需求都委托给对应插件，QR 只编排，绝不重造轮子。
