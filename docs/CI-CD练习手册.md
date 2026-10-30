# CI/CD 双账号双设备练习手册

> 项目：`shang_pin_miao_sha`（Spring Boot 3.2.5 + JDK 21）
> 平台：GitHub Actions
> 设备：A（主开发机，Windows）、B（副开发机，Windows）
> 账号：你 = **Owner**，另一个账号 = **Collaborator**（模拟真实团队里两个人协作）

---

## 0. 先理解你在练什么

CI/CD 不是"跑个绿勾"，它把下面这条链子自动化了：

```
写代码 → 提交 → 推送 → [CI] 编译 → 单测 → 打包 → 人工评审 → 合并
                                                        ↓
                                          [CD] 打 tag → 构建 → 发布 Release
```

**为什么一定要双账号双设备？** 因为一个人一个仓库永远遇不到这些真实问题：
- 两个人同时改同一个文件 → **冲突**
- 你的 PR 需要别人 **Review 才能合并**（分支保护）
- 设备 B 的代码落后 → **推送被拒**
- 凭据串号 → 推到别人仓库 / 提交作者不对

这些"疼"，才是 CI/CD 练习的全部价值。

---

## 1. 账号与仓库准备（只做一次）

### 1.1 账号分工

| 角色 | 账号 | 用途 | 设备 |
|---|---|---|---|
| Owner | 账号 1（比如 `hzp`） | 建仓库、配 CI、审批 PR、发版 | 设备 A |
| Collaborator | 账号 2 | 独立开发、提 PR、制造冲突 | 设备 B |

> 如果两个 GitHub 账号都只用一台电脑，可以用 **Chrome 无痕窗口 + 另一个浏览器 / 另一个 Chrome 用户配置** 隔离登录态，避免 cookie 互相覆盖。这是双账号练习最容易被坑的地方。

### 1.2 创建仓库（设备 A，账号 1）

1. 浏览器登录 **账号 1** → GitHub → New repository
2. 仓库名：`shang_pin_miao_sha`
3. 可见性：**Public**（Private 也能免费用 Actions，但 Public 更省心）
4. **不要**勾选 "Add a README / .gitignore / license"（本地已经有文件了）
5. Create repository

### 1.3 设备 A 配置身份并首次推送

本项目已在 `C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha` 初始化好 git，且完成第一次提交。

```powershell
cd C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha

# 确认作者身份只作用于这个仓库（推荐：仓库级配置，双账号必做）
git config user.name  "hzp"
git config user.email "2901238992@qq.com"

# 关联远程仓库（把 <账号1> 换成你的用户名）
git remote add origin https://github.com/<账号1>/shang_pin_miao_sha.git

# 推送
git push -u origin main
```

**推送第一次会弹窗要密码** —— GitHub 早已不支持账号密码，必须用 **Personal Access Token（PAT）**：

1. GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate new token
2. 勾选 `repo` 和 `workflow` 两个权限（`workflow` 是能改 `.github/workflows/` 的必需项）
3. 生成后复制（只显示一次）
4. 弹窗里 Username 填账号名，Password 粘贴 **token**

> Windows 会把凭据存进"凭据管理器"。切换账号时务必清理，否则永远在用错账号推送：
> ```powershell
> cmdkey /list | findstr git
> cmdkey /delete:git:https://github.com
> ```

### 1.4 邀请第二个账号（设备 A，账号 1）

Repository → Settings → Collaborators → Add people → 输入账号 2 的 GitHub 用户名 → 发送邀请。

然后换 **账号 2** 登录，在 GitHub 通知里接受邀请（或直接访问仓库链接）。

---

## 2. 第一次 CI：验证流水线能跑

推送完成后，浏览器打开：

```
https://github.com/<账号1>/shang_pin_miao_sha/actions
```

应当看到 **CI** workflow 正在跑（黄点）→ 变成绿勾。

### 2.1 看什么

点进那次运行，展开：

