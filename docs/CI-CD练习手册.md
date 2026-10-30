# CI/CD 双账号双设备练习手册（GitHub Desktop 版）

> 项目：`shang_pin_miao_sha`（Spring Boot 3.2.5 + JDK 21）
> 平台：GitHub Actions
> 设备：A（主开发机，账号 1）、B（副开发机，账号 2）
> 客户端：**GitHub Desktop 3.6+**（两台设备都已安装）
> 命令行为 **备用方案**，只在 GUI 做不到的地方使用（见第 8 节）

---

## 0. 先理解你在练什么

CI/CD 把下面这条链子自动化了：

```
写代码 → 提交 → 推送 → [CI] 编译 → 单测 → 打包 → 人工评审 → 合并
                                                        ↓
                                          [CD] 打 tag → 构建 → 发布 Release
```

**为什么一定要双账号双设备？** 一个人一个仓库永远遇不到这些真实问题：
- 两个人同时改同一个文件 → **冲突**
- 你的 PR 需要别人 **Review 才能合并**（分支保护）
- 设备 B 的代码落后 → **推送被拒**

这些"疼"才是练习的全部价值。

---

## 1. 一条最重要的原则：一设备一账号

**设备 A 只登录账号 1，设备 B 只登录账号 2，全程不切换。**

这一条能消掉 90% 的麻烦：不用管 token 冲突、不用管凭据管理器残留、不用管"我刚才到底以谁的身份推的"。

> 反例：两台设备都登两个账号来回切 —— 这是新手最容易把自己绕死的地方，而且**同一台机器上 GitHub Desktop 同一时刻只用一个账号**，切换后旧凭据可能还在缓存里，会让推送身份和提交作者对不上。

---

## 2. 设备 A（账号 1）准备仓库

### 2.1 在 GitHub 网页建空仓库

1. 浏览器登录 **账号 1** → New repository
2. 名称：`shang_pin_miao_sha`
3. 可见性：**Public**（Private 也能免费用 Actions，但 Public 更省心）
4. **不要**勾 "Add a README / .gitignore / license"（本地已有文件）
5. Create repository

### 2.2 用 Desktop 添加本地已有仓库

本地项目已经在 `C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha` 初始化好 git 并有 2 次提交。

1. 打开 GitHub Desktop → **File → Add local repository**
2. 路径选 `C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha`
3. 如果提示 "This directory does not appear to be a Git repository" 才对，本项目已初始化，不会出现
4. 若 Desktop 提示 "not a GitHub repository"，点 **Create repository** 或直接在 Settings 里补 remote（见 2.4）

### 2.3 登录账号 1

1. **File → Options**（或 `Ctrl+,`）→ **Accounts** 标签
2. **Sign in to GitHub.com** → 浏览器弹出 → 点 **Authorize desktop**
3. 回到 Desktop，右上角应显示账号 1 的头像和用户名

> **这里就是最大的简化**：不需要生成 Personal Access Token，不需要在弹窗里贴密码。OAuth 授权一次，之后推送全部自动。

### 2.4 关联远程仓库

本地仓库还没有 remote。两种做法：

**做法 A（推荐）**：Desktop 里 **Repository → Repository settings**（`Ctrl+Shift+,`）→ **Remote** 标签 → 填：

```
Primary remote repository (origin): https://github.com/<账号1>/shang_pin_miao_sha.git
```

**做法 B**：在 Desktop 顶部黄色横幅点 **"Publish repository"**，它会自动在账号 1 下创建仓库并推送（名字可改，**记得取消勾选 "Keep this code private"**，否则 Actions 免费额度受限）。

### 2.5 推送

顶部会显示 **Publish branch**（首次）或 **Push origin**（之后）。点一次，等进度条走完。

---

## 3. 第一次 CI：验证流水线能跑

浏览器打开：

```
https://github.com/<账号1>/shang_pin_miao_sha/actions
```

会看到 **CI** workflow 在跑（黄点）→ 变绿勾。

### 3.1 看什么

点进运行详情，展开步骤：

| 步骤 | 确认的事 |
|---|---|
| 安装 JDK 21 | `cache: maven` 生效，第二次构建明显变快 |
| 编译 | `mvn -B -ntp -DskipTests clean compile` |
| 单元测试 | `Tests run: 46, Failures: 0, Errors: 0, Skipped: 5` |
| 打包 | 产出 `target/shang_pin_miao_sha.jar` |
| 上传构建产物 | 页面底部 Artifacts 可下载 jar |
| 构建结果摘要 | 运行页顶部 Summary 有提交、分支、触发人、jar 大小 |

