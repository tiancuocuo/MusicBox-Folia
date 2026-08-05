# MusicBox + NoteBlockAPI Folia 26.2 移植 · 构建与验证

两个仓库已改造为可在 **Folia 26.2**（日历版本 "Chaos Cubed"）上运行，同时保留在普通 Paper 上运行的能力。本机（Hana 环境）没有 JDK，无法替你编译，所以按下面步骤在你自己机器上构建。

---

## 一、构建前提

- **JDK 21**（两个项目都用 Java 21 编译；你的 Folia 26.2 跑 Java 25 也能正常运行 Java 21 字节码）
- **Maven 3.6+**（构建 NoteBlockAPI）
- MusicBox 自带 Gradle Wrapper（`gradlew`），无需单独装 Gradle
- 能访问 `repo.papermc.io`、`repo1.maven.org`（下载依赖）

## 二、构建 NoteBlockAPI（先构建，MusicBox 依赖它）

```bat
cd /d D:\LX\NoteBlockAPI
mvn -q clean install
```

- `install` 会把 `com.xxmicloxx:NoteBlockAPI:1.7.0-SNAPSHOT` 装进本地 Maven 仓库，MusicBox 的 Gradle 通过 `mavenLocal()` 引用它。
- 产物在 `target\NoteBlockAPI-1.7.0-SNAPSHOT.jar`（已 shade bstats），**这个 jar 就是要放进 plugins 的 NoteBlockAPI**。

## 三、构建 MusicBox

```bat
cd /d D:\LX\MusicBox
gradlew.bat clean shadowJar
```