| 步骤 | 你要确认的事 |
|---|---|
| 安装 JDK 21 | `cache: maven` 生效后，第二次构建时间明显变短 |
| 编译 | `mvn -B -ntp -DskipTests clean compile` |
| 单元测试 | `Tests run: 46, Failures: 0, Errors: 0, Skipped: 5` |
| 打包 | 产出 `target/shang_pin_miao_sha.jar` |
| 上传构建产物 | 页面底部 Artifacts 里能下载 jar |
| 构建结果摘要 | 运行页面顶部 Summary 有提交、分支、触发人、jar 大小 |

> **为什么是 46 个测试、跳过 5 个？** `DatabaseIntegrationTest` 用 `@EnabledIfEnvironmentVariable(named="RUN_DB_TEST")` 做了开关，CI 里没有 MySQL，所以自动跳过。这是"测试要能在无外部依赖的环境里跑"的正确做法——**不要让 CI 依赖本机数据库**。

### 2.2 第一次就失败？对着这张表查

| 报错 | 原因 | 解决 |
|---|---|---|
| `fatal: repository not found` | 仓库地址错 / 没权限 | 检查 URL 大小写、确认是 `https://github.com/用户名/仓库.git` |
| `Authentication failed` | 用了账号密码 | 改用 PAT，且勾了 `repo` + `workflow` |
| `refusing to allow a Personal Access Token to create or update workflow` | PAT 没有 `workflow` 作用域 | 重新生成 token，勾上 `workflow` |
| Actions 页面提示 "Workflows aren't being run on this repository" | `.github/workflows` 没推上去 | `git ls-files .github` 确认文件在版本库里 |
| 测试报 `Mockito ... self-attach` | 老版本项目残留 | 本项目已修复（见第 8 节） |
| `Tests run: 0` | surefire 没匹配到测试类 | 测试类名必须以 `Test` 结尾 |

---

## 3. 分支保护：让 PR 必须过 CI 才能合并

这是 CI/CD 真正"卡住"质量的地方，**不做这一步，前面的 CI 只是装饰**。

Repository → Settings → Branches → Add branch protection rule：

| 配置项 | 值 | 作用 |
|---|---|---|
| Branch name pattern | `main` | 保护主分支 |
| Require a pull request before merging | ✅ | 禁止直接 push main |
| Require approvals | ✅ 1 | 必须有人 Review |
| Dismiss stale pull request approvals when new commits are pushed | ✅ | 改代码后旧批准作废 |
| Require status checks to pass before merging | ✅ | **核心** |
| → Status checks that are required | 选 `构建与测试 (JDK 21)` | CI 不绿不给合 |
| Require branches to be up to date before merging | ✅ | 必须先 rebase 到最新 main |
| Do not allow bypassing the above settings | ✅ | 管理员也不能绕过（练习时可开可关） |

> 注意：**status check 的选项只有在 CI 至少成功跑过一次之后才会出现在下拉框里**。所以顺序是：先推一次让它绿 → 再去配保护规则。

配好后，你试着直接推 main：

```powershell
# 这一步现在应该被拒绝
git commit --allow-empty -m "test direct push"
git push origin main
# → remote: error: GH006: Protected branch update failed
```

**看到这个报错就说明配对了。**

---

## 4. 完整 PR 流程（设备 B，账号 2）

现在换个身份、换台设备，走一遍真实协作。

### 4.1 设备 B 克隆仓库

```powershell
cd D:\work            # 设备 B 的目录，随意
git clone https://github.com/<账号1>/shang_pin_miao_sha.git
cd shang_pin_miao_sha

# 设备 B 用的是账号 2 的身份
git config user.name  "collaborator"
git config user.email "collaborator@example.com"
```

> **关键**：`git config` 不加 `--global`，只对这个仓库生效，两台设备的身份才不会串。

### 4.2 开分支做改动

```powershell
git switch -c feature/add-hotkey-endpoint
```

改一处代码，比如给 `HotKeyController` 加个接口，或者干脆改 README 加一行：