> **为什么 46 个测试、跳过 5 个？** `DatabaseIntegrationTest` 用 `@EnabledIfEnvironmentVariable(named="RUN_DB_TEST")` 做了开关，CI 里没有 MySQL 就自动跳过。这是"测试能在无外部依赖环境跑"的正确做法 —— **不要让 CI 依赖本机数据库**。

### 3.2 第一次就失败？对照排查

| 现象 | 原因 | 解决 |
|---|---|---|
| Desktop 推送报 `Authentication failed` | 账号登错 / 授权过期 | Options → Accounts 重新 Sign in |
| Desktop 报 `repository not found` | remote 地址错或没权限 | Repository settings → Remote 检查 URL |
| Actions 提示 "Workflows aren't being run" | `.github/workflows/` 没推上去 | Desktop 的 Changes 里确认这两个 yml 已提交推送 |
| 运行时报 `refusing to allow a PAT to create or update workflow` | 用了旧 PAT 且缺 `workflow` 权限 | 改用 Desktop OAuth 登录，或重新签发带 `workflow` 的 token |
| `Tests run: 0` | 测试类名不以 `Test` 结尾 | surefire 默认只认 `*Test` |

---

## 4. 分支保护：让 PR 必须过 CI（网页操作，GUI 替代不了）

**这一步不做，前面的 CI 只是绿勾装饰。**

Repository → Settings → Branches → Add branch protection rule：

| 配置项 | 值 | 作用 |
|---|---|---|
| Branch name pattern | `main` | 保护主分支 |
| Require a pull request before merging | ✅ | 禁止直接 push main |
| Require approvals | ✅ 1 | 必须有人 Review |
| Dismiss stale pull request approvals when new commits are pushed | ✅ | 改代码后旧批准作废 |
| Require status checks to pass before merging | ✅ | **核心** |
| → 勾选 | `构建与测试 (JDK 21)` | CI 不绿不给合 |
| Require branches to be up to date before merging | ✅ | 必须先同步到最新 main |

> **顺序很重要**：status check 的选项只有在 CI 至少成功跑过一次后才会出现在搜索框里。所以先推一次让它绿，再回来配。

配好后验证一下 —— 用 CLI 试着直推 main（这条必须用命令行，因为 Desktop 在分支保护下会直接报错弹窗）：

```powershell
cd C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha
git commit --allow-empty -m "test direct push"
git push origin main
# → remote: error: GH006: Protected branch update failed
```

**看到这个报错说明配对了。** 然后撤回这个空提交：

```powershell
git reset --hard HEAD~1
```

### 4.1 邀请账号 2

Settings → Collaborators → Add people → 输入账号 2 的用户名 → 发邀请。

换 **账号 2** 在浏览器登录，接受邀请。

---

## 5. 完整 PR 流程（设备 B，账号 2）

### 5.1 克隆

打开设备 B 的 GitHub Desktop：

1. **File → Clone repository**
2. **GitHub.com** 标签 → 列表里找 `<账号1>/shang_pin_miao_sha`（刚接受邀请，可能需要点刷新）
3. Local path 选 `D:\work\shang_pin_miao_sha`
4. **Clone**

> 如果列表里没有，用 **URL** 标签直接粘 `https://github.com/<账号1>/shang_pin_miao_sha.git`。

### 5.2 登录账号 2

Options → Accounts → Sign in，用**账号 2** 授权。这一步别搞错，否则 PR 的作者会是账号 1。

### 5.3 开分支

顶部 **Current Branch** 下拉 → **New Branch** → 输入 `feature/add-hotkey-endpoint` → Create。

### 5.4 改点东西

用编辑器改文件，比如给 `README.md` 末尾加一行：

```
> 本次改动由协作者设备 B 完成，用于练习 PR 流程。
```

回到 Desktop，左侧 **Changes** 会列出 `README.md`，右侧显示 diff。

### 5.5 提交 + 推送

1. 确认 `README.md` 已勾选
2. 左下 **Summary** 填 `docs: 补充协作者说明`，Description 可空
3. 点 **Commit to feature/add-hotkey-endpoint**
4. 顶部变 **Publish branch** → 点它

### 5.6 开 PR

推送后，顶部会出现蓝色横幅 **"Create Pull Request"** —— 点它，浏览器自动打开 PR 创建页，标题已预填。

补一段描述，点 **Create pull request**。

> 如果横幅没出现，也可以在 Desktop 菜单 **Branch → Create Pull Request**，或直接去网页 Pull requests → New。

### 5.7 观察 PR 页面的三块

| 区域 | 该看到 |
|---|---|
| 底部 **Checks** | CI 在跑 → 变绿，`构建与测试 (JDK 21)` ✅ |
| 中部 **Conversation** | 可留评论、@ 人 |
| 右侧 **Reviewers** | 指派账号 1 |

