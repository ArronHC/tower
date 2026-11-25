# Tower Web Architecture & Contract

本文档定义「塔台 · Tower」网页版（分支 `Web`）的总体架构、目录边界、前后端契约与产品约束。

## 1. 目标与核心定位

将原生 iOS / macOS App「塔台 · Tower」完整转换为 **本机优先 / 自托管（Local-First / Self-Hosted）网页版**：
- **后端（`web/server/`）**：Node.js 22 + TypeScript + Express 本地服务，直接复用仓库内已打包的离线资产（`Tower/Resources/IPCountry/`、`Tower/Resources/ACL4SSR/`、`Tower/Resources/WorldMap/`、`Tower/Resources/RuleCatalog/`、`Tower/Resources/PolicyGroupIcons/` 与 `docs/images/clients/`），零数据复制、零第三方转换上传。
- **前端（`web/client/`）**：React 19 + TypeScript + Vite + Tailwind CSS v4 单页应用，遵循 Tower 原生设计语言（卡片、胶囊统计、流畅克制动效、明暗主题自适应、自绘 Canvas 点阵世界地图、拖拽排序），通过 REST API 与后端通信。
- **共享契约（`web/shared/`）**：前后端共享的 TypeScript 类型、客户端能力矩阵常量与 DTO 定义。

## 2. 目录结构与分工边界

```text
tower/
├── Tower/Resources/           # 既有离线资源（只读复用：IPCountry / ACL4SSR / WorldMap / RuleCatalog / PolicyGroupIcons）
├── docs/images/               # 既有图标资源（只读复用：icon.png 与 clients/*.png）
├── docs/WEB_ARCHITECTURE.md   # 本文档（规划者维护）
└── web/
    ├── package.json           # 根脚本（dev / build / start / test / typecheck）
    ├── shared/                # 共享契约（规划者冻结，前后端只读引用）
    │   └── src/
    │       ├── types.ts
    │       ├── constants.ts
    │       └── index.ts
    ├── server/                # 后端（gpt-6.2-sol 全权负责）
    │   ├── package.json
    │   ├── tsconfig.json
    │   └── src/
    │       ├── index.ts              # 启动入口（监听 127.0.0.1 或 0.0.0.0，默认端口 38420；生产模式同时托管 client/dist）
    │       ├── app.ts                # Express 应用工厂（便于测试）
    │       ├── paths.ts              # 定位仓库根、Tower/Resources、docs/images、本地 data 目录
    │       ├── store/                # PersistenceStore（原子写入、0600 权限、Tailnet 独立密钥文件）、AppStateService
    │       ├── services/
    │       │   ├── subscriptionFetcher.ts    # 订阅拉取（自定义 UA、DoH、subscription-userinfo、STATUS=、flag=clash 仅补配额）
    │       │   ├── subscriptionParser.ts     # Base64 / URI 全协议 / Clash YAML / Surge INI 解析 + 不可忠实表达节点拒绝计数
    │       │   ├── ipCountryDb.ts            # 二进制 IPCountryIPv4/IPv6 + IPASNIPv4/IPv6 + IPASNNames 查询与 CIDR 展开
    │       │   ├── countryTable.ts           # 国家表（由 Tower/Services/CountryTable.swift 等价数据构建）
    │       │   ├── nodeRegionResolver.ts     # 手动覆盖 -> 节点名（国旗/别名/大写代码）-> 离线 IP 库
    │       │   ├── nodeLatencyService.ts     # 自动 / ICMP(ping) / TCP 并发测速，UDP-only 不做 TCP 探测
    │       │   ├── worldDotMapService.ts     # 解析 WorldDotMap.txt 与 WorldDotCountries.txt
    │       │   ├── ruleSchemeParser.ts       # ACL4SSR / Subconverter INI / Clash YAML / Surge 规则方案解析
    │       │   ├── ruleSchemeService.ts      # 内置 ACL4SSR、规则定制、matchingOrder/groupOrder 拖拽优先级与冲突检测、RuleCatalog
    │       │   ├── policyGroupIcons.ts       # PolicyGroupEmoji 识别与 FluentCircle / 图标映射
    │       │   ├── tailnetWriter.ts          # TailnetConnection 写入（Surge 不写 Key，Stash/mihomo/sing-box 写 Key；预览打码）
    │       │   ├── configGenerator.ts        # 18 客户端完整配置 + 仅节点 + Anywhere AJSON + Surfboard 过滤 + 严格名称转义
    │       │   └── lanSubscription.ts        # 随机 Token 局域网/Web 订阅端点（UA 自动识别 + target 参数 + AJSON header）
    │       └── routes/               # REST API + /sub/:token + 静态资源路由
    └── client/                # 前端（gemini-4-hydrogen high 全权负责）
        ├── package.json
        ├── tsconfig.json
        ├── vite.config.ts
        ├── index.html
        └── src/
            ├── main.tsx
            ├── index.css             # TowerTheme CSS tokens（明/暗模式、字体、卡片、动效）
            ├── api/                  # 类型安全 API 客户端
            ├── state/                # 全局状态管理与 Toast / 刷新进度反馈
            ├── components/           # 通用组件（布局、Toast、Modal、MetricPill、SelectionIndicator、ClientIcon 等）
            └── features/
                ├── onboarding/       # WelcomeView 首次隐私引导
                ├── subscriptions/    # WorldDotMapCanvas、订阅卡片、添加来源、节点列表/筛选/测速/地区选择器、节点分享二维码
                ├── rules/            # 规则方案列表、策略组拖拽排序+冲突撤销弹窗、策略组详情编辑、规则库搜索、自定义规则集、导入方案
                ├── export/           # 横向可拖拽客户端选择器、客户端筛选排序、兼容摘要、代码高亮预览与编辑、导出/复制/URL Scheme/LAN 共享
                └── settings/         # 常规设置、DNS 保护、测速模式、Tailscale 内网管理、快照导入导出与重置
```

