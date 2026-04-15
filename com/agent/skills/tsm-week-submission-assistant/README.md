# TSM 按周提报（终端脚本）

与 [`SKILL.md`](./SKILL.md) 配套。**推荐**：业务参数全部放在 **`tsm.config.json`**，鉴权放在 **`.env.tsm.local`**，只执行 **`from-config`**。

## 快速开始

1. 将 [`.env.tsm.local.example`](./.env.tsm.local.example) 复制为 **`.env.tsm.local`**（填网关与 token），放在 **与脚本同级** 的目录（或通过 **`TSM_SKILL_DIR`** 指定）。
2. 将 [`tsm.config.example.json`](./tsm.config.example.json) 复制为 **`tsm.config.json`**，修改 **`action`**、`week`、`saveDraft` / `saveMulti` 等（**勿把 token 写进此文件**）。
3. **Claude / 全局技能**：设置 **`TSM_REPO_ROOT`** 为 ipm-ui 仓库根（或始终在仓库根下执行并只用绝对路径）。
4. 在技能目录：

```powershell
node tsm-weekly-api.mjs from-config
```

（可选：`node tsm-weekly-api.mjs from-config D:\path\to\my-tsm.config.json`，或设置 **`TSM_CONFIG`**。）

5. 仍支持传统子命令（`query`、`save-draft`、环境变量等）：`node tsm-weekly-api.mjs` 无参查看帮助。

**说明**：不向接口发 HTTP 则无法在系统里保存工时；若完全不运行本脚本，只能把 `tsm.config.json` 当 **网页手填清单**。

## 环境变量（路径相关）

| 变量 | 作用 |
|------|------|
| `TSM_REPO_ROOT` / `TSM_WORKSPACE_ROOT` | 业务仓库根；`save`/`save-multi` 解析相对 JSON 的第二候选；`TSM_USE_REPO_ENV=1` 时参与查找 `.env` |
| `TSM_SKILL_DIR` | 技能根目录（默认=脚本所在目录），其下放置 `.env.tsm.local` |

默认 **`TSM_USE_REPO_ENV` 未开启** 时，只从 `getSkillDir()` 加载 `.env.tsm.local`。

## 说明

- Node 16+；鉴权勿粘贴到聊天。
- 与 Cursor / Claude 安装位置无关；勿在文档中写死 `.cursor/...` 路径。