### 5.8 Review + 合并（设备 A，账号 1）

1. 打开 PR → **Files changed** → 看改动 → **Review changes** → Approve
2. 回 Conversation → **Merge pull request** → Confirm merge
3. **Delete branch** 删远端分支

### 5.9 设备 B 同步（Desktop 一键）

切回 Desktop → 顶部 **Fetch origin** → 出现 **Pull origin** 则点它。

`main` 就同步好了。本地分支清理：菜单 **Branch → Delete**，选中 `feature/add-hotkey-endpoint`。

---

## 6. 冲突演练（最重要的一节）

冲突不是意外，是日常。**练到闭着眼能解**。

### 6.1 制造冲突

**设备 A**：Current Branch → New Branch → `fix/title-from-a` → 把 README 第 1 行改成 `# A 的标题` → 提交 → Publish → Create PR → 网页合并掉。

**设备 B**：先 **Fetch origin**（重要，要拿到 A 的提交），然后 New Branch → `fix/title-from-b` → 把 README **同一行**改成 `# B 的标题` → 提交 → Publish → Create PR。

### 6.2 看冲突

B 的 PR 页面显示：

```
This branch has conflicts that must be resolved
```

不能合并。此时回设备 B 的 Desktop：点 **Pull origin**（或 Fetch origin 后点 Pull），Desktop 会弹出冲突提示，列出冲突文件。

### 6.3 用 Desktop 解决

1. 冲突文件在 Changes 里显示为 **红色 "!"** 图标
2. 点文件旁的 **"Open in Text Editor"**（或右键 → Open in ...）
3. 编辑器里会看到：

```
<<<<<<< HEAD
# A 的标题            ← main 上的（A 的）
=======
# B 的标题            ← 你的（B 的）
>>>>>>> fix/title-from-b
```

4. **删掉 `<<<<<<<`、`=======`、`>>>>>>>` 这三行**，只留最终想要的内容，比如：

```
# shang_pin_miao_sha —— 商品秒杀 · Redis 热 Key 治理（B 修订）
```

5. 保存，回 Desktop，点文件旁的 **"Mark as resolved"**
6. 全部标记完，底部按钮变 **"Continue merge"**（若用的是 rebase 则显示 **"Continue rebase"**）→ 点它

### 6.4 推送

Desktop 顶部会显示 **Push origin**。如果提示需要 force push，勾选 Desktop 弹窗里那个 **"Force push"** 选项。

> **Desktop 的 force push 比命令行的 `--force` 安全**：它底层用的是 force-with-lease 语义，会先检查远端有没有别人的新提交。

### 6.5 放弃怎么办

Desktop 菜单 **Branch → Abort Merge**（rebase 时是 **Abort Rebase**），回到冲突前状态。**没有什么是 Abort 救不回来的。**

---

## 7. CD 演练：打 tag 自动发版

**GitHub Desktop 不支持创建 tag**，所以这一步换网页，反而更直观。

### 7.1 用网页建 Release（会自动创建 tag 并触发流水线）

1. 仓库首页右侧 → **Releases** → **Draft a new release**
2. **Choose a tag** → 输入 `v1.0.0` → 点 **"Create new tag: v1.0.0 on publish"**
3. Target 选 `main`
4. Release title 填 `v1.0.0`
5. 描述写一句：`首个正式版本：热 Key 三层监控 + 打散 + 二级缓存`
6. **Publish release**

### 7.2 观察 CD

- 发布瞬间，`Actions` 里 **Release** workflow 自动触发（因为创建 tag 会触发 `push: tags`）
- 等它绿了，回到刚才的 Release 页面，附件里会多出 **`shang_pin_miao_sha-1.0.0.jar`**

> 原理：`softprops/action-gh-release` 遇到已存在的 Release 会**更新**它而不是报错，所以"先手动建 Release，再由流水线补上 jar"这个顺序是可行的，也正好演示了 CD 的价值 —— **人只负责打版本号，产物由机器生成**。

### 7.3 纯命令行的替代（想练 CLI 时用）

```powershell
cd C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha
git switch main
git pull --rebase origin main
git tag -a v1.0.0 -m "首个正式版本"
git push origin v1.0.0
```

Desktop 里 **Fetch origin** 之后就能看到这个 tag 对应的提交。

### 7.4 tag 发错了

网页上删除 Release（Release 页面 → Delete），然后命令行删 tag：

```powershell
git tag -d v1.0.0
git push origin :refs/tags/v1.0.0
```

重新按 7.1 走一遍。

---

## 8. Desktop 做不到、必须用命令行的场景