```powershell
# 演示用：给 README 加一行
Add-Content README.md "`n> 本次改动由协作者设备 B 完成，用于练习 PR 流程。"
git diff
```

### 4.3 提交并推送分支

```powershell
git add README.md
git commit -m "docs: 补充协作者说明"
git push -u origin feature/add-hotkey-endpoint
```

### 4.4 开 Pull Request

推送后 GitHub 会在仓库首页显示黄色横幅 **"Compare & pull request"**，点它。或者：

`Pull requests` → `New pull request` → base: `main` ← compare: `feature/add-hotkey-endpoint`

PR 标题和描述写清楚，然后 **Create pull request**。

### 4.5 观察 PR 页面的三个区域

| 区域 | 你现在该看到 |
|---|---|
| 底部 **Checks** | CI 正在跑，最后变绿；`构建与测试 (JDK 21)` 显示 ✅ |
| 中部 **Conversation** | 可以留评论、@ 人 |
| 右侧 **Reviewers** | 指派账号 1 评审 |

### 4.6 Review + 合并（设备 A，账号 1）

1. 打开 PR → **Files changed** → 逐行看改动 → **Review changes** → `Approve`
2. 回到 Conversation，**Merge pull request** → `Confirm merge`
3. 合并后可以 **Delete branch**（远端分支删掉，本地还在）

### 4.7 设备 B 同步 main

```powershell
git switch main
git pull origin main          # 现在 main 上有你合并进去的提交
git branch -d feature/add-hotkey-endpoint
```

**这就是一次完整的 mini-CI/CD 协作闭环。**

---

## 5. 冲突演练：故意撞车（最重要的一节）

冲突不是意外，是日常。**你必须练到闭着眼能解决**。

### 5.1 制造冲突

**设备 A（账号 1）** —— 改 README 第 1 行：

```powershell
cd C:\Users\Administrator\.dsh\ungrouped\shang_pin_miao_sha
git switch -c fix/title-from-a
# 把 README 第一行改成 "A 的标题"
git add README.md; git commit -m "docs: A 修改标题"
git push -u origin fix/title-from-a
```

**设备 B（账号 2）** —— 同时改同一行：

```powershell
git switch -c fix/title-from-b
# 把 README 第一行改成 "B 的标题"
git add README.md; git commit -m "docs: B 修改标题"
git push -u origin fix/title-from-b
```

### 5.2 让 B 的 PR 撞上 A 的 PR

1. 先在 GitHub 合并 **A 的 PR**
2. 此时 B 的 PR 页面会显示 **"This branch has conflicts that must be resolved"**，不能合并

### 5.3 在设备 B 本地解决

```powershell
git switch fix/title-from-b
git fetch origin
git rebase origin/main
```

你会看到：

```
CONFLICT (content): Merge conflict in README.md
error: could not apply a1b2c3d... docs: B 修改标题
```

打开 `README.md`，会看到：

```
<<<<<<< HEAD
# shang_pin_miao_sha —— 商品秒杀 · Redis 热 Key 治理   ← main 上的（A 的）
=======
# A 的标题                                              ← 你的（B 的）
>>>>>>> a1b2c3d (docs: B 修改标题)
```

> 注意：`git rebase` 时 `HEAD` 一侧是**别人的 main**，`=======` 下面才是**你的提交**。这点和 `merge` 相反，最容易搞混。

### 5.4 三步解决

```powershell
# 1. 手工编辑 README.md，删掉 <<<<<<<、=======、>>>>>>> 三行，保留最终想要的内容
#    （练习时保留 B 的标题，或者两者合并成 "# shang_pin_miao_sha —— 商品秒杀 · Redis 热 Key 治理（B 修订）"）

# 2. 标记已解决
git add README.md

