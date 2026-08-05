# MusicBox (Folia)

> 一个支持 **Folia** 的 Minecraft 音乐插件 —— 通过音符盒播放 `.nbs` 歌曲。
> 本仓库是 [Spliterash/MusicBox](https://github.com/Spliterash/MusicBox) 的 **Folia 适配分支**，并附带**简体中文**完整翻译。

A Minecraft music plugin that plays `.nbs` note-block songs — **ported to run on Folia** (regionized multithreading), while staying compatible with classic Paper. Ships with a full **Simplified Chinese** translation.

---

## 这个分支做了什么 · What's in this fork

- ✅ **Folia 支持**：全面迁移到 Folia 的区域化调度器（Region / Async / Global / Entity Scheduler），在 Folia 26.x 上稳定运行，同时向下兼容普通 Paper。
- ✅ **简体中文**：内置完整的 `zh.yml` 中文语言文件（含指令帮助、GUI、播放列表、告示牌、商店等全部文本），开箱即用。
- ✅ **日历版本适配**：修复了对 MC 日历版本（26.x）的识别（`NMSUtils.parseMajorVersion`）。
- ✅ **唱片机纯 API 实现（V26_2）**：无需 paperweight / NMS dev bundle，即可在 26.x 上「放入唱片但不触发原版播放」。
- ✅ **依赖升级**：XSeries 升级到 13.7.1（正确解析 26.x 版本号）；编译目标 Java 21、Paper API 1.21.1。

English: full Folia region-scheduler port, event-driven listener tracking (replaces the old 100 ms async polling), Simplified Chinese language pack, calendar-version (26.x) support, a pure-API jukebox implementation for 26.x, and dependency updates.

## 功能 · Features

- 🎵 **告示牌播放器**：写下 `[music]` 的告示牌，用红石控制播放/切歌
- 💿 **唱片机播放**：把唱片放进唱片机播放（不触发原版音乐，由插件发声）
- 🔊 **音响模式**：让周围的玩家听到你放的歌
- 📻 **个人电台**：只给自己听，不打扰别人
- 📝 **播放列表**：创建、编辑、随机播放
- 🛒 **唱片商店**：配合 Vault 经济购买唱片
- 🎶 **自定义唱片上传**：玩家通过网页上传自己的 .nbs 歌曲，做成专属唱片（见下方说明）
- 🌏 **多语言**：英文 / 俄文 / 简体中文（`config.yml` 里 `lang` 切换）

## 自定义唱片上传 · Custom Disc Upload

玩家可以把自制的 `.nbs` 音乐上传为专属唱片，在服务器里播放：

1. 背包携带**任意一张唱片** + **100 金币**（Vault），执行 `/musicbox upload <名称>`（名称支持颜色码）
2. 扣费后获得一条可点击的**上传链接**（30 分钟内有效）
3. 打开网页，拖拽上传 `.nbs` 文件（单个最大 5 MB，每人最多 5 张）
4. 上传成功后歌曲自动注册进曲库，可放入唱片机播放、加入播放列表
5. `/musicbox mydiscs` 查看自己的唱片列表，每行有聊天按钮：
   - **`[给予]`**：花费 50 金币重新获得唱片实物
   - **`[删除]`**：二次点击确认后删除（同时清理文件）

`config.yml` 的上传配置段：

```yaml
upload:
  enabled: true          # 是否启用上传服务
  host: 0.0.0.0          # Web 服务器监听地址
  port: 8518             # Web 服务器端口（需防火墙放行）
  displayUrl: http://127.0.0.1:8518   # 展示给玩家的链接（换成公网 IP/域名）
  maxFileSize: 5242880   # 单个 .nbs 最大字节数（5 MB）
  maxDiscs: 5            # 每名玩家最多自定义唱片数
  uploadPrice: 100       # 创建上传插槽价格（金币）
  givePrice: 50          # 重新获得唱片价格（金币）
  tokenExpireMinutes: 30 # 上传链接有效分钟数（0 = 永久）
```

> 上传服务基于 JDK 内置 `com.sun.net.httpserver`，无需额外依赖；数据存于 `plugins/MusicBox/custom/<玩家UUID>/` 目录与 SQLite（`custom_discs` / `pending_uploads` 表）。

## 运行环境 · Requirements

| 组件 | 版本 |
|------|------|
| 服务端 | Folia 26.x（或 Paper 1.21+） |
| Java | 21+ |
| 前置 | [NoteBlockAPI (Folia)](https://github.com/tiancuocuo/NoteBlockAPI-Folia) |
| 可选 | Vault（唱片商店经济） |

## 安装 · Installation

1. 把 `NoteBlockAPI`（Folia 版）和 `MusicBox` 两个 jar 放进 `plugins/`。
2. 启动服务器。中文界面：`plugins/MusicBox/config.yml` 中设置 `lang: zh`（新装默认为 `zh`）。

## 构建 · Build

```bash
# 先构建并安装前置 NoteBlockAPI（见对应仓库）
mvn -f NoteBlockAPI clean install

# 再构建本插件（默认完整多版本构建，含历史 NMS 模块）
gradlew shadowJar
```

产物在 `build/libs/MusicBox-*-all.jar`。

> 只需 26.x 或想避开 paperweight 的完整构建？见 [FOLIA-PORT.md](FOLIA-PORT.md) 中的「26.x 精简构建」，几秒钟即可出包。

关于本次 Folia 移植的技术细节（调度模型、事件区域化、线程安全等），也见 [FOLIA-PORT.md](FOLIA-PORT.md)。

## 许可与归属 · Credits & License

- 原作：[**Spliterash/MusicBox**](https://github.com/Spliterash/MusicBox)（[Spigot 页面](https://www.spigotmc.org/resources/musicbox-music-on-discs.67949/)）
- 前置 API：[NoteBlockAPI](https://www.spigotmc.org/resources/noteblockapi.19287/)（LGPL v3）
- 本分支仅做 Folia 适配与汉化，版权归原作者所有；请遵循原作与 NoteBlockAPI 的许可证。