Desktop 覆盖了 95% 的日常操作，但下面这些没有 GUI：

| 场景 | 命令 | 说明 |
|---|---|---|
| 打/删 tag | `git tag -a v1.0.0 -m "说明"` | 也可用网页 Release 代替 |
| 整理提交历史（squash/reword/drop） | `git rebase -i HEAD~3` | 提交乱了想合并成一个时用 |
| 撤销**已推送**的提交 | `git revert <hash>` | 生成反向提交，历史不改，最安全 |
| 挑某个提交到别的分支 | `git cherry-pick <hash>` | |
| 查看配置来源 | `git config --list --show-origin` | 排查身份串号神器 |
| 空提交测分支保护 | `git commit --allow-empty -m "test"` | 第 4 节验证用 |

**在 Desktop 里打开终端**：菜单 **Repository → Open in Command Prompt**（或 `Ctrl+`` ` 打开内置终端）。这样不用自己 cd 路径。

---

## 9. 本项目已做的关键修复（面试能讲）

练习过程中发现并修掉了一个**真实的构建环境问题**：

**现象**：`mvn test` 时 `ProductQueryServiceTest` 全部报错：

```
IllegalStateException: Could not initialize plugin: interface org.mockito.plugins.MockMaker
Caused by: MockitoInitializationException: Could not initialize inline Byte Buddy mock maker.
    It appears as if your JDK does not supply a working agent attachment mechanism.
```

**原因**：Mockito 5 默认用 **inline mock maker**，需要在运行时把自己作为 Java agent **自附加**到当前 JVM。在受限环境（禁 attach 的沙箱、容器、某些安全策略）里必然失败，加 `-Djdk.attach.allowAttachSelf=true` 也救不回来。

**修复**：不依赖字节码增强，改用 JDK 原生手段：

| 被测类型 | 原来 | 现在 |
|---|---|---|
| `ProductMapper`（接口） | `Mockito.mock()` | **JDK 动态代理** + 桩 Map |
| `StringRedisTemplate`（具体类） | `Mockito.mock()` | **内部类继承**，覆写实际用到的方法 |

**好处**：
- 测试零 Mockito / CGLIB 运行时依赖，环境无关
- 更快（46 项约 3 秒）
- GitHub 的 Ubuntu + temurin 21 和本机 Windows 行为完全一致

> **教训**：CI 环境和你本机不一样。**让测试少依赖运行时环境特性**，是让 CI 稳定绿性价比最高的办法。

---

## 10. 进阶练手题（按难度）

每加一个就开一个独立 PR —— **顺便练 8 次 PR 流程**：

1. **格式检查** —— 引入 `spotless-maven-plugin`，CI 加 `mvn spotless:check`
2. **静态检查** —— 项目已有 `-Ppmd` profile，CI 加 `mvn -B -Ppmd pmd:check`
3. **覆盖率** —— 引入 JaCoCo，报告作为 artifact 上传
4. **Docker 构建** —— CI 加 `docker build`，把镜像推到 GHCR
5. **缓存优化** —— 现在只缓存 `~/.m2`，可加 `actions/cache` 缓存 `target/`
6. **矩阵构建** —— 同时跑 JDK 21 和 JDK 17
7. **依赖漏洞扫描** —— `mvn dependency-check:check` 或开启 Dependabot
8. **自动发版** —— 合并到 main 时按 commit 类型自动 bump 版本（release-please）

---

## 11. 一页纸总结

```
┌─ 设备 A（账号 1）──────────────────────────────────┐
│  Desktop 登录账号1 → 建仓库 → Publish → 网页配保护  │
│  → 网页审 PR 合并 → 网页建 Release 发版             │
└──────────────────────────────────────────────────┘
                    ↕  GitHub Actions
        push/PR → 编译 → 单测 → 打包 → artifact
        tag     → 构建 → Release 附件
                    ↕
┌─ 设备 B（账号 2）──────────────────────────────────┐
│  Desktop 登录账号2 → Clone → New Branch → 改代码    │
│  → Commit → Publish → Create PR → 解冲突            │
└──────────────────────────────────────────────────┘
```

**Desktop 里最常按的 6 个按钮**：

```
Current Branch → New Branch      开分支
Changes → Commit to xxx          提交
Publish branch / Push origin     推送
Fetch origin / Pull origin       同步
Create Pull Request（横幅）       开 PR
Mark as resolved → Continue      解冲突
```

**四条铁律**：

1. **一设备一账号**，全程不切换
2. **不直推 main**，所有改动走 PR，让 CI 当守门员
3. **冲突用 Abort 保底**，任何时候都能反悔
4. **Review 和分支保护只能网页配**，GUI 替代不了