# 3. 继续 rebase
git rebase --continue
```

### 5.5 强推分支（rebase 后必须）

```powershell
git push --force-with-lease origin fix/title-from-b
```

> **为什么用 `--force-with-lease` 而不是 `--force`？**
> `--force` 会无脑覆盖远端，如果别人在这期间推了新提交，会被你直接抹掉。
> `--force-with-lease` 会先检查远端是否还是你上次看到的那个提交，不是就拒绝——**留一手安全网**。

现在 B 的 PR 冲突消失，CI 重新跑，绿了就能合。

### 5.6 放弃这次 rebase 怎么办

```powershell
git rebase --abort
```

回到 rebase 之前的状态，一点事没有。**没有什么是 `--abort` 救不回来的。**

---

## 6. 常用 git 操作速查（双设备场景）

### 6.1 设备 B 落后了，推送被拒

```
! [rejected] main -> main (fetch first)
error: failed to push some refs
```

这不是错误，是保护。**永远不要在这时用 `git push --force`**，正确做法：

```powershell
git pull --rebase origin main     # 把你的提交挪到最新 main 之上
git push origin main
```

### 6.2 提交信息写错了 / 想合并提交

```powershell
git commit --amend -m "更准确的描述"      # 改最后一次提交（还没推）
git rebase -i HEAD~3                      # 整理最近 3 个提交（squash / reword / drop）
```

### 6.3 提交到错误的分支

```powershell
git switch 正确的分支
git cherry-pick <那个提交的hash>
git switch 错误的分支
git reset --hard HEAD~1                   # 本地撤回，谨慎
```

### 6.4 撤销

| 场景 | 命令 | 说明 |
|---|---|---|
| 改了还没 add | `git restore 文件` | 丢弃工作区改动 |
| add 了想撤回 | `git restore --staged 文件` | 退回工作区 |
| 提交了还没推 | `git reset --soft HEAD~1` | 保留改动，撤销提交 |
| 已经推了 | `git revert <hash>` | **生成一个反向提交**，历史不改，最安全 |
| 彻底回到某版本 | `git reset --hard <hash>` | 危险，会丢改动 |

### 6.5 看看自己到底做了什么

```powershell
git status -sb                     # 简洁状态 + 领先/落后几个提交
git log --oneline --graph --all    # 提交图
git diff                           # 没 add 的改动
git diff --staged                  # 已 add 的改动
git remote -v                      # 远程地址（确认没连错仓库）
git config --list --show-origin    # 看每个配置来自哪个文件（排查身份串号神器）
```

---

## 7. CD 演练：打 tag 自动发版

### 7.1 发布流程

```powershell
# 确认在最新 main 上
git switch main
git pull origin main
git log --oneline -3

# 打标签（语义化版本）
git tag -a v1.0.0 -m "首个正式版本：热 Key 三层监控 + 打散 + 二级缓存"

# 推送标签（注意 --tags，普通 push 不会带标签）
git push origin v1.0.0
```

### 7.2 观察 CD

`Actions` → **Release** workflow 自动触发 → 绿了之后：

打开 `Releases` 页面，应该看到：

- 标题 `v1.0.0`
- 自动生成的 Release Notes（`generate_release_notes: true` 根据 commit 和上一个 tag 生成）
- 附件 `shang_pin_miao_sha-1.0.0.jar`

点开 jar 下载，验证能跑：

```powershell
java -jar shang_pin_miao_sha-1.0.0.jar
```

> 需要本机 MySQL + Redis 才能完整启动。只想验证 jar 完整性的话，`java -jar xxx.jar --help` 或看启动日志报什么错即可。

### 7.3 tag 发错了怎么办

```powershell
# 删本地 tag
git tag -d v1.0.0

# 删远端 tag
git push origin :refs/tags/v1.0.0

# 重新打
git tag -a v1.0.0 -m "修正后的说明"
git push origin v1.0.0
```

对应的 GitHub Release 需要在网页上手动删掉再重建。

---

## 8. 本项目已做的关键修复（记录一下，面试能讲）

练习过程中发现并修掉了一个**真实的构建环境问题**，值得记住：

**现象**：本机 `mvn test` 时 `ProductQueryServiceTest` 全部报错：

```
java.lang.IllegalStateException: Could not initialize plugin:
    interface org.mockito.plugins.MockMaker
