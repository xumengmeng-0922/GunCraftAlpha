# GunCraft Alpha

MC 风格的 3D 方块游戏（Java），带 **PCL2 风格**启动器。当前版本 **Alpha 1.4**。

> **说明**：启动器界面布局与交互参考了 PCL2（Plain Craft Launcher 2）的常见设计；PCL2 为龙腾猫跃作品，**并非**使用其闭源代码，仅为视觉与流程上的借鉴。

## 需求

- JDK 17+
- Maven 3.6+（可选；游戏 zip 可用 `pack-game-zip.cmd` 无 Maven 打包）

## 构建

```bash
mvn clean package
```

生成：

- `launcher/target/guncraft-launcher-alpha-1.4.jar` + `launcher/target/lib/`（FlatLaf、Gson）
- `game/target/guncraft-game-alpha-1.4.jar` + `game/target/lib/`（LWJGL 等）
- 游戏分发 zip：双击 **`打包游戏zip.bat`** 或 **`pack-game-zip.cmd`**（**不需要 Maven**，只需 JDK 17+ 和网络），得到 `game/target/guncraft-game-alpha-1.4.zip` 与 `dist/` 下同名副本（内含 jar + `lib/`）

## 启动器（Alpha 1.4）

- **开始游戏**（仅 Windows）：游戏安装在 **`%APPDATA%\GunCraft\game\<版本 id>\`**。启动游戏后启动器保持打开；关闭游戏不会重复弹出启动器。
- **联网更新版本列表（无需每次重装启动器）**  
  配置好**远程清单 URL** 后，每次打开启动器都会在后台自动拉取最新 JSON；**发布新版本只需更新服务器上的清单**（及 GitHub Releases 里的 zip），玩家不用换启动器。  
  地址优先级：**设置里填的 URL** → 内嵌 `remoteManifestUrl` → 由 **`githubRepo`**（`owner/repo`）+ **`githubBranch`** 自动拼 Raw 地址 → `builtin-remote-manifest.url` 第一行 HTTPS。
- **版本与下载**（面向玩家：只认云端）：
  - **下载 / 更新**：每个版本可写完整 **`zipUrl`**，或只写 **`releaseTag` + `zipAssetName`**（根上配 **`githubRepo`**），启动器按 GitHub Releases 规则拼直链并下载解压。
  - **刷新版本列表**：手动再拉一次远程 `versions-manifest.json`。
  - **设为启动版本**：在「开始游戏」页使用所选版本。
- **设置**：一般只需把 `docs/versions-manifest.json` 推到仓库：写了 **`githubRepo`** 时多数情况**不必**再手填 Raw URL；需要 Gist/镜像时再在设置里覆盖。
- **开发者**：无云端包时，在「设置」最下方可用「从本机 game/target 导入」。

### 云端清单（维护者）

> **从零上 GitHub**：见 [`docs/GitHub发布指南.md`](docs/GitHub发布指南.md)，或双击根目录 **`GitHub首次发布.bat`**。

1. 将仓库推到 GitHub；若 Fork 或改名，改清单根上的 **`githubRepo`**（`owner/repo`）及 **`githubBranch`**（默认 `main`）。
2. **`docs/versions-manifest.json`**：每个版本填写 **`releaseTag`**（与 Release 标签一致）和 **`zipAssetName`**（与上传的附件名一致），或改用 **`zipUrl`** 任意 HTTPS 直链。
3. 在 **Releases** 上传对应 zip（示例：标签 **`game-alpha-1.4`**，文件 **`guncraft-game-alpha-1.4.zip`**）。
4. **`打包启动器.bat`** 分发给玩家即可。

### 打包发布用 zip（Releases 附件 / zipUrl 指向的文件）

zip **根目录**须包含（与 `game/target` 一致）：

```
guncraft-game-alpha-1.4.jar
lib/
  (所有依赖 .jar)
```

### 打包 Windows 启动器（发给同学）

**Windows** 下在项目根目录 **双击 `打包启动器.bat`** 即可（需 **完整 JDK 17+**，含 `jpackage`）：

- 自动从 Maven Central 下载 FlatLaf、Gson
- 用 `jpackage` 生成 `dist/GunCraftLauncher/`（内含 `GunCraftLauncher.exe` 与精简运行时）
- 自动生成 `dist/GunCraftLauncher-Share.zip`，把 zip 发给对方解压即用

开发时也可用 Maven 打普通 jar：`mvn -pl launcher package`，得到 `launcher/target/*.jar` 与 `lib/`，`java -jar ...` 运行。

## 直接运行游戏（不经过启动器）

```bash
cd game/target
java -jar guncraft-game-alpha-1.4.jar
```

## 操作说明（游戏内 · Alpha 1.4）

| 按键 | 功能 |
|------|------|
| **W A S D** | 移动 |
| **鼠标** | 视角 |
| **空格** | 跳跃 |
| **Shift** | 蹲下 |
| **G** | 生成生物 |
| **E** | 打开/关闭 MC 风格物品栏 |
| **1–9 / 滚轮** | 切换快捷栏 |
| **左键** | 枪射击 / 破坏方块 / 物品栏整堆操作 |
| **右键** | 放置泥土 / 物品栏单个操作 |
| **Shift+点击** | 物品栏快速转移 |
| **Esc** | 暂停菜单（设置→语言→选择语言） |
| **暂停菜单** | 回到游戏 / 设置 / 回到启动器 |

## 多版本管理

在 `versions-manifest.json` 的 `versions` 数组中增加条目（不同 `id`、`releaseTag`、`zipAssetName`），即可在启动器里切换 **Alpha 1.3 / 1.4** 等；也可通过远程清单统一分发。
