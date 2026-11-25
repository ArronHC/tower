# Tower Android Architecture & Contract

本文档定义「塔台 · Tower」安卓原生版（分支 `Android`）的总体架构、目录所有权边界、共享契约、产品约束与构建发布规范。

## 1. 目标与核心定位

将原生 iOS / macOS App「塔台 · Tower」完整转换为 **100% 本机优先、原生高性能的 Android 应用（Kotlin + Jetpack Compose）**：
- **纯本地核心引擎（`android/core/`，Kotlin JVM 模块）**：订阅抓取、全协议解析、内置离线 IP 国家 / ASN 二进制库、节点地区识别、并发延迟测速、世界点阵地图加载、ACL4SSR / 自定义规则方案解析与拖拽重排冲突检测、策略组图标识别、Tailscale 内网配置注入、19 个客户端（含 Android 原生客户端与跨平台/Apple 客户端）配置生成、本机 `127.0.0.1` 3 分钟一键导入服务、随机 Token 局域网订阅服务、QR 矩阵生成、原子快照持久化。
- **Android 平台绑定（`android/app/src/main/java/com/jzb/tower/android/platform/`）**：Android Assets 资源读取（直接复用仓库 `Tower/Resources/` 与 `docs/images/clients/`，零资产复制入库）、Android KeyStore AES-GCM 硬件级加密存储 Tailscale Auth Key、局域网共享前台服务 `LanSharingForegroundService`、依赖注入容器 `TowerAppContainer`。
- **原生 Jetpack Compose 前端（`android/app/src/main/java/com/jzb/tower/android/ui/`）**：Material 3 Edge-to-Edge 界面 + Tower 原生设计语言（卡片圆角、柔和边框、明暗主题自适应、克制流畅动效）、自绘 Compose `Canvas` 220×149 点阵世界地图、订阅管理、策略组单列表拖拽重排与冲突撤销弹窗、横向客户端选择器与代码高亮预览、Android URL Scheme / Intent 一键导入与系统文件分享、Tailscale 内网管理与备份导入导出。

## 2. 模块与分工边界（严禁跨界修改）

```text
tower/
├── Tower/Resources/                # 既有离线资源（只读，Gradle 构建期自动打包进 APK assets/tower_resources/）
├── docs/images/                    # 既有图标资源（只读，Gradle 构建期自动打包进 APK assets/tower_clients/）
├── docs/ANDROID_ARCHITECTURE.md    # 本文档（规划者维护）
└── android/
    ├── settings.gradle.kts         # 规划者冻结
    ├── build.gradle.kts            # 规划者冻结
    ├── gradle.properties           # 规划者冻结
    ├── core/
    │   ├── build.gradle.kts        # 规划者冻结
    │   └── src/
    │       ├── main/java/com/jzb/tower/core/
    │       │   ├── model/          # 【规划者冻结】DomainModels.kt, ClientCatalog.kt
    │       │   ├── contract/       # 【规划者冻结】TowerContracts.kt
    │       │   ├── service/        # 【后端 gpt-6.2-sol 全权负责】解析、IP/ASN、测速、规则、生成、HTTP 服务、QR
    │       │   ├── store/          # 【后端 gpt-6.2-sol 全权负责】PersistenceStore、ConfigurationCache
    │       │   └── engine/         # 【后端 gpt-6.2-sol 全权负责】TowerRepositoryImpl
    │       └── test/java/com/jzb/tower/core/
    │                               # 【后端 gpt-6.2-sol 全权负责】完整单元测试套件
    └── app/
        ├── build.gradle.kts        # 规划者冻结
        └── src/main/
            ├── AndroidManifest.xml # 规划者冻结
            ├── res/                # 【前端 gemini-4-hydrogen high 负责文案/资源；launcher 图标由规划者生成】
            └── java/com/jzb/tower/android/
                ├── TowerApplication.kt            # 规划者维护
                ├── di/TowerAppContainer.kt        # 【后端 gpt-6.2-sol 接入真实实现】
                ├── platform/                      # 【后端 gpt-6.2-sol 全权负责】AndroidResourceProvider, AndroidTailnetSecretStore, LanSharingForegroundService
                ├── MainActivity.kt                # 【前端 gemini-4-hydrogen high 全权负责】
                ├── TowerViewModel.kt              # 【前端 gemini-4-hydrogen high 全权负责】
                └── ui/                            # 【前端 gemini-4-hydrogen high 全权负责】
                    ├── theme/TowerTheme.kt
                    ├── components/
                    ├── onboarding/WelcomeScreen.kt
                    ├── subscriptions/
                    ├── rules/
                    ├── export/
                    └── settings/
```

**协作硬规**：
1. `core/model/*`、`core/contract/*`、所有 `*.gradle.kts`、`AndroidManifest.xml` 由规划者冻结；后端与前端严禁修改冻结接口签名，确保两端并行编译零冲突。
2. 后端只写 `android/core/src/main/java/com/jzb/tower/core/{service,store,engine}/`、`android/core/src/test/`、`android/app/src/main/java/com/jzb/tower/android/platform/` 和 `android/app/src/main/java/com/jzb/tower/android/di/TowerAppContainer.kt`。
3. 前端只写 `android/app/src/main/java/com/jzb/tower/android/ui/`、`MainActivity.kt`、`TowerViewModel.kt` 及 `android/app/src/main/res/values*/strings.xml`。

