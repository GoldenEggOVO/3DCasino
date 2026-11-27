# 3DCasino

[English](README.md) | [简体中文](README.zh-CN.md)

包含十二种实体娱乐场游戏的 Minecraft 插件，提供机台动画、玩家私有操作与原生 Dialog 菜单。

**3DCasino 1.0.0** 使用 Java 25。下列服务端共用同一个 JAR。

| 服务端 | 已实测的 Minecraft 版本 |
| --- | --- |
| Paper | 26.1.1、26.1.2、26.2、26.3 |
| Purpur | 26.1.2、26.2、26.3 |
| Spigot／CraftBukkit | 26.1、26.1.1、26.1.2、26.2、26.3 |
| Folia | 26.1.2、26.2、26.3 |

每种组合均验证了十二种机台、四个摆放朝向、Dialog 输入和重启恢复。Folia 还验证了玩家往返不同区域的机台，并检查扣款和入账在负责该玩家的线程上执行。

## 安装与游玩

1. 从 [1.0.0 发布页](https://github.com/GoldenEggOVO/3DCasino/releases/tag/v1.0.0) 下载插件 JAR 和可选资源包。
2. 干净停服，替换前备份插件数据。
3. 在 `plugins/` 保留一个着色后的 `3DCasino-1.0.0.jar`，不要安装 `original-*.jar`。
4. 启动后运行 `/3dcasino`。管理员可在游戏菜单或通过 `/3dcasino create <game>` 放置机台。

玩家直接操作机台上的按钮与控件，潜行＋右键打开机台设置。所有者和管理员可以拆除空闲机台。金额与偏好按玩家分别保存，放置一台机器不会给所有人设置同一金额。

插件可以独立运行，不需要客户端模组或额外 Core 插件。CraftEngine 和资源包是可选项；只有正金额玩法需要 Vault 和可用经济提供者。没有资源包时采用原版 Display 模型；加载后显示对应机台素材，菜单使用原生 Dialog 文字按钮。资源包开关位于**设置**。在子页面点击说明下方的黄色 **[返回]** 回到上一级，按 Esc 直接关闭菜单。


## 游戏、命令与权限

| 游戏 | 标识 |
| --- | --- |
| 弹珠 | `plinko` |
| 扫雷 | `mines` |
| 21 点 | `blackjack` |
| 爆点 | `crash` |
| 拉霸 | `slots` |
| 赛鸭 | `duck_race` |
| 幸运轮盘 | `wheel_of_fortune` |
| 金钱轮盘 | `money_wheel` |
| 企鹅过河 | `penguin_cross` |
| 基诺 | `keno` |
| HiLo | `hilo` |
| 龙塔 | `dragon_tower` |

| 玩家命令 | 功能 |
| --- | --- |
| `/3dcasino [menu]` | 打开游戏目录 |
| `/3dcasino settings` | 打开设置，包含资源包开关 |
| `/3dcasino create <game> [skin-id]` | 放置机台；仅管理员 |
| `/3dcasino remove [game]` | 拆除自己的空闲机台；管理员可管理附近机台 |
| `/3dcasino reload-models` | 重载自定义机台定义；仅管理员 |
| `/3dcasino reload` | 重载配置与翻译；仅管理员 |
| `/3dcasino reload-pack` | 重建可编辑资源；仅管理员 |

`3dcasino.use` 默认授予所有人，`3dcasino.admin` 默认授予管理员。命令需要玩家执行；渲染模式、资源来源和集成变更需要重启。本插件不提供 PlaceholderAPI 扩展。

## 配置与数据

配置位于 `plugins/3DCasino/config.yml`：

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `language` | `en_US` | `languages/` 中的语言目录，内置 `zh_CN` |
| `menu-enabled` | `true` | 启用 Dialog 菜单 |
| `rendering.mode` | `auto` | CraftEngine 启用时使用 `mixed`，否则 `vanilla`；也可明确指定 `vanilla`、`mixed`、`resource-pack` |
| `rendering.resource-pack.source` | `craftengine` | CraftEngine 统一包；也可选择 `local`、`external` |
| `rendering.resource-pack.url` | 空 | 外部托管时与当前 JAR 匹配的 ZIP 地址 |
| `rendering.resource-pack.local.bind-address` | `0.0.0.0` | 独立 HTTP 监听地址 |
| `rendering.resource-pack.local.port` | `8138` | 独立 HTTP 端口 |
| `rendering.resource-pack.local.public-url` | 空 | 可选公开地址；留空时使用玩家连接主机名 |
| `economy.enabled` | `false` | 通过 Vault 启用正金额玩法 |
| `economy.stakes.minimum`／`maximum` | `1.0`／`1000.0` | 正金额总投入范围，包含 21 点加倍 |

新的个人金额为 **0**。经济提供者可用时，玩家从机台设置选择金额。有效开局或投出弹珠前先扣款；返还包含本金，并按货币精度向下取整。关闭经济系统会把正金额偏好归零；重新开启后，玩家需要主动设置正金额。

退出、死亡、离开世界、机台卸载或重启时，未完成回合按放弃处理。已确定的收取和弹珠结果在动画结束前记录。明确被拒绝的付款会定期重试；Vault 没有幂等交易编号，结果不明的记录保留给管理员核对，不自动重复付款。服务运行时不要编辑或丢弃账本。

| `plugins/3DCasino/` 下的路径 | 内容 |
| --- | --- |
| `casino/placements.json` | 机台位置与保存的定义 |
| `casino/players/` | 个人偏好和结算摘要 |
| `casino/payments.json` | 持久化资金日记账 |
| `casino/machines/` | 可选自定义 YAML 机台定义 |
| `languages/` | 面向玩家的翻译 |
| `resource-pack/`、`resource-pack.zip` | 可编辑资源输入与生成的插件包 |

编辑生成的翻译时保留键名与 `{placeholders}`，然后运行 `/3dcasino reload`。标准中英文目录缺失的条目使用内置对应语言，其他语言回退到英文。重载成功后，已打开的赌场菜单会关闭，玩家再次打开即可看到新语言。翻译校验失败时，现有菜单和语言继续生效。

## 资源维护

本插件命名空间是 `3dcasino`。JAR 自动向 `plugins/CraftEngine/resources/3dcasino/` 安装默认注册，保留已有条目并补充缺少的默认项。Casino 与桌游资源分开，共用扑克牌素材在各自交付中自包含。CraftEngine 模式使用完整统一包及其现有下载服务。

升级时，插件会为旧版默认模型补齐粒子纹理，并更新旧版默认资源包的版本范围。自定义过的文件会保留。日常改图可直接编辑服务端生成的 `resource-pack/assets/3dcasino/`，然后执行 `/3dcasino reload-pack`。无效内容保留上一份可用包。请等当前构建完成再启动下一次 CraftEngine 资源包生成。机台在玩家加载成功且模型就绪后切换到资源包外观。每个插件只移除自己发出的资源包请求。

使用本地或外部托管时，JAR 自带的模型可以独立工作。没有安装 CraftEngine 的服务器可将 `rendering.mode` 设为 `mixed` 或 `resource-pack`。独立 `source: local` 需要开放 8138，或反向代理 `/3dcasino/`。`source: external` 使用与 JAR 匹配的 ZIP 并配置 URL，摘要已内置；更换资源来源或监听设置后重启。

客户端模型位于 `resource-pack/assets/3dcasino/models/item/`，同级的 `items/` 存放物品定义，`textures/` 存放图片。专用素材按 `casino/<game>/` 分类，共用扑克位于 `shared/playing_cards/`。默认注册在 `resource-pack/craftengine/`；`originals/`、`licenses/` 与 `sources.json` 保存源图和署名，原稿不打入客户端 ZIP。原版几何在 `src/main/resources/vanilla-models/casino/`，索引为 `vanilla-models/index.json`。修改带签名的原版扑克图案还需重新生成纹理并编译，普通资源重载不会替换头颅皮肤。

## 从集成版离线切换

先在完整备份上进行本地验证，再使用候选构建更新服务器。插件不会自动迁移数据，也没有执行线上部署。

1. 干净停服，备份世界、`plugins/3DGames/`、已有的 `plugins/3DCasino/` 以及自定义 CraftEngine 和资源目录。
2. 将 `plugins/3DGames/casino/` **完整复制**到 `plugins/3DCasino/casino/`。如果目标已有数据，先明确哪份是有效数据，不要直接覆盖。
3. 在复制的机台存档／自定义定义中，人工定向修改 `definition.models` 与 `parts` 内属于 Casino 的 `3dgames:` 自有资源引用，使其对应 `3dcasino:`。不要替换其他作者的命名空间；付款记录、金额、交易状态、UUID、所有权和坐标均保持原样。
4. 自定义 Casino CraftEngine 注册、材质／模型路径和资源包 URL 同步采用新命名空间，默认注册由各自 JAR 安装；两个插件分别使用自己的配置与资源目录。
5. 在备份副本检查机台恢复、个人偏好、未结款、两套命令、资源切换和各自占用限制，再切换服务器。保留原始数据和备份，不让两个插件同时写同一账本，不删除旧目录。

## 源码与构建

```text
3DCasino/
├── src/             Java、默认配置、翻译、原版模型与测试
├── resource-pack/   客户端素材、CraftEngine 注册与原稿
├── tools/           打包、头颅素材工具与检查
├── pom.xml          Maven 配置
└── LICENSE          代码许可
```

Java 源码位于 `src/main/java/GoldenEggOVO/TDCasino/`。`TDCasinoPlugin.java` 装配插件；`game/` 包含十二种规则与机台，`machine/` 管理占用、定义与持久化，`render/` 管几何与显示，`menu/` 管 Dialog 页面，`economy/` 管付款日记账，`Core/platform/` 处理不同服务端的任务调度、消息与 Dialog，`Core/pack/` 管理资源包交付，`Core/render/` 维护玩家可见范围和实体移动。`command/`、`integration/`、`interaction/`、`text/` 各自负责对应边界。测试沿用相同目录，不依赖外部 Core 服务。

需要 JDK 25、Maven 3.9+、Python 3.12+：

```sh
mvn -B -ntp clean
python tools/package_resource_pack.py
mvn -B -ntp package
python -m unittest discover -s tools/tests -t . -p "test_*.py"
python tools/package_source.py
```

先打资源包再运行 Maven，使 JAR 包含匹配 ZIP 与摘要。产物为 `target/3DCasino-1.0.0.jar`、`target/3DCasino-resource-pack.zip`、资源清单和 `target/3DCasino-source.zip`；Java 报告在 `target/surefire-reports/`。头颅图案工具可能需要 Pillow，运行时不需要 MineSkin 密钥。自动检查不代表客户端画面、听感或 FPS 已验收。

准备好本地服务端构建后，运行 `python tools/tests/server/run_compat_matrix.py --help` 查看跨平台检查用法。双玩家资源包测试位于 `tools/tests/server/pack_probe/run_pack_layers.py`。两者均在本机回环地址使用临时世界。模型检查工具 `tools/tests/client/CasinoModelProbe.java` 使用待测版本的官方客户端库读取模型。

## 许可与署名

插件代码使用 [GPL-3.0-or-later](LICENSE)。规则、几何与控制器改编自 GoldenEggOVO/3DCasinoGames 0.6.0-beta.5-SNAPSHOT；文字工具来自该项目 `a4ddd20` 修订版，遵循 GPL-3.0。交付保留对应源码和声明。Gson 以 **Apache-2.0** 许可内置并重定位；Adventure 与 Kyori Option 以 MIT 许可随包提供，JOML 由服务端提供，Vault API 可选且不打入 JAR。新 Casino 不使用桌游的国际象棋、象棋、五子棋和中国跳棋规则库。

资源包牌面与蓝色牌背采用 George Blackwell 的 [Pixel Playing Cards Pack](https://georgeblackwell.itch.io/playing-cards-sprite-pack)，许可为 **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**。原始 60×84 图像位于 `resource-pack/originals/shared/textures/playing-cards/`；衍生牌面保留宽高比、采用最近邻放大并映射到机台卡牌几何。分发时须保留作者署名、许可链接和修改说明。

原版扑克含 Minecraft 26.2 客户端的僵尸、骷髅和苦力怕面部像素，纹理归 **Mojang／Microsoft** 所有，与 GPL 代码分别处理。卡牌布局、花色与凋零风格像素图案由本项目制作。带签名皮肤属性内置，API Key 和生成缓存不交付。来源、摘要和素材声明随资源源文件及 `resource-pack/licenses/` 保存。

发牌音效使用 Kenney 的 [Casino Audio 1.1](https://kenney.nl/assets/casino-audio)，按 **[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)** 分发。原始 OGG、摘要和许可证随项目保留；`3dcasino:cards.deal` 独立打包已有的单声道加工样本，仅对已成功加载资源包的玩家播放，其余玩家使用原版翻页声。本插件无需其他桌游插件提供音频，也不包含其通用出牌录音或麻将语音。