## 3. 不可破坏的产品约束（源自 CLAUDE.md，Web 版必须严格遵守）

1. **本机处理，隐私优先**：订阅、节点与生成配置仅在本机 Node.js 后端处理，不上传第三方转换或在线 IP 查询服务。
2. **离线 IP / ASN 库**：国家识别与 ASN 组织查询直接读取 `Tower/Resources/IPCountry/*.bin`；节点地区优先级固定为：**用户手动指定（`countryOverride`） → 节点名（国旗 Emoji / 中英文国名别名 / 大写国家码） → 离线 IP 国家库**（不从域名后缀猜国家）。
3. **节点排除保留（`carriedOverExclusions`）**：刷新订阅时，先按精确 `canonicalKey` 匹配，再按去 remark 的宽松键（仅当刷新前后唯一时）继承 `excludedNodeIDs` 与 `countryOverride`。
4. **配额与原体保真**：配额按 `subscription-userinfo` 响应头 → `STATUS=` 行 → 公告行提取；仅缺结构化配额时补发一次 `flag=clash` 请求且**只读响应头**，节点始终以原地址返回体为准。
5. **不可信名称转义**：所有节点名与策略组名写入 INI 前经过 `confName`（去除换行，替换 `=` / `,` / `#` / `;` / `//` / `[` / `]`），写入 YAML 前经过安全引号转义，杜绝规则注入。
6. **协议保真**：无法忠实表达的节点（如带不支持 SIP003 plugin 的 SS、目标客户端不支持的协议/传输）明确拒绝并计入 `skippedCount` 与 `skippedReasons`。
7. **Quantumult X 边界**：QuanX 没有完整本地配置一键导入 URL Scheme，前端不得伪装成完整一键导入，保持文件下载/复制；QuanX 全节点测速组直接列节点名，不用 `server-tag-regex=.*`。
8. **Tailscale 安全边界**：`TailnetConnection` 不是节点，不进任何策略组/地区组；Auth Key 单独保存在服务端受限文件（`0600`），绝不进入导出快照 JSON，也不在普通配置预览中明文回显（预览时打码，真实下载/订阅导出按客户端规则写入，Surge 永远写 `interactive-login` 不写 Key）。
9. **规则拖拽优先级与冲突提示**：规则定制页所有策略组在一个列表里可拖拽；`groupOrder` 决定面板展示顺序，`matchingOrder` 通过最长递增子序列（LIS）最小改动移动被拖组规则块（保留行尾 `GEOIP` 与 `FINAL`），绝不把 `groupOrder` 整张直接套到规则顺序上；若拖拽导致域名匹配结果变化，返回 `conflicts` 供前端弹窗提示并提供一键撤销。
10. **自绘点阵世界地图**：前端使用 `<canvas>` 渲染 `WorldDotMap.txt` + `WorldDotCountries.txt`（220×149 网格），不使用任何联网地图 SDK；有节点覆盖的国家按延迟状态着色，未测速呈灰蓝色，点击国家点阵轮廓即可选中并联动筛选节点。
11. **临时与持久化文件权限**：本地状态文件与密钥文件以原子写（tmp + rename）+ `0o600` 权限落盘。

