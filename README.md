# BTV

<p align="center">    
  <img src="icon.svg" alt="BTV logo" width="96" />    
</p>

<p align="center">    
  <b>哔哩哔哩（B 站）第三方 Android TV 客户端</b>    
</p>

<p align="center">    
  <a href="./LICENSE">MIT License</a> ·    
  <a href="./CHANGELOG.md">修改日志</a> ·    
  <a href="./docs/PRD.md">PRD</a> ·    
  <a href="./AGENTS.md">开发规范</a>    
</p>

---

> **English**  
> BTV is a third-party **Bilibili (B站) Android TV client**, rebuilt on the architecture of [BV](https://github.com/aaa1115910/bv). It replaces BV's multi-Activity shell with a single-Activity + Jetpack Compose (Navigation) architecture, swaps Koin for Hilt, keeps the Media3 player (VOD + Live), and adds live streaming, comments, touch + D-pad input, theme switching and a local-first crash/diagnostics system. See the Chinese section for details.

---

## 简介

BTV 是基于 [BV](https://github.com/aaa1115910/bv) 重构的哔哩哔哩第三方 Android TV 客户端（前身 newBV），在保留 BV 播放与弹幕体验的基础上重构架构、增强功能。

**相对原版 BV 的主要变化**

- 架构：多 Activity → 单 Activity + Navigation-Compose，根治焦点与状态重建问题
- 依赖注入：Koin → Hilt
- 播放器：Media3，兼容 VOD + Live（不接入 VLC）
- 移除：所有代理功能、Firebase Crashlytics
- 新增：完整直播、评论、触屏 + 遥控器双模、黑夜 / 白天主题、本地诊断日志
- 作者的话：全程AI许愿式编程，动效和性能优化尽力了。电影的会员档画质（1080P+ / 4K）缺失，采集不到。求大手子优化

---

## 功能特性

- **首页**：推荐、热门、动态。
- **搜索**：关键字搜索与结果浏览。
- **详情页**：视频 / 影视（PGC）详情，影视含首页横幅（Banner）。
- **播放器**：点播（VOD），Animeko TV 风格控制条与弹窗；弹幕、字幕、选集、画质 / 线路切换。
- **互动视频**：支持 Stein Gate 分支选择播放。
- **付费 / 版权拦截**：按官方口径拦截付费与版权受限视频。
- **直播**：完整直播观看，HDR 档位按解码能力过滤，起播降档 / 线路切换提示。
- **评论**：详情页与播放器内评论，楼中楼预览、相对时间、长文展开。
- **账号**：二维码登录、多账户；历史、收藏、稍后再看、追番。
- **设置**：主题、播放、画质、日志查看器等。
- **本地诊断日志**：崩溃 / 手动日志经 Ktor 网页端查看，无需云端。
- **内置更新**：检查更新、下载、安装一体（含安装权限处理）。

---

## 界面预览

> 截图位于 `docs/`，以下为各主要界面。

| 界面               | 截图       |
| ---------------- | -------- |
| 主页（推荐 / 热门 / 动态） | 主页       |
| 影视界面与横幅          | 影视界面与横幅  |
| 影视视频详情页          | 影视视频详情页  |
| 播放界面与控件          | 播放界面与控件  |
| 直播播放界面           | 直播播放界面   |
| 互动视频支持           | 互动视频支持   |
| 单片付费视频拦截         | 单片付费视频拦截 |

---

## 技术栈

| 项       | 版本                                                              |
| ------- | --------------------------------------------------------------- |
| 语言 / 构建 | Kotlin 2.4.10 / Java 17 · AGP 9.1.1 / Gradle 9.3.1 / KSP 2.3.10 |
| UI      | Jetpack Compose（BOM 2025.06）、Material3、TV Material              |
| 导航 / DI | Navigation-Compose（类型安全）· Hilt                                  |
| 播放器     | Media3 / ExoPlayer 1.8.0（VOD + Live）                            |
| 网络 / 存储 | Ktor 3.1.3 + gRPC-kotlin · Room 2.7.1 / DataStore 1.1.7         |
| 弹幕 / 图片 | akdanmaku 1.0.4（Frost819 fork）· Coil 3.x                        |
| 平台      | minSdk 21 / compileSdk 36 / targetSdk 36                        |

---

## 架构

单 Activity + Navigation-Compose，分层 `UI → ViewModel → Repository → 数据 / 网络`，统一 Hilt 注入。

```
app/           主应用：UI、ViewModel、Navigation
core/          主题、交互模式、日志基础设施、工具
data/          Room、DataStore、Repository 接口
bili-api/      B 站 HTTP 接口封装（已删除代理）
bili-api-grpc/ Protobuf 定义
bili-subtitle/ 字幕解析
player/        播放器引擎抽象 + Media3 实现
danmaku/       弹幕渲染封装
danmaku-engine/ 弹幕引擎
```

---

## 构建

```bash
# 1. 克隆
git clone https://github.com/hamenot/BTV.git
cd BTV

# 2. 配置（local.properties 不提交 git）
cp local.properties.template local.properties

# 3. 构建 Debug
./gradlew assembleDebug
```

- 环境：JDK 17（`JAVA_HOME` 指向 JDK 17）、Android SDK（compileSdk 36）。
- 接口集成测试需 B 站账号凭证，配置于 `local.properties`，详见 [AGENTS.md](./AGENTS.md)。

---

## 文档

| 文档                                                 | 说明        |
| -------------------------------------------------- | --------- |
| [docs/PRD.md](./docs/PRD.md)                       | 产品需求规格    |
| [docs/开发计划.md](./docs/开发计划.md)                     | 阶段任务与验收   |
| [AGENTS.md](./AGENTS.md)                           | 开发规范      |
| [CHANGELOG.md](./CHANGELOG.md)                     | 修改日志与日志规范 |
| [.github/release-notes/](./.github/release-notes/) | 各版本发版摘要   |

---

## 许可证与免责声明

- 基于 [BV](https://github.com/aaa1115910/bv) 重构，遵循 **MIT License**，详见 [LICENSE](./LICENSE)。
- **免责声明**：BTV 为个人学习 / 研究用途的第三方客户端，**不支持在中国大陆地区内使用**；如有相关需求请使用哔哩哔哩官方客户端。本项目与哔哩哔哩官方无任何关联。

---

<p align="center">Made with ❤️ for Android TV · BTV</p>