## 3. 不可破坏的产品约束（源自 CLAUDE.md）

1. **100% 本机处理**：订阅解析、IP 国家识别、规则匹配、配置生成全部在设备本地完成，不上传第三方转换服务或在线 IP API。
2. **离线 IP / ASN 库**：直接二进制二分查找打包在 APK 内的 `IPCountryIPv4.bin` / `IPCountryIPv6.bin` / `IPASNIPv4.bin` / `IPASNIPv6.bin` / `IPASNNames.bin`，并支持 sing-box GEOIP / IP-ASN CIDR 展开。
3. **节点地区识别顺序**：**用户手动指定（`countryOverride`） → 节点名（国旗 Emoji → 中英文国名/别名/城市 → 大写国家代码） → 离线 IP 国家库**；禁止从域名后缀或词语猜测国家；国旗一律用 Emoji 渲染、列表里不加圆形底。
4. **刷新保留排除项（`carriedOverExclusions`）**：先精确 `canonicalKey` 匹配，失配时用去掉 remark 的宽松键（且仅在刷新前后均唯一时）继承 `excludedNodeIDs` 与 `countryOverride`。
5. **配额与原体保真**：按 `subscription-userinfo` 响应头 → `STATUS=` 行 → 公告行提取；缺结构化配额时补发一次 `flag=clash` 且**只读响应头**，节点始终以原订阅返回体为准。
6. **不可信名称转义与协议保真**：写入 INI 前必须经 `confName`（去除换行，替换 `=` / `,` / `#` / `;` / `//` / `[` / `]`），写入 YAML 前安全引号转义；无法忠实表达的节点（如带 SIP003 plugin 的 SS、不支持的协议/传输）明确跳过并计入 `skippedCount` 与 `skippedReasons`。
7. **本地 127.0.0.1 一键导入与 QuanX 边界**：一键导入使用仅绑定 `127.0.0.1`、3 分钟自动失效的临时 HTTP 服务；Quantumult X 无完整本地配置导入 Scheme，保持系统文件分享与复制，严禁伪装成一键导入；QuanX 全节点 `url-latency-benchmark` 直接列出代理节点，禁止 `server-tag-regex=.*`。
8. **Android 客户端一等公民支持**：默认客户端顺序优先呈现 Android 常用客户端（FlClash、Clash / Mihomo、sing-box、Hiddify、Surfboard、v2rayNG / V2Box、Clash Mi、Karing），并保留全部跨平台/Apple 客户端以便局域网订阅分发。Surfboard 遵循手册严格过滤协议（跳过 SSH / TrustTunnel / MASQUE、VMess/Trojan HTTPUpgrade、SS ShadowTLS、Snell v6、缺 dns-server/mtu 或带 reserved 的 WireGuard）。
9. **Tailscale 内网安全边界**：`TailnetConnection` 不是节点，不进任何策略组/地区组；Auth Key 仅存 Android KeyStore AES-GCM 加密文件，绝不进入快照或导出 JSON，配置预览中必须打码，Surge 永不写入 Key（仅写 `interactive-login`），`stableSlug` 固定来自连接 UUID。
10. **规则拖拽优先级与冲突撤销**：规则定制页所有策略组在同一个列表中可排序；`groupOrder` 决定面板顺序，`matchingOrder` 通过最小改动移动被拖组规则块（行尾 `GEOIP` / `FINAL` 留在原处），绝不把 `groupOrder` 整张套到规则顺序上；若拖拽导致域名匹配变化，前端必须弹窗提示并提供一键撤销（同时恢复 `groupOrder` 和 `matchingOrder`）。
11. **自绘点阵世界地图**：使用 Compose `Canvas` 渲染 `WorldDotMap.txt` + `WorldDotCountries.txt`（220×149），不使用在线地图 SDK；覆盖国家按延迟着色（未测速灰蓝色），选中加深加密且文字保持中性，整个国家点阵轮廓可点击筛选。
12. **剪贴板与展开交互**：打开添加面板时自动读一次系统剪贴板、仅在受支持内容时自动填充，同一次展示不重复读取，并保留手动「从剪贴板粘贴」按钮；订阅展开不使用顶部滑入过渡，不提供「显示更多节点」。
13. **公开仓库脱敏**：不提交任何 keystore、签名密码、个人邮箱、设备 ID 或私钥。

## 4. 构建、测试与发布命令

```sh
export JAVA_HOME="$HOME/.local/jdk/jdk-21"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

cd android
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Release 签名通过环境变量或本地不提交的 `android/signing.properties` 传入：
- `TOWER_KEYSTORE_PATH`
- `TOWER_KEYSTORE_PASSWORD`
- `TOWER_KEY_ALIAS`
- `TOWER_KEY_PASSWORD`