## 4. HTTP API 契约（所有路由均以 `/api` 为前缀）

通用响应格式：
- 成功：HTTP `200` / `201`，返回 JSON 数据对象（具体类型见 `web/shared/src/types.ts`）。
- 失败：HTTP `4xx` / `5xx`，返回 `{ error: string, code?: string, details?: unknown }`。

### 4.1 引导、状态与静态资产
| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/health` | 健康检查 `{ ok: true, version, resourcesReady, ipDbVersion }` |
| `GET` | `/api/state` | 获取完整前端视图状态 `AppStateResponse`（含 snapshot、runtimeMap、schemes、lanStatus、stats） |
| `GET` | `/api/world-map` | 获取预解析的 `WorldDotMapData`（220×149 网格、国家点集、经纬度辅助） |
| `GET` | `/api/countries` | 获取国家列表 `CountryEntry[]`（用于手动选择地区） |
| `GET` | `/assets/clients/:filename` | 静态托管 `docs/images/clients/*.png` |
| `GET` | `/assets/tower-icon.png` | 静态托管 `docs/images/icon.png` |

### 4.2 订阅与节点（Subscriptions & Nodes）
| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/sources/detect` | 输入检测 `{ input: string }` → `SourceDetectionResult`（识别订阅 URL / 节点链接 / Clash / Surge / 规则） |
| `POST` | `/api/subscriptions` | 添加订阅 `{ name?, urlString, requestOptions? }`，自动拉取解析并返回 `SubscriptionMutationResult` |
| `PATCH` | `/api/subscriptions/:id` | 更新订阅（改名、改 URL、启用/禁用、请求选项） |
| `DELETE` | `/api/subscriptions/:id` | 删除订阅及其所属节点 |
| `POST` | `/api/subscriptions/:id/refresh` | 刷新单个订阅（保留排除与地区覆盖） |
| `POST` | `/api/subscriptions/refresh-all` | 批量刷新全部已启用订阅（不同 host 并发、同 host 串行） |
| `POST` | `/api/nodes/import` | 导入自有节点文本 `{ content: string }` → `{ addedNodes, skippedCount, skippedReasons }` |
| `PATCH` | `/api/nodes/:id` | 编辑单个节点（名称、地区手动覆盖 `countryOverride`、启用/排除 `excluded` 或参数编辑） |
| `DELETE` | `/api/nodes/:id` | 删除单个自有节点 |
| `POST` | `/api/nodes/exclusions` | 批量设置排除节点 `{ excludedNodeIDs: string[] }` 或 `{ nodeIds: string[], excluded: boolean }` |
| `POST` | `/api/nodes/latency` | 测速 `{ nodeIds?: string[], mode?: NodeLatencyTestMode }` → `{ results: Record<string, NodeLatencyMeasurement> }` |
| `POST` | `/api/nodes/resolve-geo` | 触发离线 IP/ASN 补全解析 `{ nodeIds?: string[] }` → `{ runtime: Record<string, NodeRuntimeInfo> }` |
| `GET` | `/api/nodes/:id/share` | 获取单节点分享 URI 与 QR DataURL `{ uri, qrDataUrl }` |
| `PUT` | `/api/nodes/export-filter` | 更新全局节点名导出筛选 `NodeExportNameFilter` |

### 4.3 规则与策略组（Rules & Policy Schemes）
| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/rules/schemes` | 列出内置 ACL4SSR + 已导入方案 + 当前定制后展示状态 `RuleSchemesResponse` |
| `PUT` | `/api/rules/selected` | 切换当前选中方案 `{ schemeId: string }` |
| `POST` | `/api/rules/schemes/import` | 从 URL 或文本导入规则方案 `{ name?, url?, content? }` |
| `DELETE` | `/api/rules/schemes/:id` | 删除用户导入方案（内置不可删） |
| `POST` | `/api/rules/schemes/:id/refresh` | 刷新远程导入方案（保留 customization 与 customRuleFlows） |
| `PUT` | `/api/rules/schemes/:id/enabled-groups` | 设置启用的分流规则组 `{ enabledGroupNames: string[] | null }` |
| `POST` | `/api/rules/schemes/:id/reorder-groups` | 拖拽排序策略组 `{ fromIndex, toIndex, orderedGroupNames }` → 返回更新后的方案与 `conflicts: MatchingOrderConflict[]`、`previousCustomization`（供撤销） |
| `PUT` | `/api/rules/schemes/:id/customization` | 更新/恢复整份 `RuleSchemeCustomization`（含撤销、策略组改名/成员编辑/节点正则过滤、删除组、Emoji 开关、DNS 覆盖） |
| `POST` | `/api/rules/schemes/:id/groups` | 新建自定义节点过滤分组或策略组 |
| `PATCH` | `/api/rules/schemes/:id/groups/:groupName` | 编辑策略组（改名、修改类型、修改成员候选、节点正则、补充规则） |
| `DELETE` | `/api/rules/schemes/:id/groups/:groupName` | 删除非受保护策略组 |
| `GET` | `/api/rules/catalog` | 搜索内置规则库索引 `?q=...&source=all|acl4ssr|blackmatrix7&limit=50` |
| `POST` | `/api/rules/flows` | 添加规则库条目或自定义规则流到方案 |
| `PATCH` | `/api/rules/flows/:id` | 编辑/启停自定义规则流 |
| `DELETE` | `/api/rules/flows/:id` | 删除自定义规则流 |
| `GET` | `/api/rules/local-sets` | 列出用户本地规则集 `LocalRuleSet[]` |
| `POST` | `/api/rules/local-sets` | 新建本地规则集 |
| `PATCH` | `/api/rules/local-sets/:id` | 编辑本地规则集 |
| `DELETE` | `/api/rules/local-sets/:id` | 删除本地规则集 |

### 4.4 配置导出与订阅分发（Export & LAN Sharing）
| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/export/generate` | 生成指定客户端配置 `{ target: ClientTarget, mode: ExportContentMode, maskSecrets?: boolean }` → `GeneratedConfiguration` |
| `GET` | `/api/export/download` | 直接下载配置文件 `?target=...&mode=...`（含真实 Tailnet Key，带 `Content-Disposition: attachment`） |
| `PUT` | `/api/export/preferences` | 更新选中目标、可见客户端集合、排序、各客户端内容模式、排除协议 |
| `GET` | `/api/lan/status` | 获取局域网/Web 订阅共享状态（含可用本机/局域网 URL 与访问 Token） |
| `POST` | `/api/lan/toggle` | 开启/关闭订阅共享 `{ enabled: boolean }` |
| `POST` | `/api/lan/rotate-token` | 轮换随机访问 Token |
| `GET` | `/api/lan/qr` | 生成指定格式订阅链接的二维码 `?format=auto|clash|surge|...&content=fullConfiguration|nodesOnly&baseUrl=...` |
| `GET` | `/sub/:token` | **客户端拉取订阅端点**：支持 `?target=...&content=fullConfiguration|nodesOnly`，否则按 `User-Agent` 自动识别客户端格式；若带 `X-Anywhere-Format: ajson/1` 且有 AJSON 则返回 `application/json`；响应附带 `subscription-userinfo` 与 `profile-title` |

### 4.5 设置、Tailscale 与快照管理（Settings & Tailnet）
| Method | Path | 说明 |
|---|---|---|
| `PATCH` | `/api/settings` | 更新 `AppSettings` |
| `GET` | `/api/tailnets` | 列出 `TailnetConnection[]`（附 `hasAuthKey: boolean`，不回传明文 Key） |
| `POST` | `/api/tailnets` | 新建 Tailscale 连接（可附 `authKey` 单独存服务端密钥库） |
| `PATCH` | `/api/tailnets/:id` | 更新连接（`authKey === null` 清除，`undefined` 保持不变） |
| `DELETE` | `/api/tailnets/:id` | 删除连接及对应密钥 |
| `GET` | `/api/snapshot/export` | 导出不含 Auth Key 的本地备份 JSON |
| `POST` | `/api/snapshot/import` | 导入备份 JSON 恢复状态 |
| `POST` | `/api/snapshot/reset` | 重置全部本地数据（含密钥库） |

## 5. 开发与运行方式

- 后端默认端口：`38420`（环境变量 `PORT` 可覆盖，默认绑定 `0.0.0.0` 以支持局域网扫码订阅，或通过 `HOST` 覆写）。
- 前端开发端口：`5173`（Vite 代理 `/api`、`/sub`、`/assets` 到 `http://127.0.0.1:38420`）。
- 生产构建：`web/client/dist` 构建完成后由后端 Express 托管，单端口访问完整网页版。
