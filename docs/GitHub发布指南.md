# GunCraft Alpha — GitHub 首次发布

账号：**xumengmeng-0922**  
仓库名：**GunCraftAlpha**（与清单里 `githubRepo` 一致）

清单已配置：`docs/versions-manifest.json` → 从 Raw 拉版本列表，从 Releases 下载 `guncraft-game-alpha-1.3.zip`。

---

## 第一步：安装 Git（若还没有）

下载：https://git-scm.com/download/win  
安装时勾选 **Add Git to PATH**。

可选（命令行建仓库）：https://cli.github.com/ → 安装后执行 `gh auth login`。

---

## 第二步：在 GitHub 网页建空仓库

1. 打开 https://github.com/new  
2. Repository name：`GunCraftAlpha`  
3. **Public**  
4. **不要**勾选 “Add a README”（本地已有代码）  
5. Create repository  

记下页面上的地址：`https://github.com/xumengmeng-0922/GunCraftAlpha.git`

---

## 第三步：本地推送代码

在项目根目录双击 **`GitHub首次发布.bat`**，或手动：

```bat
cd C:\Users\13618\Desktop\GunCraftAlpha
git init
git branch -M main
git add -A
git status
git commit -m "GunCraft Alpha 1.3: game, launcher, docs"
git remote add origin https://github.com/xumengmeng-0922/GunCraftAlpha.git
git push -u origin main
```

首次 `git push` 会弹出 GitHub 登录（浏览器或 Personal Access Token）。

---

## 第四步：打游戏 zip 并上传 Release

1. 双击 **`打包游戏zip.bat`**（需 Maven），得到  
   `game\target\guncraft-game-alpha-1.3.zip`
2. GitHub 仓库 → **Releases** → **Draft a new release**
3. **Choose a tag**：新建标签 **`game-alpha-1.3`**（必须与清单里 `releaseTag` 一致）
4. 上传附件：**`guncraft-game-alpha-1.3.zip`**（文件名必须与 `zipAssetName` 一致）
5. Publish release

下载直链会自动是：

`https://github.com/xumengmeng-0922/GunCraftAlpha/releases/download/game-alpha-1.3/guncraft-game-alpha-1.3.zip`

---

## 第五步：验证清单 Raw 地址

浏览器打开（推送 main 分支后应返回 JSON）：

https://raw.githubusercontent.com/xumengmeng-0922/GunCraftAlpha/main/docs/versions-manifest.json

---

## 第六步：打包并分发启动器

1. 双击 **`打包启动器.bat`** → `dist\GunCraftLauncher-Share.zip`
2. 发给玩家解压，运行 `GunCraftLauncher.exe`
3. 启动器会自动拉上面的 Raw 清单，在「版本与下载」里 **下载 / 更新** 即可（无需手填 URL）

本地调试仍可用 **「从本地 zip 安装…」**。

---

## 以后发新版（例如 Alpha 1.4）

1. 改 `pom.xml` / 内嵌清单里的版本号  
2. `打包游戏zip.bat` → 新 zip  
3. 新 Release 标签 + 上传 zip  
4. 只改 **`docs/versions-manifest.json`** 里版本条目，推送到 `main`  
5. 玩家启动器里点 **刷新版本列表**（一般不用重装启动器）