- 产物在 `build\libs\` 下（shadowJar 合并了 plugin + nms 各模块，并 relocate 了 XSeries/bstats/nbteditor）。
- `plugin.yml` 由 `net.minecrell.plugin-yml` 自动生成，已含 `folia-supported: true` 与 `api-version: 1.21`。

### 可选：26.2 专用精简构建（更快、更稳，推荐）

完整构建会让 paperweight 下载约 10 个历史版本（1.19.2~1.21.3）的 dev bundle，慢且可能因网络失败。**由于 26.2 的唱片机实现 V26_2 是纯 Bukkit API（在 plugin 模块里，不依赖任何 reobf 模块）**，那些历史 NMS 模块在 26.2 上根本用不到，可以直接裁掉：

1. `settings.gradle`：只保留
   ```
   include "plugin"
   include "nms:shared"
   include "nms"
   ```
   （删掉所有 `include "nms:versions:..."`）
2. 根 `build.gradle.kts` 的 `dependencies {}`：只保留
   ```kotlin
   api(project(":plugin"))
   api(project(":nms"))
   api(project(":nms:shared"))
   ```
   （删掉所有 `api(project(":nms:versions:..."))`）

这样构建不再需要 paperweight，几秒钟就能出包，且功能在 26.2 上完全等价。以后想支持旧版本再还原即可。

## 四、部署

把两个 jar 放进 Folia 26.2 的 `plugins\`：
- `NoteBlockAPI-1.7.0-SNAPSHOT.jar`
- MusicBox 的 shadowJar 产物

MusicBox 的 `plugin.yml` 里 `depend: [NoteBlockAPI]`，会保证 NoteBlockAPI 先加载。

## 五、首次启动验证清单

1. 两个插件都正常 enable，控制台无 `UnsupportedOperationException`、无 region 线程报错。
2. 放一块牌子写 `[music]` 或用 `/musicbox` 打开选歌 GUI，播放一首，确认：
   - 声音正常、随距离衰减；
   - 走开/回来能听到范围变化（听者范围改为事件驱动）。
3. 唱片机插唱片播放（V26_2 纯 API 实现：放入唱片但不触发原版播放，由 NoteBlockAPI 发声）。
4. 喇叭（speaker）模式播放 + 自动切下一首，确认 bossbar 与切歌正常。

### 首要观察项：XSeries 版本解析

XSeries 被刻意留在 **11.0.0**（12.x 起 XMaterial/XSound 重构成不兼容的新 API）。11.0.0 发布早于日历版本制度，**存在把 26.2 误判为旧版本的风险**。典型症状：

- 选歌 GUI 里的唱片/图标变成空气或缺失；
- 控制台出现 `XMaterial` / `parseItem` 相关的 `NullPointerException`。

一旦出现，把症状发我，我把 XMaterial/XSound 的用法迁移到 XSeries 新版 API 即可（范围明确：约 20 处 XMaterial + 1 处 XSound）。

## 六、已知限制（不影响主流程）

- **喇叭（EntitySongPlayer）跨 region**：`playTick` 会同时读听者和喇叭持有者的位置。两者距离很近（喇叭半径内），Folia 几乎总在同 region，安全；region 边界的极端情况属理论残留。
- **防销毁牌子的区块强加载**：Folia 上无法安全地在区块卸载处理器里强留区块，故 `preventDestroy` 的牌子在区块卸载后改为「保留注册、区域任务暂停」，区块重新加载后自动恢复播放，而不是阻止卸载。
- **EntitySongPlayer 的范围事件**：`PlayerRangeStateChangeEvent` 在各自听者 region 线程触发，MusicBox 未监听它，无影响。

---

## 附：本次移植改了什么（技术摘要）

**NoteBlockAPI**
- 新增 `utils/FoliaCompat`：一套跨 Paper/Folia 的调度桥（region/async/global/entity 调度 + Folia 检测）。
- `SongPlayer`（新/旧两个包）：播放线程从共享异步池改为**可追踪的专用线程**（onDisable 可中断）；`playTick` 从主线程同步改为**按听者玩家的 EntityScheduler 派发**（在听者 region 线程执行，线程安全）。
- 事件派发按**歌曲所在区域**进行：`PositionSongPlayer` 用方块位置 region，`EntitySongPlayer` 用实体 region（新增 `getEventLocation`/`fireEvent` 钩子）。这让 MusicBox 的「歌结束→切下一首→读写方块」链路落在正确 region。
- `onEnable/onDisable` 去掉 `cancelTasks/getActiveWorkers`（Folia 会抛异常），改为显式取消 + 中断播放线程。
- `pom.xml`：spigot-api 1.16 → **paper-api 1.21.1**，Java 8 → **21**；`plugin.yml` 加 `folia-supported: true`、`api-version: 1.21`。

**MusicBox**
- 新增 `utils/FoliaUtils`：调度桥。7 处 `Bukkit.getScheduler()` 全部迁移到区域正确的调度。
- **`RangePlayerModel` 重写为事件驱动**：原来 100ms 异步轮询所有玩家坐标（Folia 跨 region 违规），改为监听 Join/Quit/Move/WorldChange 在**各自玩家 region**更新听者集合；声源位置缓存（喇叭持有者的位置在其自身 region 刷新）；自动销毁改为声源 region 上的轻量周期任务。这既是适配也是优化（去掉了空转轮询）。
- `AbstractBlockPlayer`/`SpeakerPlayer` 的异步 while 循环移除，改为 region 周期任务。
- `BukkitUtils.runSyncTask` 增加**按位置/按玩家**的区域正确重载；`checkPrimary` 在 Folia 变为 no-op。
- 线程安全：`AbstractBlockPlayer.players` 与 `MusicBoxSongPlayerModel.all` 改为并发集合 + 快照迭代（Folia 多 region 并发）。
- 跨 region 消息/元数据：`PlayerWrapper.clearAll`、`SPControlGUI.openNext`、`ReloadExecutor`、`PlayListEditorGUI`、`MusicBox` 启动重载等改为按玩家 region 派发。
- **NMS 版本检测**：`NMSUtils.parseMajorVersion` 适配日历版本（"26.2" → 26，旧逻辑会得到 2）；`JukeboxFactory` 新增 26 分支并改为优雅降级。
- **V26_2 唱片机实现（纯 Bukkit API）**：`setRecord` + `stopPlaying` + `update` 在同一快照内完成，实现「放入唱片但不播放」，无需 paperweight dev bundle。
- 构建：plugin 模块升到 paper-api 1.21.1 + Java 21；根构建加 `foliaSupported = true`、`api-version 1.21`、PaperMC 仓库。