Caused by: MockitoInitializationException:
    Could not initialize inline Byte Buddy mock maker.
    It appears as if your JDK does not supply a working agent attachment mechanism.
```

**原因**：Mockito 5 默认使用 **inline mock maker**，它需要在运行时把自己作为 Java agent **自附加**到当前 JVM。在受限环境（禁 attach 的沙箱、某些安全策略、容器）里这一步会失败。而且就算加了 `-Djdk.attach.allowAttachSelf=true` 也未必救得回来。

**修复**：不再依赖字节码增强，改用 JDK 原生手段：

| 被测类型 | 原来 | 现在 |
|---|---|---|
| `ProductMapper`（接口） | `Mockito.mock()` | **JDK 动态代理** `Proxy.newProxyInstance` + 桩 Map |
| `StringRedisTemplate`（具体类） | `Mockito.mock()` | **静态内部类继承**并覆写实际用到的方法 |

**副作用（好的那种）**：
- 测试不再有任何 Mockito / CGLIB 运行时依赖，环境无关
- 测试跑得更快（46 项约 3 秒）
- CI 上 Ubuntu + temurin 21 和本机 Windows 行为完全一致

> **教训**：CI 环境和你本机不一样。**让测试尽可能少依赖运行时环境特性**，是让 CI 稳定绿的性价比最高的办法之一。

---

## 9. 进阶：给 CI 加东西（练手题）

按难度排，做出来就可以写在简历上：

1. **加代码格式检查**——引入 `spotless-maven-plugin`，CI 里跑 `mvn spotless:check`
2. **加静态检查**——项目已有 `-Ppmd` profile，在 CI 里加一步 `mvn -B -Ppmd pmd:check`
3. **加测试覆盖率**——引入 JaCoCo，上传报告到 Codecov 或作为 artifact
4. **加 Docker 构建**——CI 里 `docker build`（项目根目录其实有 Dockerfile 的思路可以补）
5. **加缓存优化**——现在只缓存了 `~/.m2`，可以加 `actions/cache` 缓存 `target/`
6. **加矩阵构建**——同时在 JDK 21 和 JDK 17 上跑，看兼容性
7. **加依赖漏洞扫描**——`mvn dependency-check:check` 或 GitHub 自带的 Dependabot
8. **加自动打 tag**——合并到 main 时根据 commit 类型自动 bump 版本（release-please）

每加一个，建议开一个独立 PR，**这样你能顺便练 8 次 PR 流程**。

---

## 10. 一页纸总结

```
┌─ 设备 A（账号 1，Owner）─────────────────────────────┐
│  建仓库 → 推 main → 配分支保护 → 审 PR → 打 tag 发版  │
└──────────────────────────────────────────────────────┘
                    ↕  GitHub Actions
        push/PR → 编译 → 单测 → 打包 → 产物
        tag     → 构建 → Release 附件
                    ↕
┌─ 设备 B（账号 2，Collaborator）──────────────────────┐
│  clone → 开分支 → 改代码 → push → 开 PR → 解冲突      │
└──────────────────────────────────────────────────────┘
```

**必须亲手敲过的 6 条命令**（考试级别）：

```powershell
git switch -c feature/xxx          # 开分支
git push -u origin feature/xxx     # 首推并建立跟踪
git pull --rebase origin main      # 同步并变基（不用 merge 拉出脏历史）
git rebase origin/main             # 解冲突后强推 --force-with-lease
git revert <hash>                  # 已推送的提交这样撤
git tag -a v1.0.0 -m "说明"        # 打版本标签
```

**三条铁律**：

1. **不直推 main**——所有改动走 PR，让 CI 当守门员
2. **不用 `--force`**——用 `--force-with-lease`，别抹掉别人的提交
3. **配置不加 `--global`**——双账号机器上，身份一定按仓库配
