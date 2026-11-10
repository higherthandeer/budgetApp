# Android 通知记账首版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Java Android 工程中交付仅以通知自动采集、支持手工增改删与集中核对、统计一致且可本机长期使用的记账首版。

**Architecture:** 保留单个 app 模块，通知适配和手工表单产生独立来源证据，解析输出统一候选，处理层通过 Room 事务完成校验、去重、分类和入账。统计与明细共用筛选条件，只读取 POSTED；通知权限失效不影响手工账本。未来无障碍仅新增来源和解析适配，不在本轮实现或预建空方法。

**Tech Stack:** Java 11 业务代码、XML Views/ViewBinding、ViewModel/LiveData、Room 2.8.5、SQLite、WorkManager 2.12.0、java.time、JUnit4、AndroidX Test/Espresso；沿用现有 Gradle Kotlin DSL。

**Spec:** [已修订设计文档](D:/gitRepo/budgetApp/docs/superpowers/specs/2026-11-06-android-local-budget-design.md)。执行者先读设计和本计划；设计限定范围，本计划确定实施文件、接口和验收。当前只写计划，以下步骤尚未执行。

## Global Constraints

以下固定值摘自设计，所有任务共同遵守：

- “建议 Android 8.0/API 26 起；人民币单账本，账本时区固定 Asia/Shanghai。”
- “正式交易类型仅为 EXPENSE、INCOME。不设置退款类型或退款关联字段。”
- “账单状态为 POSTED、DELETED。”
- “本地主键采用 UUID 字符串。平台订单号保存为 TEXT；金额保存为 SQLite INTEGER / Java long，单位分，币种独立。”
- “仅过滤可明确识别的退款通知，避免被误记为收入。”退款通知不自动改账、删账或建立退款核对任务。
- “不为移出的功能提前建立空包、方法、服务、权限、表或字段。”导入、导出、用户备份恢复、无障碍实现、OCR、服务器、登录、同步和预算不属于本计划。
- “人工锁定类别 > 已确认商家规则 > 默认未分类。”
- “仅有通知到达时间而无法确定成交日期时进入待核对。”
- “原始通知文本默认保留 7 天。”过期原文不得继续展示或重解析；物理清理在进程下一次可运行时进行，不能承诺强停期间准点删除。
- “同一事务内写账单、可得身份、来源证据，并把事件改为 DONE；任何一步失败则回滚。”
- “不后台打开支付 App、不轮询页面、不常持唤醒锁。”不承诺全部付款实时覆盖。
- 单主用户空间、每个平台一个本地账号范围；双开、工作资料、外币和自动识别账号切换不支持。

## Review Focus

以下五类高风险输入已分配到对应任务，不能只测试正常付款：

1. **同店同额的两笔真实付款，以及手工补录后的迟到通知**：确定身份不同的两笔都保留；只有弱匹配时先核对，不静默合并。Task 7 `distinctIdsStayDistinctAndManualLateNoticeNeedsReview`。
2. **删除或改成部分退款净额后，旧金额通知重发**：结合来源证据防重，不能复活删除记录或覆盖人工金额。Task 7 `deletedAndEditedFactsSurviveReplayAfterRawPurge`。
3. **“退款成功”与聊天仿写“支付成功”**：不生成收入、不删除原消费；普通聊天不入库。Task 6 `refundAndChatAreNotPayments`，Task 9 `chatDoesNotReachDisk`。
4. **跨月迟到通知、只有发布日期或只有日期**：不拿通知到达时间冒充成交时间；可靠日期允许 DAY 精度。Task 6 `postedTimeCannotSupplyTradeDate`，Task 2 `dayPrecisionUsesLedgerZone`。
5. **工作即将结束时又收到事件、进程重启与强停**：已经落盘的待办不被一次 KEEP 调度遗漏；授权、实际连接及历史状态分开。Task 9 `enqueueDuringDrainIsNotLost`、`lastConnectionIsNotCurrentConnection`。

---

## 现有工程与执行环境

本轮读取时已经有 Android Studio Java 骨架，设计首段“未创建工程”属于此前状态；**复用现有工程，不重新生成、不改包名**。

| 已存在文件/配置 | 当前值与本计划处理 |
|---|---|
| `D:/gitRepo/budgetApp/app/build.gradle.kts:6` | namespace/applicationId=`com.example.budgetapp`，保留；minSdk=26，compileSdk/targetSdk=37，Java 源码级别 11 |
| `D:/gitRepo/budgetApp/gradle/libs.versions.toml` | AGP 9.4.1；沿用现有 AppCompat 1.6.1、Material 1.10.0、ConstraintLayout 2.1.4，不顺便全面升级 |
| `D:/gitRepo/budgetApp/gradle/wrapper/gradle-wrapper.properties` | Gradle 9.6.0，保留 wrapper 和校验值 |
| `D:/gitRepo/budgetApp/gradle/gradle-daemon-jvm.properties` | JVM 25；本机 `D:/devtools/AndroidStudio2026/jbr/bin/java.exe` 已确认是 25.0.3 |
| `D:/gitRepo/budgetApp/local.properties` | SDK 位于 `D:/devtools/Android/Sdk`；已安装 API 37.0、Build Tools 36.0.0；此文件不入 Git |
| `D:/gitRepo/budgetApp/app/src/main/java/com/example/budgetapp/MainActivity.java` | 只有模板页面，尚无业务；保留 Edge-to-edge/insets 行为并逐步替换内容 |
| Manifest 与两个备份 XML | 当前 allowBackup=true、规则为模板；Task 1 在收集任何真实通知前收紧 |
| Git/Java | 当前没有 `.git`，Java 不在 PATH；只在执行阶段建立版本控制和构建基线，本轮不初始化、不构建 |

执行时 PowerShell 会话前置设置：`$env:JAVA_HOME='D:\devtools\AndroidStudio2026\jbr'`，工作目录固定为 `D:/gitRepo/budgetApp`。下文 `./gradlew.bat` 均指现有 wrapper。Gradle 从 9.1 起支持 JVM 25；AGP 9.4 对应 Gradle 9.6、API 37、最低 JDK 17。首次执行必须实测完整构建，不能把兼容表当作构建通过。[Gradle Java 25 支持](https://docs.gradle.org/9.1.0/release-notes.html)、[AGP 9.4 兼容说明](https://developer.android.com/build/releases/agp-9-4-0-release-notes)

新增依赖分任务添加，统一在版本目录锁定：Room runtime/compiler/testing=2.8.5（Java annotationProcessor，不用 Room 3/KSP）；Work runtime/testing=2.12.0；Fragment Java artifact=1.9.1；Lifecycle viewmodel/livedata/viewmodel-savedstate=2.11.0；RecyclerView=1.4.0；JUnit=4.13.2；AndroidX Test runner/core=1.7.0、ext.junit=1.3.0、Espresso=3.7.0；arch core-testing=2.2.0。用 `activity` 替换同版本 `activity-ktx`；传递依赖如提升版本，记录实际解析结果，不创建业务 Kotlin 源码。[Room](https://developer.android.com/jetpack/androidx/releases/room)、[WorkManager](https://developer.android.com/jetpack/androidx/releases/work)、[Fragment](https://developer.android.com/jetpack/androidx/releases/fragment)、[Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle)、[RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview)、[Test](https://developer.android.com/jetpack/androidx/releases/test)

### 路径与文件职责

所有相对路径均精确相对 `D:/gitRepo/budgetApp`。为保持任务可读，下文使用四个固定路径缩写，按表严格展开，不是新模块：

- `J/` = `app/src/main/java/com/example/budgetapp/`
- `U/` = `app/src/test/java/com/example/budgetapp/`
- `A/` = `app/src/androidTest/java/com/example/budgetapp/`
- `R/` = `app/src/main/res/`

公共资源约定：每个界面任务同时修改 `R/values/strings.xml` 存放文案，不在 Java 硬编码。结果名 `SaveResult`、`WriteResult` 均指 ManualLedgerRepository 的公开嵌套静态类型；同一测试只导入所需枚举，避免 Disposition/ProcessState 的同名常量歧义。

| 任务 | 文件单元与单一职责 |
|---|---|
| 1 | `J/capture/notification/{NotificationSnapshot,NotificationPackages,DiagnosticBuffer,PaymentNotificationService}.java`：通知快照、包白名单、短时诊断缓冲、系统服务；debug Activity 只用于观测 |
| 2 | `J/processing/{LedgerTypes,Money,TransactionDraft,LedgerFilter,TimeWindows}.java`：共享值类型、精确金额、用户输入、不可变筛选、日期边界 |
| 3 | `J/data/entity/` 八个实体；`J/data/{LedgerDatabase,LocalSettings}.java`；四个 DAO：来源、账本、类别、核对；只承担持久化和本地配置 |
| 4 | `J/data/ManualLedgerRepository.java`；`J/ui/ledger/`：手工写账、列表、编辑与删除；`J/{BudgetApplication,AppContainer}.java`：进程级依赖组装 |
| 5 | `J/processing/CategoryMatcher.java`、`J/data/CategoryRepository.java`、`J/ui/category/CategoryRulesFragment.java`：规则、归档和预览重分类 |
| 6 | `J/parsing/{ParsedTransaction,ParseResult,TemplateCatalog,NotificationParser}.java`：可验证的来源解析；`J/data/EventCodec.java`：来源与候选 JSON，不引入额外 JSON SDK |
| 7 | `J/processing/TransactionProcessor.java`：事件幂等、硬校验、去重、证据和事务；禁止持有通知服务或 UI |
| 8 | `J/data/ReviewRepository.java`、`J/ui/review/{ReviewViewModel,ReviewFragment,ReviewAdapter}.java`：核对、关联、合并和显式确认 |
| 9 | `J/capture/notification/EventIntake.java`、`J/worker/{RecoveryScheduler,ProcessEventsWorker}.java`、`J/health/{NotificationHealth,NotificationHealthController}.java`、`J/ui/settings/SettingsFragment.java`：落盘调度、恢复和实际服务状态 |
| 10 | `J/data/dao/StatsDao.java`、`J/stats/StatsRepository.java`、`J/ui/overview/{OverviewViewModel,OverviewFragment}.java`：统一总额、类别和明细投影 |
| 11 | `J/stats/{ComparisonWindows,SpendingComparison}.java`：同期、环比和类别变化解释 |
| 12 | `J/ui/stats/{StatsViewModel,StatsFragment,BudgetChartView}.java`：趋势、占比、范围切换和点击下钻 |
| 13 | `J/data/RawRetention.java`、`docs/validation/`：原文清理、迁移保护、回归和本机验收报告 |

布局、测试和资源文件在各任务 Files 中给出。新增类是当前首版所需，不建立无障碍空类。一个 DTO 的关联枚举/结果类型可作为该 DTO 的嵌套静态类型，避免为了每个字段拆出接口文件。

### 共用测试与提交约定

- Task 1 增加 testing buildType（基于 debug 配置、applicationIdSuffix=`.testing`、testBuildType=`testing`）；不增加模块/flavor。仪器测试只针对 `com.example.budgetapp.testing`，与日常 `com.example.budgetapp` 数据隔离。initWith 不会复制 src/debug 源集，诊断 Activity 仍仅在 debug，不加进 testing/release。
- 本地单测：`./gradlew.bat :app:testDebugUnitTest --tests "com.example.budgetapp.<TestClass>"`；仪器测试：`./gradlew.bat :app:connectedTestingAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.budgetapp.<TestClass>`。后者要有本人授权的设备/模拟器；数据初始化只能清理 testing 安装包，不卸载或清理日常 App、支付 App。
- RED 可以是新类不存在导致编译失败，或新断言失败；先确认失败对应当前改动。之后 GREEN 才能勾选测试步骤，不能以“Gradle 任务存在”替代 PASS。
- 下列代码是测试中的关键断言；测试类自身的 setUp 按本任务列明的构造器组装数据库/对象，不在生产加入测试后门。Room 测试可允许主线程访问，生产禁止。
- 每项收尾只 stage 该任务 Files 中实际改动及必要的版本目录/资源，用 `git diff --cached --check` 检查，然后执行该项给出的 commit。不用 `git add .`，不提交 local.properties、原始真实通知、IDE/构建缓存。
- 执行前读取现有 Git 状态。若仍无 Git，先在本目录 `git init -b main` 并提交已核对的现有源文件/构建文件/设计与计划基线；若已存在仓库则沿用，不重置用户改动。隔离工作区在实施阶段按 using-git-worktrees 技能处理，不在写计划阶段创建。

## Task 1: 在现有工程中建立安全的通知观测与真机能力表

**Files:**
- Modify: `app/build.gradle.kts:5`、`gradle/libs.versions.toml`、`app/src/main/AndroidManifest.xml:5`、`R/xml/{backup_rules,data_extraction_rules}.xml`、`.gitignore`。
- Modify: `app/src/androidTest/java/com/example/budgetapp/ExampleInstrumentedTest.java` 的模板包名断言，改为与目标变体 BuildConfig.APPLICATION_ID 比较，不保留写死包名导致 testing 误失败。
- Create: `J/capture/notification/{NotificationSnapshot,NotificationPackages,DiagnosticBuffer,PaymentNotificationService}.java`。
- Create: `app/src/debug/java/com/example/budgetapp/debug/NotificationDiagnosticsActivity.java`、`app/src/debug/AndroidManifest.xml`、`app/src/debug/res/layout/activity_notification_diagnostics.xml`。
- Test: `U/capture/notification/NotificationPackagesTest.java`、`A/capture/notification/NotificationManifestTest.java`。
- Create: `docs/validation/notification-matrix.md`。

**Interfaces:**
- Consumes: 现有 MainActivity、Android NotificationListenerService，无业务数据库依赖。
- Produces: `NotificationPackages.isSupported(String packageName): boolean`，仅 `com.tencent.mm` 与 `com.eg.android.AlipayGphone` 为 true。
- Produces: 不可变 `NotificationSnapshot(String packageName,String notificationKey,long postTimeMs,long capturedAtMs,String channelId,String title,String text,String bigText,List<String> textLines,boolean groupSummary,boolean truncated,String appVersion)`，字段同参数名。可缺的文案规范成空字符串，列表防御性复制；postTime 不是交易时间。
- Produces: `DiagnosticBuffer(boolean debugBuild, Clock clock)`，`openWindow(): void`、`closeWindow(): void`、`accept(NotificationSnapshot): void`、`latest(): List<NotificationSnapshot>`；明确开启后仅内存查看，15 分钟超时/主动关闭即清空。Release 不允许开启，不写磁盘或 Logcat。
- Produces: 系统标准的 `PaymentNotificationService`；后续 Task 9 修改同一个服务，不注册第二个监听器。

- [ ] **Step 1: 建立可执行基线。** 设置会话 JDK、上述 testing 配置与变体包名断言，运行 `./gradlew.bat --version` 和 `./gradlew.bat :app:assembleDebug :app:assembleTesting :app:testDebugUnitTest`；预期 JVM 25、Gradle 9.6、BUILD SUCCESSFUL。失败先修当前环境，不顺便升级 AGP；依赖下载需权限时按执行环境流程处理。
- [ ] **Step 2: 写过滤与 Manifest 的失败测试。** 核心断言：

```java
assertTrue(NotificationPackages.isSupported("com.tencent.mm"));
assertTrue(NotificationPackages.isSupported("com.eg.android.AlipayGphone"));
assertFalse(NotificationPackages.isSupported("com.example.chat"));
assertFalse(NotificationPackages.isSupported(null));
```

仪器测试通过 PackageManager 断言监听器 exported=true 且受 `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` 保护；无无障碍组件、INTERNET、POST_NOTIFICATIONS 或全盘权限；allowBackup=false，两个备份 XML 的 cloud/device 分支排除 database、file、sharedpref、root 数据。不要把服务绑定权限错误写成普通运行时权限申请。
- [ ] **Step 3: 运行 Task 1 两个测试，确认新能力缺失而 RED。** 使用共用命令，后缀分别为 `capture.notification.NotificationPackagesTest`、`capture.notification.NotificationManifestTest`。
- [ ] **Step 4: 实现通知观测与最小配置。** 开启 ViewBinding/BuildConfig；收紧备份规则，当前 SDK 支持的跨平台传输范围也排除敏感数据并校验。只复制必要 extras，正文总上限 16,384 字符、最多 32 行，超限标 truncated；不把截断内容作为未来自动入账依据。诊断入口只在 debug manifest 注册，查看原文要本人主动开启，其他包立即丢弃。
- [ ] **Step 5: 再运行两个测试和 assembleDebug，预期全部 PASS。** 测试包的服务名用实际 class.getName 构造，不假设 Java 包名等于 applicationId。
- [ ] **Step 6: 安装 debug APK，验证系统授权与诊断关闭后的清空。** 这一步没有正式账单写入。
- [ ] **Step 7: 填写真机能力表。** 人工对照支付 App，记录机型/ROM/版本、付款路径、实际交易数、有无通知、字段与延迟、误判/漏采、是否可启用模板。真实样本只留手工脱敏、经本人核对的测试夹具；不增加产品导出入口。每个启用模板至少有两条独立真实成功样本和负例验证；没有样本或无法区分普通消息，保持不支持。无设备时此步保持未完成，可继续手工账本任务，但不得声称采集已验证。
- [ ] **Step 8: 提交。** `git commit -m "feat: add safe notification observation baseline"`。

## Task 2: 固定精确金额、日期与共享输入类型

**Files:** Create `J/processing/{LedgerTypes,Money,TransactionDraft,LedgerFilter,TimeWindows}.java`；Test `U/processing/{MoneyTest,TimeWindowsTest}.java`。

**Interfaces:**

- #### Consumes: Java 标准库，无 Android 服务/Room。
- Produces: `LedgerTypes` 嵌套枚举 Kind(EXPENSE/INCOME)、BookingState(POSTED/DELETED)、SourceType(NOTIFICATION/MANUAL)、ProcessState(NEW/DONE/REVIEW/IGNORED/ERROR)、TimePrecision(DAY/MINUTE/SECOND)、CategoryOrigin(USER/RULE/DEFAULT)。实体用同名字符串持久化，不存 ordinal。
- Produces: LedgerTypes 的未分类固定 UUID：UNCATEGORIZED_EXPENSE=`00000000-0000-0000-0000-000000000001`，UNCATEGORIZED_INCOME=`00000000-0000-0000-0000-000000000002`，分类默认值必须与收支方向一致。
- Produces: `Money.parseFen(String text): long`、`Money.formatFen(long fen): String`；无效金额抛 IllegalArgumentException。
- Produces: `TransactionDraft(Kind kind,long amountMinor,long occurredAtMs,TimePrecision timePrecision,String merchant,String provider,String accountScopeId,String categoryId,String note)`；categoryId 可空，其他输入按有效草稿校验；币种固定 CNY。
- Produces: `LedgerFilter(long startMs,long endMs,Metric metric,String categoryId,String provider)`；Metric 为 EXPENSE/INCOME/NET_OUTFLOW，两个筛选字段可空，币种/时区固定；`withCategory(String)`、`withProvider(String)`、`withRange(long,long)` 返回新实例。start=end 是空范围，start>end 拒绝；支持月初恰为零的“截至当前”。
- Produces: `TimeWindows.month(YearMonth): Range`、`inclusiveDates(LocalDate,LocalDate): Range`、`dayAnchor(LocalDate): long`，Range 字段 startMs/endMs，均 Asia/Shanghai 半开区间。

- [ ] **Step 1: 写金额与日期失败测试。**

```java
assertEquals(2880L, Money.parseFen("28.80"));
assertEquals(1L, Money.parseFen("0.01"));
assertEquals("0.00", Money.formatFen(0));
assertEquals("-2961.20", Money.formatFen(-296120L));
assertEquals(123456L, Money.parseFen("1,234.56"));
assertThrows(IllegalArgumentException.class, () -> Money.parseFen("28.800"));
assertThrows(IllegalArgumentException.class, () -> Money.parseFen("-1.00"));
assertThrows(IllegalArgumentException.class, () -> Money.parseFen("1e3"));
assertEquals(Instant.parse("2026-10-31T16:00:00Z").toEpochMilli(),
    TimeWindows.month(YearMonth.of(2026,11)).startMs);
assertEquals(Instant.parse("2026-11-04T16:00:00Z").toEpochMilli(),
    TimeWindows.dayAnchor(LocalDate.of(2026,11,5)));
```

补充零、空值、三位小数、错误千分位、long 溢出、反向日期、闰年、设备默认时区改成美国后结果不变；测试名包含 `dayPrecisionUsesLedgerZone`。
- [ ] **Step 2: 运行 `./gradlew.bat :app:testDebugUnitTest --tests "com.example.budgetapp.processing.*Test"`，预期新类型不存在或断言失败。**
- [ ] **Step 3: 实现上述五个值单元。** 金额先做十进制/千分位格式校验再 BigDecimal 精确转分，拒绝小数超过两位（包括尾零）、零、负数和指数；不通过 double。不要给业务 POJO 添加 Android Parcelable 依赖。
- [ ] **Step 4: 重跑 Step 2，预期全部 PASS。**
- [ ] **Step 5: 提交。** `git commit -m "feat: add exact money and ledger time contracts"`。

## Task 3: 建立八张表、约束、本地账号范围和数据库测试

**Files:**
- Create: `J/data/entity/{SourceEventEntity,LedgerTransactionEntity,TransactionIdentityEntity,TransactionEvidenceEntity,CategoryEntity,CategoryRuleEntity,ReviewItemEntity,ChangeLogEntity}.java`。
- Create: `J/data/dao/{SourceEventDao,LedgerDao,CategoryDao,ReviewDao}.java`、`J/data/{LedgerDatabase,LocalSettings}.java`；生成 `app/schemas/com.example.budgetapp.data.LedgerDatabase/1.json`。
- Modify: `app/build.gradle.kts`、`gradle/libs.versions.toml`。
- Test: `A/data/LedgerDatabaseTest.java`。

**Interfaces:**
- Consumes: Task 2 值类型，设计 6.2/6.3 的字段、索引和关联。
- Produces: `LedgerDatabase.open(Context): LedgerDatabase`，文件名 `budget.db`，schema version=1；`sourceEventDao()`、`ledgerDao()`、`categoryDao()`、`reviewDao()`。
- Produces: SourceEventDao `insertIgnore(SourceEventEntity): long`、`find(String id): SourceEventEntity`、`pending(int limit): List<SourceEventEntity>`；LedgerDao `find(String id): LedgerTransactionEntity`、`countPosted(): int`、`listPosted(long startMs,long endMs,String kind,String categoryId,String provider): List<LedgerTransactionEntity>`（后三项筛选可空）；其余类型化 insert/update/身份查找/证据关联 SQL 由本任务在 DAO 定义，后续任务仅扩充具体查询，不让 UI 写 SQL。
- Produces: `LocalSettings(Context)`，`accountScope(String provider): String`、`replaceAccountScope(String provider,String displayName): String`（返回新 UUID，保留旧别名）、`retentionDays(): int`、`setRetentionDays(int): void`（7 或 30）。WECHAT/ALIPAY/OTHER 首次各生成稳定范围；不保存实际账号密码。

- [ ] **Step 1: 写内存数据库失败测试。** setUp 用 Room.inMemoryDatabaseBuilder；填入设计八表，核心断言使用真实 SQL/DAO，不 mock 唯一约束：

```java
assertEquals(1, db.ledgerDao().countPosted()); // setUp 插入一笔 2880 分消费
assertEquals(2880L, db.ledgerDao().find("tx-1").amountMinor);
assertThrows(SQLiteConstraintException.class, () -> identityDaoInsertSameKey());
assertThrows(SQLiteConstraintException.class, () -> evidenceInsertMissingParent());
```

此处两项本测试类辅助动作直接执行重复 identity 插入及指向不存在 transaction 的 evidence 插入；再验证不同 scope/merchant namespace 可同编号、event_id 不能归两单、category/rule 不能硬删仍被引用项、POSTED/DELETED 过滤。另验证初建五个默认类别无重复；重复读取账号范围不变、更换产生新 UUID、旧记录不改归属。测试辅助方法只在测试类中定义。
- [ ] **Step 2: 运行 `connectedTestingAndroidTest` 的 `data.LedgerDatabaseTest`，预期数据库/实体缺失导致 RED。**
- [ ] **Step 3: 实现八实体、四 DAO、数据库和 LocalSettings。** Room runtime/compiler/testing=2.8.5，compiler 用 annotationProcessor；导出 schema 供迁移测试，不是产品导出。FK 为 RESTRICT，身份五列 NOT NULL，事件唯一键保持 source_type+event_key；不增加 import_batch 或退款字段。Room 2 无 CHECK 注解，初建回调用 INSERT/UPDATE 校验触发器保护账单 amount>0、currency=CNY、kind/state 枚举；后续真实迁移保留等价约束。生产不允许主线程 DB 操作，不启用破坏性迁移。
- [ ] **Step 4: 实现初建默认类别幂等写入。** 两类未分类使用 Task 2 常量，餐饮/交通/工资依次用末位 3/4/5 的同形式固定 UUID。未知有效类别不得隐式消失，归档代替删除。
- [ ] **Step 5: 重跑数据库测试、生成 schema 并执行 assembleDebug，预期 PASS。**
- [ ] **Step 6: 提交。** `git commit -m "feat: add transactional local ledger schema"`。

## Task 4: 交付可独立使用的手工账本、编辑删除与导航

**Files:**
- Create: `J/{BudgetApplication,AppContainer}.java`、`J/data/ManualLedgerRepository.java`、`J/ui/ledger/{LedgerViewModel,LedgerFragment,LedgerAdapter,EntryEditorFragment}.java`。
- Modify: `J/MainActivity.java:11`、`R/layout/activity_main.xml`、`R/values/strings.xml`、Manifest application。
- Create: `R/layout/{fragment_ledger,item_ledger,fragment_entry_editor}.xml`、`R/menu/bottom_navigation.xml`。
- Test: `A/data/ManualLedgerRepositoryTest.java`、`A/ui/ManualLedgerUiTest.java`、`A/ui/UiTestSupport.java`。

**Interfaces:**
- Consumes: Task 3 DB/LocalSettings，Task 2 TransactionDraft/LedgerFilter/Money。
- Produces: `ManualLedgerRepository(LedgerDatabase,Clock)`；`save(String submissionId,TransactionDraft draft,boolean confirmDistinct): SaveResult`、`update(String txId,long expectedRevision,TransactionDraft draft): WriteResult`、`delete(String txId,long expectedRevision): WriteResult`、`undoDelete(String txId,long expectedRevision): WriteResult`、`list(LedgerFilter): List<LedgerTransactionEntity>`。
- SaveResult 嵌套 Status=SAVED/DUPLICATE_WARNING/CONFLICT，字段 status、transactionId、revision、similarIds；WriteResult 嵌套 Status=APPLIED/STALE/NOT_ALLOWED，字段 status、revision。领域校验失败抛 IllegalArgumentException，页面转为字段错误，不关闭编辑页。
- Produces: `LedgerViewModel.rows(): LiveData<List<LedgerTransactionEntity>>`、`busy(): LiveData<Boolean>`；测试工具 `UiTestSupport.awaitValue(LiveData<T>,Predicate<T>): T` 用主线程订阅与最多 10 秒的测试线程等待，finally 解除订阅，不用 sleep 猜测 IO 完成，不清理日常包数据。
- Produces: `AppContainer(Context,Clock)` 持有单个 DB、单线程 IO ExecutorService、主线程 Executor 和 repository；`BudgetApplication.container(): AppContainer`；`MainActivity.showLedger(LedgerFilter): void`、`showEditor(String nullableTxId): void`。后续任务逐步补充真实页面，不创建未实现业务服务。

- [ ] **Step 1: 写 repository 失败测试。** setUp 的 draft 是 11/5、2880 分、餐饮、WECHAT、本地 scope；同 submissionId 复用，修改采用最新 revision：

```java
SaveResult a = repo.save("submission-1", draft, false);
assertEquals(a.transactionId, repo.save("submission-1", draft, false).transactionId);
assertEquals(1, db.ledgerDao().countPosted());
assertEquals(APPLIED, repo.delete(a.transactionId,a.revision).status);
assertEquals(0, db.ledgerDao().countPosted());
assertEquals(DELETED.name(), db.ledgerDao().find(a.transactionId).bookingState);
```

再测撤销仅允许最后一次用户 DELETE（不允许撤销 MERGE）、过期 revision 不覆盖、100 元改 80 元保留前值、改日期/类型、相似记录警告后可显式另记，以及事务回滚后没有半条 MANUAL 来源。收入不能接受支出类别；类型切换时 UI 清除旧类别，空类别选该方向未分类。用测试专属 SQLite 触发器制造最终写入失败，不加生产故障开关。
- [ ] **Step 2: 运行 `data.ManualLedgerRepositoryTest`，确认 RED。**
- [ ] **Step 3: 实现同步 repository（只供 IO 执行器调用）。** 手工来源事件键来自 submissionId，来源/账单/证据/日志同事务写；按方向默认未分类。修改锁定人工变更字段；逻辑删除保留身份与来源；同提交重试返回第一次账单。简单同范围、同方向、同额、同商家且日期邻近的记录给警告而非合并；Task 7 扩充历史证据匹配。
- [ ] **Step 4: 重跑 repository 测试，预期 PASS。**
- [ ] **Step 5: 写 UI 失败测试 `saveEditDeleteUndoSurvivesRecreation`。** 用 Espresso 依次点击新增、输入 28.80、保存；断言只出现一条金额 28.80；连点/重建 Activity 不额外新增；编辑成 18.80 后列表更新；删除后消失，撤销后回到一条；无通知权限也可完成。固定字段 ID 为 entry_amount、entry_date、entry_kind、entry_merchant、entry_provider、entry_category、entry_note、entry_save，列表 ID=ledger_list。使用 awaitValue 等待 rows 条数/金额，不清空非 testing 数据库。
- [ ] **Step 6: 运行 `ui.ManualLedgerUiTest`，确认 RED。**
- [ ] **Step 7: 实现界面和导航。** ViewModel 用 SavedStateHandle 保存草稿和稳定 submissionId，成功后才生成下一次提交 ID；保存期间禁用重复点击但仍以 DB 幂等兜底。用户保存即确认；表单仅选日期时默认账本当天、DAY 精度；类别为空取对应方向未分类。Fragment 只通过 ViewModel/IO 调 repository；保留 insets。其他导航页未交付前可隐藏，不放空按钮。
- [ ] **Step 8: 运行 `ui.ManualLedgerUiTest` 和 repository 测试，预期 PASS，assembleDebug 可安装并单独使用手工账本。**
- [ ] **Step 9: 提交。** `git commit -m "feat: deliver manual ledger with edit delete and undo"`。

## Task 5: 商家分类规则、类别维护和可预览的重分类

**Files:** Create `J/processing/CategoryMatcher.java`、`J/data/CategoryRepository.java`、`J/ui/category/CategoryRulesFragment.java`、`R/layout/{fragment_category_rules,item_category_rule}.xml`；Modify `J/data/ManualLedgerRepository.java`、`J/data/dao/CategoryDao.java`、`J/{AppContainer,MainActivity}.java`；Test `U/processing/CategoryMatcherTest.java`、`A/data/CategoryRepositoryTest.java`。

**Interfaces:**
- Consumes: Task 3 CategoryEntity/CategoryRuleEntity 与 DAO，Task 4 repository/IO 执行器。
- Produces: `CategoryMatcher.match(Kind kind,String merchant,String provider,String scopeId,List<CategoryMatcher.Rule> rules): Match`；Rule 是从规则和类别 JOIN 得到的嵌套值类，字段 id/version/categoryId/kind/provider/scopeFilter/matchType/pattern/priority/enabled/categoryArchived，CategoryDao 增加 `ruleRows(): List<CategoryMatcher.Rule>`。Match 字段 categoryId、origin、ruleId、conflict；无命中/同序冲突返回对应收支方向的 DEFAULT 未分类，不能将餐饮支出类别套到收入。
- Produces: `CategoryRepository.upsertRule(CategoryRuleEntity): void`、`setRuleEnabled(String id,boolean): void`、`renameCategory(String id,String name): void`、`archiveCategory(String id): void`、`previewReclassification(String ruleId): Preview`、`applyReclassification(Preview): WriteResult`；Preview 保存 ruleVersion、符合条件的 transactionId/revision 及前后类别，不包含人工锁定项。
- Produces: `MainActivity.showCategoryRules(): void`，编辑器和后续设置页复用该入口。

- [ ] **Step 1: 写分类失败测试。** 测试类 fixtures 设置“午间面馆→餐饮”、更短包含规则及同序冲突规则，断言：

```java
assertEquals(foodId, matcher.match(Kind.EXPENSE,"午间面馆","WECHAT",scopeId,rules).categoryId);
assertEquals(DEFAULT, matcher.match(Kind.EXPENSE,"未知商家","WECHAT",scopeId,rules).origin);
assertTrue(matcher.match(Kind.EXPENSE,"午间面馆","WECHAT",scopeId,conflictingRules).conflict);
assertEquals(UNCATEGORIZED_INCOME,
    matcher.match(Kind.INCOME,"午间面馆","WECHAT",scopeId,rules).categoryId);
assertEquals(0, repo.previewReclassification(ruleId).changes.size());
// 最后一条的 setUp 仅有人工锁定类别的账单。
```

补充保留“1店/2店”区别、平台/账号过滤、精确优于包含、停用规则、归档类别不用于新分配、未分类不可归档；预览后账单 revision 变化则 apply 整体 STALE。
- [ ] **Step 2: 运行 `processing.CategoryMatcherTest` 和 `data.CategoryRepositoryTest`，确认 RED。**
- [ ] **Step 3: 实现匹配与 Repository。** 使用安全规范化、优先级/匹配精度/长度排序；只在最终同序仍冲突时回默认。新增规则不改历史，手工类别优先；批量重分类按预览版本事务提交并逐条记日志，过期预览不部分应用。归档同时停用指向该类别的规则，已有账单保留关联。
- [ ] **Step 4: 接入手工编辑器的“以后同一商家都这样分类”和规则管理页。** 保存账单成功后由用户明确选项创建规则；历史重分类单独确认预览，不多弹逐笔确认。CategoryRulesFragment 只调用 Repository，不直接操作交易表。
- [ ] **Step 5: 重跑两个测试及 `ui.ManualLedgerUiTest`，预期 PASS；人工确认新增规则不会改变历史已锁类别。**
- [ ] **Step 6: 提交。** `git commit -m "feat: add merchant category rules and previewed reclassification"`。

## Task 6: 基于真机样本实现安全解析，未知模板保持不自动入账

**Files:**
- Create: `J/parsing/{ParsedTransaction,ParseResult,TemplateCatalog,NotificationParser}.java`、`J/data/EventCodec.java`、`app/src/main/assets/notification_templates.json`。
- Create: `app/src/androidTest/assets/notifications/{wechat,alipay}/` 中 Task 1 实际收集的脱敏 JSON；每个文件含 snapshot、expected 与核对依据，不制造“真实”样本文件名。
- Create: `app/src/androidTest/assets/notifications/synthetic_catalog.json`（仅供算法测试，标明合成上下文，绝不放入 main assets）；Task 7 复用此仪器测试资产，不跨 src/test 目录引用测试类。
- Test: `A/parsing/NotificationParserTest.java`、`A/parsing/NotificationGoldenTest.java`、`A/data/EventCodecTest.java`；解析/JSON 测试在 instrumentation 环境运行，不在普通 JVM 调 Android JSONObject。
- Modify: `docs/validation/notification-matrix.md`，记录模板 ID/版本/允许版本范围及支持结论。

**Interfaces:**
- Consumes: Task 1 NotificationSnapshot，Task 2 Money/枚举与时间，Task 1 真实能力表。
- Produces: `ParsedTransaction`：kind、amountMinor、currency、occurredAtMs、timePrecision、merchant、payMethod、tradeStatus、proofs、identities；缺字段可空。TradeStatus=SUCCESS/PROCESSING/FAILED/CLOSED/UNKNOWN；Proof 枚举 AMOUNT/CURRENCY/KIND/STATUS/DATE/ORIGIN。Identity 嵌套值为 idKind、namespace、externalId。
- Produces: `ParseResult`：Disposition=CANDIDATE/REVIEW/IGNORED，candidate 可空、reasonCode、parserId/version。退款是 IGNORED reasonCode=REFUND_UNSUPPORTED，不建立 REFUND kind。
- Produces: `TemplateCatalog.load(Context): TemplateCatalog`、`TemplateCatalog.fromJson(String): TemplateCatalog`、`NotificationParser(TemplateCatalog)`、`classify(NotificationSnapshot): IntakeDecision`（DROP/TRADE/SUSPECTED_TRADE）、`parse(NotificationSnapshot): ParseResult`。load 只读 main assets 的生产 catalog；fromJson 同时供加载和独立测试资产使用，坏版本拒绝。catalog 只保存模板 ID、平台/包、允许版本/频道、来源匹配条件及字段锚点，不建立脚本/插件引擎；具体锚点由 Task 1 实际样本确定，不在未知数据时编造。
- Produces: `EventCodec.encodeSnapshot(NotificationSnapshot): String`、`decodeSnapshot(String): NotificationSnapshot`、`encodeParsed(ParseResult): String`、`decodeParsed(String): ParseResult`，用 Android JSONObject，格式版本=1，坏载荷抛 IllegalArgumentException。

- [ ] **Step 1: 写 `refundAndChatAreNotPayments`、`postedTimeCannotSupplyTradeDate`。** 测试类 setUp 只在测试中构造受控模板，明确不是支付平台兼容证明；覆盖下列断言：

```java
assertEquals(IGNORED, parser.parse(refundSuccess).disposition);
assertEquals("REFUND_UNSUPPORTED", parser.parse(refundSuccess).reasonCode);
assertEquals(DROP, parser.classify(chatImitation));
assertEquals(REVIEW, parser.parse(onlyPostTime).disposition);
assertNull(parser.parse(onlyPostTime).candidate.occurredAtMs);
assertEquals(DAY, parser.parse(trustedDateOnly).candidate.timePrecision);
```

fixtures 分别给“退款成功 100.00”、普通聊天“支付成功 28.80”、只有 postTime 且跨月到达、仅可靠日期、原价/优惠/实付并存、多币种、截断、摘要通知、未知状态、非允许版本。只接受明确实付，未知金额不拿第一个数字替代。另写 EventCodec 往返和坏格式测试。
- [ ] **Step 2: 用 connectedTestingAndroidTest 分别运行 `parsing.NotificationParserTest`、`parsing.NotificationGoldenTest`、`data.EventCodecTest`，确认 RED。**
- [ ] **Step 3: 实现候选、解析与编码。** 按包/频道/模板结构/版本和明确字段锚点提取，退款/失败/非收支先排除；聊天文案不能单独证明 ORIGIN。没有可信成交日期时不从 postTime/capturedAt 补值；未知、缺字段、冲突进入 REVIEW。普通消息 preflight=DROP，不将其原文落盘。
- [ ] **Step 4: 加入 Task 1 已核对模板与 golden 测试。** 每份样本的 expected 由人工对照真实账单独立填写，断言全部金额、方向、日期精度、状态及忽略原因。只将通过真实样本、负例和来源可区分性检查的模板加入生产 catalog；无可靠样本时 catalog 明确为空，此平台仅手工/核对可用。不能用 test 模板填生产 catalog；未完成真实验证的平台保持能力表未支持。
- [ ] **Step 5: 重跑所有本任务测试，预期 PASS，验证没有“仅凭包名+关键词”自动记账路径。** synthetic 测试通过仅证明算法，不证明真实覆盖。
- [ ] **Step 6: 提交。** `git commit -m "feat: parse verified payment notifications with safe fallbacks"`。

## Task 7: 幂等入账、身份匹配和保留人工事实

**Files:** Create `J/processing/TransactionProcessor.java`；Modify `J/data/dao/{SourceEventDao,LedgerDao,ReviewDao}.java`、`J/data/ManualLedgerRepository.java`、`J/AppContainer.java`；Test `A/processing/TransactionProcessorTest.java`。

**Interfaces:**
- Consumes: Task 6 `NotificationParser.parse`、EventCodec 与 ParsedTransaction；Task 5 CategoryMatcher；Task 3 DB；Task 4 写账语义。
- Produces: `TransactionProcessor(LedgerDatabase,NotificationParser,CategoryMatcher,Clock)`、`process(String eventId): ProcessResult`；ProcessResult.Status=POSTED/LINKED/REVIEW/IGNORED/ALREADY_HANDLED，字段 transactionId、reviewId、reasonCode。
- Produces: 通知入口唯一语义：只有金额/币种/方向/成功状态/成交日期/可靠来源证明完整时可自动创建账单；类别可默认未分类。processor 不接受 Android Notification/Service，输入只有持久化事件 ID。

- [ ] **Step 1: 写端到端数据库失败测试。** setUp 可直接插入受控 SourceEventEntity，使用 Task 6 测试内模板；测试 `distinctIdsStayDistinctAndManualLateNoticeNeedsReview`：两条确定不同订单号的 2880 分消费都 POSTED；没有订单号但与手工记录相似的迟到通知进入 REVIEW，总数不变。弱匹配窗口固定为可靠时间前后 120 秒；DAY 精度比较同账本日；同时限制平台、scope、方向和金额，商家未知时仅作为更弱候选，不自动合并。
- [ ] **Step 2: 写 `deletedAndEditedFactsSurviveReplayAfterRawPurge` 与事务测试。** 删除有身份记录、清空 raw_payload 后重发同订单，仍只有 DELETED；100 元改 80 元后旧金额通知重发，结合持久化 parsed_json/修改前值匹配，不再加 100。核心断言：

```java
assertEquals(ALREADY_HANDLED, processor.process(doneEventId).status);
assertEquals(DELETED.name(), db.ledgerDao().find(deletedTxId).bookingState);
assertEquals(8000L, db.ledgerDao().find(editedTxId).amountMinor);
assertEquals(1, db.ledgerDao().countPosted());
```

再测相同事件并发处理、同槽位复用且不同订单、同额两笔用户可另记、不同 scope/namespace 不合并；精确身份字段冲突保持旧账并建核对；测试专属触发器在事件更新 DONE 时抛错，断言账单/身份/证据全回滚，移除触发器后重试仅一笔。
- [ ] **Step 3: 运行 `processing.TransactionProcessorTest`，确认 RED。**
- [ ] **Step 4: 实现事务处理顺序。** 读取/解析在事务外准备，写事务内重查事件是否终态；先排除退款/非交易和硬字段不足，再查精确身份（含 DELETED），最后查弱匹配及历史已接受来源/日志。双方同一身份命名空间有不同确定 ID 时排除彼此弱匹配。精确相同挂证据；冲突仅核对；弱匹配仅建议；真正新交易才分类入账。人工字段不被新证据覆盖，唯一冲突不使用 REPLACE。MANUAL 正常来源已原子完成，异常遗留 MANUAL NEW/ERROR 送人工核对，不走通知解析。
- [ ] **Step 5: 重跑本任务、ManualLedgerRepositoryTest 与 CategoryRepositoryTest，预期 PASS。** 验证退款事件 IGNORED 后账本无变化且无退款核对任务。
- [ ] **Step 6: 提交。** `git commit -m "feat: add idempotent booking and evidence-based deduplication"`。

## Task 8: 集中核对、关联、人工确认和安全合并

**Files:** Create `J/data/ReviewRepository.java`、`J/ui/review/{ReviewViewModel,ReviewFragment,ReviewAdapter}.java`、`R/layout/{fragment_review,item_review,dialog_review_resolution}.xml`；Modify `J/data/dao/ReviewDao.java`、`J/MainActivity.java`、`J/AppContainer.java`、`R/menu/bottom_navigation.xml`；Test `A/data/ReviewRepositoryTest.java`、`A/ui/ReviewUiTest.java`。

**Interfaces:**
- Consumes: Task 7 持久化核对原因/候选；Task 4 的校验、版本、人工字段锁和日志约定。
- Produces: `ReviewRepository(LedgerDatabase,Clock)`；`listOpen(): List<ReviewItemEntity>`、`confirmNew(String reviewId,TransactionDraft corrected): WriteResult`、`linkExisting(String reviewId,String txId): WriteResult`、`acceptCorrection(String reviewId,String txId,long revision,TransactionDraft corrected): WriteResult`、`ignore(String reviewId): WriteResult`、`merge(String primaryId,long primaryRevision,String duplicateId,long duplicateRevision): WriteResult`。
- Produces: ReviewViewModel 的 `LiveData<List<ReviewItemEntity>> rows()` 和异步操作结果；所有操作 IO 执行、一个事务关闭核对并更新来源/身份/证据。

- [ ] **Step 1: 写 repository 失败测试 `reviewResolutionIsIdempotentAndMergeCannotBeUndoneAsDelete`。** 对缺日期候选补全后确认一笔；再次 confirm 不加账；link 只挂证据；不同确定身份允许另记；精确身份冲突不能被 confirmNew 绕过；被删主单不被 link 自动恢复。merge 后主单保留、来源/身份转移、重复单 DELETED，日志 action=MERGE；Task 4 undoDelete 不允许复活 MERGE 记录。各场景 setUp 独立，关键断言为：

```java
assertEquals(APPLIED, reviewRepo.linkExisting(reviewId,txId).status);
assertEquals(1, db.ledgerDao().countPosted());
assertEquals(DELETED.name(), db.ledgerDao().find(duplicateId).bookingState);
assertEquals(NOT_ALLOWED, manualRepo.undoDelete(duplicateId,mergedRevision).status);
```
- [ ] **Step 2: 运行 `data.ReviewRepositoryTest`，确认 RED。**
- [ ] **Step 3: 实现核对事务。** 接受修正是用户显式编辑，允许用户选择覆盖对应字段并更新锁；过期 revision 返回 STALE 不部分应用。合并相同币种/方向且预览双方金额和证据，拒绝同 ID、自相矛盾或归属不明的身份转移，不能静默丢弃唯一键冲突。
- [ ] **Step 4: 写 `ui.ReviewUiTest.resolveWithoutDuplicateBooking`。** 用户看到原因与对照值；“关联已有”后数不变；“确认另一笔”后增加一笔；忽略后候选消失但账本不变；无退款分组。固定 UI ID=review_list、review_reason、review_link、review_new、review_ignore；断言点击 review_link 后 `db.ledgerDao().countPosted()==1`，再通过 awaitValue 验证核对列表已更新。
- [ ] **Step 5: 运行 ReviewUiTest，确认 RED。**
- [ ] **Step 6: 实现页面、徽标与预览确认。** ReviewViewModel 增加 `busy(): LiveData<Boolean>`，UI 测试使用 Task 4 等待工具，不靠定时 sleep；让用户看清关联依据后执行对应事务。
- [ ] **Step 7: 重跑 ReviewRepositoryTest、ReviewUiTest，预期 PASS。**
- [ ] **Step 8: 提交。** `git commit -m "feat: add centralized transaction review and safe linking"`。

## Task 9: 接通实时采集、持久化恢复和真实连接状态

**Files:**
- Create: `J/capture/notification/EventIntake.java`、`J/worker/{RecoveryScheduler,ProcessEventsWorker}.java`、`J/health/{NotificationHealth,NotificationHealthController}.java`、`J/ui/settings/SettingsFragment.java`、`R/layout/fragment_settings.xml`。
- Modify: `J/capture/notification/PaymentNotificationService.java`、`J/{BudgetApplication,AppContainer,MainActivity}.java`、`J/data/dao/SourceEventDao.java`、Manifest、版本目录与 app build。
- Test: `A/capture/notification/EventIntakeTest.java`、`A/worker/EventRecoveryTest.java`、`U/health/NotificationHealthControllerTest.java`。

**Interfaces:**
- Consumes: Task 1 快照/服务，Task 3 LocalSettings/事件表，Task 6 preflight 与编码，Task 7 processor。
- Produces: `EventIntake(LedgerDatabase,NotificationParser,LocalSettings,RecoveryScheduler,ExecutorService)`、`accept(NotificationSnapshot): void`；IO 中先 preflight，再生成 source_key/落盘，然后调度处理。事件摘要键包括 source、provider、scope、notificationKey、postTime 和规范化载荷 hash，key 的格式版本=1。
- Produces: `RecoveryScheduler.schedule(): void`：唯一名称 `process-source-events`，OneTimeWorkRequest + APPEND_OR_REPLACE；不创建 PeriodicWorkRequest。`ProcessEventsWorker(Context,WorkerParameters)`，测试通过 WorkerFactory 使用包内注入 processor/db 的构造器，不添加全局测试 setter。
- Produces: `NotificationHealth` 不可变状态（authorized、connection、lastConnectedAtMs、lastEventAtMs、lastParsedAtMs、pendingCount、errorCode）；Connection=UNKNOWN/CONNECTING/CONNECTED/DISCONNECTED。`NotificationHealthController(Clock,Long nullableLastConnectedAtMs)` 的 `onAuthorization(boolean)`、`onConnected()`、`onDisconnected()`、`onEvent()`、`snapshot(): NotificationHealth`，进程初始 UNKNOWN；持久化历史时间不恢复 CONNECTED。

- [ ] **Step 1: 写 `chatDoesNotReachDisk`、`enqueueDuringDrainIsNotLost`、`lastConnectionIsNotCurrentConnection` 失败测试。** 普通聊天 preflight DROP 后 source_event 无新增；同事件并发 accept 只有一条。Worker 读空/收尾的同时再插入 NEW 并 schedule，后继工作仍处理该事件。重建 health controller 时即便有 lastConnectedAt 也不是 CONNECTED；授权 true 不代表已连接。

```java
NotificationHealthController health = new NotificationHealthController(clock,123L);
health.onAuthorization(true);
assertNotEquals(CONNECTED, health.snapshot().connection);
health.onConnected();
assertEquals(CONNECTED, health.snapshot().connection);
```
- [ ] **Step 2: 运行上述三个测试类，确认 RED。**
- [ ] **Step 3: 实现采集到调度。** 系统主线程只快照/入队，单 IO 执行器做过滤、落盘和调度；入库成功前不显示已记账。普通聊天不落盘，明确退款仅必要原因。采用 APPEND_OR_REPLACE 避免 KEEP 在 Worker 收尾时吞掉新调度；Worker 每批取 100 条 NEW/ERROR、直到无剩余；永久解析不支持转 REVIEW，暂时 DB/IO 错误指数退避，单次工作最多 3 次重试后报告失败，不做周期轮询。进程在落盘与调度间死亡由下次启动/连接扫描补调度；从未落盘的通知不能补回。[Work 调度策略](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy)
- [ ] **Step 4: 实现服务连接和设置页。** 标准回调更新 health；连接后才读取当前活动通知，仍走同一去重入口；已授权断连时最多每 30 秒一次由实际回调或用户按钮触发重绑，不运行定时重绑循环。设置页分别显示授权、当前连接、最近事件/解析、积压/错误、支付 App 版本、本地账号范围；更换范围新建 UUID，不改历史账单。Manifest 的 queries 只声明两个目标包；引导系统授权，无权限可继续手工。
- [ ] **Step 5: 测试重启与竞态。** 进程重建后扫描 NEW、清除已有 work 后重新调度仍幂等；拒绝权限无重绑；无新交易不误报断连；强停需用户重新交互，不能用广播/闹钟绕开。用测试 WorkManager/内存数据库执行确定性竞态测试，再人工测实际服务；不通过删除 App 数据模拟保留账本的重启。
- [ ] **Step 6: 重跑本任务和 Task 7/8 仪器测试，预期 PASS；用一笔已验证真实支付确认“通知→正式账本/待核对”路径。** 该平台暂无可用通知时记录不支持结论，不改用其他采集方式补成绩。
- [ ] **Step 7: 提交。** `git commit -m "feat: connect notification intake recovery and health status"`。

## Task 10: 统一统计、明细筛选和月度总览

**Files:** Create `J/data/dao/StatsDao.java`、`J/stats/StatsRepository.java`、`J/ui/overview/{OverviewViewModel,OverviewFragment}.java`、`R/layout/fragment_overview.xml`；Modify `J/data/LedgerDatabase.java`、`J/ui/ledger/{LedgerViewModel,LedgerFragment}.java`、`J/{AppContainer,MainActivity}.java`、`R/menu/bottom_navigation.xml`；Test `A/stats/StatsRepositoryTest.java`、`A/ui/OverviewUiTest.java`。

**Interfaces:**
- Consumes: Task 2 LedgerFilter/TimeWindows，Task 3 ledger/category 数据，Task 4 `MainActivity.showLedger(LedgerFilter)`。
- Produces: `StatsDao.totals(long startMs,long endMs,String categoryId,String provider): TotalsRow`（expenseFen/incomeFen）、`expenseCategories(long startMs,long endMs,String categoryId,String provider): List<CategoryRow>`（categoryId/name/amountFen）。筛选字段可空，SQL 固定 CNY、POSTED，空聚合返回 0。
- Produces: `StatsRepository(LedgerDatabase,Executor)` 的 `load(LedgerFilter): Snapshot`、`observe(LedgerFilter): LiveData<Snapshot>`。Snapshot 字段 filter、expenseFen、incomeFen、netOutflowFen、valueFen、categories、entries；CategoryAmount 字段 categoryId/name/amountFen/sharePercent；Entry 字段 transaction、contributionFen，均为嵌套静态值类。
- Produces: 值类构造器 `Snapshot(LedgerFilter,long expenseFen,long incomeFen,List<CategoryAmount>,List<Entry>)`（计算 netOutflowFen/valueFen）、`CategoryAmount(String categoryId,String name,long amountFen,BigDecimal nullableSharePercent)`、`Entry(LedgerTransactionEntity,long contributionFen)`。字段公开只读，列表防御复制；供后续纯比较测试构造输入，不依赖数据库。
- E/I 在相同时间/类别/渠道范围计算；metric 只决定 valueFen 及明细种类。EXPENSE 的明细只有支出且贡献为正，INCOME 只有收入且为正，NET_OUTFLOW 含两类并对收入取负；valueFen 分别 E/I/E−I。

- [ ] **Step 1: 写统计失败测试。** setUp 插入 11 月餐饮支出 2880、交通 1000、收入 300000、已删除支出 9999，另有一条待核对但未入账的来源；餐饮为有效未分类时也必须计入。核心断言：

```java
Snapshot s = repo.load(novemberNetFilter);
assertEquals(3880L, s.expenseFen);
assertEquals(300000L, s.incomeFen);
assertEquals(-296120L, s.netOutflowFen);
assertEquals(s.valueFen, s.entries.stream().mapToLong(e -> e.contributionFen).sum());
assertEquals(s.expenseFen, s.categories.stream().mapToLong(c -> c.amountFen).sum());
```

再测 provider/category 过滤、上下月边界、空范围、删除/撤销/改日期、归档类别历史仍显示。构造两笔使 SUM 溢出的测试，预期明确统计错误，不返回绕回的金额或转浮点凑结果。
- [ ] **Step 2: 运行 `stats.StatsRepositoryTest`，确认 RED。**
- [ ] **Step 3: 实现只读 DAO 与一致性快照。** totals、类别和明细在同一个 Room 读取事务内加载；long 精确求和，净额用精确减法，溢出报告错误。observe 在有效观察者期间监听实际账单/类别变更并在后台重查，撤除观察时解除；不增加定时刷新。当前月总览结束边界取 min(月末,本次查询 now)，不把未来手工记录算入“截至当前”。
- [ ] **Step 4: 写总览与下钻失败测试。** 固定时钟下总览显示 E/I/N；点支出进入 EXPENSE 明细，点收入进入 INCOME，点净支出进入 NET_OUTFLOW；导航后筛选时区/类别/渠道不丢。记录 query filter 并断言明细合计等于被点金额，更新账单后总览自动变化。
- [ ] **Step 5: 运行 `ui.OverviewUiTest`，确认 RED。**
- [ ] **Step 6: 实现 OverviewViewModel/Fragment 与筛选下钻。** 卡片文案“消费”“收入”“净支出”，错误态“数据异常，暂无法统计”；零记录为“暂无已记支出”，不写“本月没有消费”。
- [ ] **Step 7: 重跑两个测试至 PASS，验证列表和总览没有各自不同的过滤逻辑。**
- [ ] **Step 8: 提交。** `git commit -m "feat: add consistent monthly totals and bill drilldown"`。

## Task 11: 实现环比、完整自然日同期和类别变化解释

**Files:** Create `J/stats/{ComparisonWindows,SpendingComparison}.java`；Modify `J/stats/StatsRepository.java`、`J/ui/overview/{OverviewViewModel,OverviewFragment}.java`、`R/layout/fragment_overview.xml`、`A/ui/OverviewUiTest.java`；Test `U/stats/{ComparisonWindowsTest,SpendingComparisonTest}.java`、`A/stats/SpendingComparisonQueryTest.java`。

**Interfaces:**
- Consumes: Task 2 Range/LedgerFilter；Task 10 Snapshot/categories，比较两个期间均使用 EXPENSE 指标和同一类别/渠道范围。
- Produces: `ComparisonWindows.completedMonth(YearMonth): Pair`、`monthToDate(Clock): Optional<Pair>`；Pair 的 current/previous 都为 TimeWindows.Range，字段 comparedDays。monthToDate 使用 k=min(d−1,上月天数)，第 1 日返回 empty。
- Produces: `SpendingComparison.calculate(Snapshot current,Snapshot previous): Result`；Result 字段 deltaFen、nullable percent、label、categoryDeltas、explanation；CategoryDelta 含 categoryId、deltaFen。百分比为 BigDecimal，保留 2 位、HALF_UP；零基数用“新增 X 元”或“无变化”。净支出不计算百分比。
- Produces: `StatsRepository.compare(LedgerFilter scope,ComparisonWindows.Pair pair): SpendingComparison.Result`，两个期间在同一读取快照查询；解释按金额增减排序，显示最多三项增加、三项减少与精确余项，不算不必要的贡献率。

- [ ] **Step 1: 写区间与解释失败测试。**

```java
Clock clock = Clock.fixed(Instant.parse("2026-11-06T04:00:00Z"), ZoneOffset.UTC);
Pair p = ComparisonWindows.monthToDate(clock).get();
assertEquals(5, p.comparedDays);
assertEquals(Instant.parse("2026-11-05T16:00:00Z").toEpochMilli(), p.current.endMs);
assertEquals(Instant.parse("2026-10-05T16:00:00Z").toEpochMilli(), p.previous.endMs);
assertEquals(10000L, SpendingComparison.calculate(current1100,previous1000).deltaFen);
assertEquals(new BigDecimal("10.00"), SpendingComparison.calculate(current1100,previous1000).percent);
```

setUp 的 previous1000 为餐饮 60000 分+交通 40000 分，current1100 为 80000+30000，断言解释包含餐饮 +200.00、交通 −100.00、合计 +100.00。另测月初 empty、3/31 对非闰年 2 月仅前 28 天、闰年 29 天、跨年、类别新增消失、总变化为零、上月零、两个区间始终保持相同筛选。
- [ ] **Step 2: 运行 `stats.ComparisonWindowsTest`、`stats.SpendingComparisonTest`、`stats.SpendingComparisonQueryTest`，确认 RED。**
- [ ] **Step 3: 实现计算与一致查询。** 使用账本时区而非 Clock.zone；本月总额与“前 k 个完整日”分别展示，不把当日或多出的天数混入比较；重分类后两个期间按当前类别映射重算。解释只陈述金额，不生成未经证据支持的消费原因。
- [ ] **Step 4: 接入总览同期卡片。** 明示“前 k 天/截止日期”，第 1 天显示“暂无完整日可比”；已经结束月份显示整月环比；收入/净支出仅展示变化金额。增加总览 UI 断言，不能出现未结束月对上月整月的同进度百分比。
- [ ] **Step 5: 重跑本任务与 OverviewUiTest，预期 PASS。**
- [ ] **Step 6: 提交。** `git commit -m "feat: add comparable spending periods and category explanations"`。

## Task 12: 交付趋势、分类占比、自选范围和可点击图表

**Files:** Create `J/ui/stats/{StatsViewModel,StatsFragment,BudgetChartView}.java`、`R/layout/{fragment_stats,item_stats_value}.xml`；Modify `J/stats/StatsRepository.java`、`J/{MainActivity,AppContainer}.java`、`R/menu/bottom_navigation.xml`；Test `A/stats/TrendQueryTest.java`、`A/ui/StatsUiTest.java`。

**Interfaces:**
- Consumes: Task 10 StatsRepository、Task 11 比较、Task 4 `MainActivity.showLedger(LedgerFilter)`。
- Produces: `StatsRepository.trend(LedgerFilter scope,YearMonth endMonth,int monthCount,Clock clock): List<TrendPoint>`；monthCount 只允许 6/12，endMonth 为当前账本月或历史月；TrendPoint 含 YearMonth、valueFen、filter、inProgress。整个序列在同一读取事务生成，当前月截至一次捕获的 now，空月份补零并标已记范围。
- Produces: `BudgetChartView.setItems(List<ChartItem>,OnItemSelectedListener): void`；ChartItem 含 label、valueFen、filter，监听器 `onSelect(LedgerFilter): void`；view 包内 `hitTest(float x,float y): int` 返回项目索引或 −1。只负责绘图/命中，不计算财务金额。
- Produces: StatsViewModel 用 SavedStateHandle 保存月份、6/12 月选择、自选起止日、类别/渠道；自选范围直接用同一 load/filter 查询，不用 trend 的 6/12 限制。

- [ ] **Step 1: 写趋势查询失败测试。** 6 月份必须恰好 6 点、12 月份恰好 12 点，跨年/无记录补零；当月未来记录不计“截至当前”，历史月份完整。每个点执行 `repo.load(point.filter).valueFen` 必须等于 point.valueFen；自选含起止日且不重复跨日零点。
- [ ] **Step 2: 运行 `stats.TrendQueryTest`，确认 RED。**
- [ ] **Step 3: 实现 trend 和 StatsViewModel。** 月份边界复用 TimeWindows，scope 的指标/类别/渠道不丢；类别变化复用相同序列加类别过滤。不建月缓存表，不让绘图库决定钱数。
- [ ] **Step 4: 写 StatsUiTest 失败测试。** 点餐饮条形进入相同期间/渠道的餐饮明细；点某月趋势点进入该月明细；切 6/12 月和 Activity 重建后筛选保留；自选日期结果正确；E=0 不显示无意义占比；类别占比只对支出计算。直接触摸 chart 的有效区域与空白区，分别回调正确 filter/不回调，数据列表提供等价点击路径。
- [ ] **Step 5: 运行 StatsUiTest，确认 RED。**
- [ ] **Step 6: 实现页面与图表。** 使用柱状/折线及排序类别条形即可，不强制圆环；金额取整数原值，绘图才用浮点。提供标签、空态、点击数据列表、适合大字体的布局，不开发任何无障碍服务。新增文案集中到 strings.xml。
- [ ] **Step 7: 重跑两个测试及 OverviewUiTest，预期 PASS；最终底部导航为总览、账单、统计、待核对，设置从总览可达。**
- [ ] **Step 8: 提交。** `git commit -m "feat: add spending trends category charts and precise drilldown"`。

## Task 13: 原文保留、迁移保护、整体回归与真机交付

**Files:** Create `J/data/RawRetention.java`、`docs/validation/{acceptance-report,notification-runbook}.md`；Modify `J/data/{LedgerDatabase,LocalSettings}.java`、`J/worker/ProcessEventsWorker.java`、`J/ui/settings/SettingsFragment.java`、`R/layout/fragment_settings.xml`、`R/values/strings.xml`、Manifest/备份 XML（仅在集成检查发现偏差时）、`docs/validation/notification-matrix.md`；Test `A/data/{RawRetentionTest,DatabaseUpgradeSafetyTest}.java`、`A/ui/EndToEndLedgerTest.java`。

**Interfaces:**
- Consumes: Task 3 schema/LocalSettings；Task 7 幂等与人工锁定；Task 9 生命周期入口；Task 10—12 统一统计。
- Produces: `RawRetention(LedgerDatabase,LocalSettings,Clock)` 的 `purgeExpired(): int`、`extendEvent(String eventId,int days): void`（7/30）、`clearRaw(String eventId): void`。事件保留期限以 capturedAt 为基础；单条延期写入 source_event 的 `raw_expires_at_ms`（本次首次增加该列须创建真实 1→2 迁移及对应 schema，不覆盖原 schema）。只清原文，保留 parsed_json、摘要、身份、删除标记和最小证据。
- Produces: 首次真实迁移 1→2，添加可空 raw_expires_at_ms，老行默认按 capturedAt+本地保留天数解释；8 表其余事实不改。版本=2，迁移用原始固定 SQL，不引入字段常量造成历史迁移漂移。

- [ ] **Step 1: 写保留与迁移失败测试。** 固定 Clock 验证 7 天边界、30 天延期、原文过期不展示/不重解析、清理后身份去重仍可用。用 Task 3 导出的 v1 schema 建真实测试库，插入 POSTED、DELETED、人工锁、待核对及多来源证据，打开 v2 后逐项一致：

```java
assertNull(db.sourceEventDao().find(expiredEventId).rawPayload);
assertNotNull(db.sourceEventDao().find(expiredEventId).parsedJson);
assertEquals(DELETED.name(), db.ledgerDao().find(deletedTxId).bookingState);
assertEquals(8000L, db.ledgerDao().find(editedTxId).amountMinor);
```

额外复制测试库并标为未知未来版本，生产打开必须失败且原始数据仍存在，不能 fallback 删除；这个未知版本只在测试文件设置，不创建虚假生产迁移。同期写 EndToEndLedgerTest：100 元改 80 元、purgeExpired 后原金额重发仍一笔 80；手工删后重发不复活；撤销后恢复一笔；收入 3000 元时 E=80、I=3000、N=−2920，退款无动作，点击明细合计一致，无权限可操作。断言 `expenseFen==8000L`、`netOutflowFen==-292000L`；只用本 App 受控事件。
- [ ] **Step 2: 运行 RawRetentionTest、DatabaseUpgradeSafetyTest、EndToEndLedgerTest，确认新保留/迁移能力缺失而 RED。**
- [ ] **Step 3: 实现清理与真实迁移。** App 打开、实际服务重连、已安排 Worker 开始时按需清理，不安排每日轮询；过期、缺原文的未解决事件保留结构化候选并提示只能人工补全。设置页支持默认 7/30 天、单条延期或清除原文，默认 7；关闭进程期间无法保证物理清理时点，下一次可运行即处理。schema 升至 v2 并提交两版 schema 和迁移测试。
- [ ] **Step 4: 接通过期读取保护。** 核对/诊断页读取原文前检查期限；解析任务发现原文过期且无可用解析结果时转人工补全，不重新运行旧文本解析。结构化事实、人工锁和删除状态保持不变。
- [ ] **Step 5: 重跑本任务三个测试，预期 PASS。** 不通过放宽字段证明、自动合并弱匹配或改变退款范围让测试过。
- [ ] **Step 6: 执行整体自动验证。** `./gradlew.bat :app:testDebugUnitTest :app:connectedTestingAndroidTest :app:lintDebug :app:assembleDebug :app:assembleRelease`。预期 BUILD SUCCESSFUL、所有测试通过、无未处理 lint error；检查 release merged manifest 没有 debug 诊断 Activity、无障碍、INTERNET、文件导入/导出组件，通知服务受绑定权限保护。检查最终备份规则，不把库升级误当成用户备份恢复。
- [ ] **Step 7: 完成连续至少 7 天的本人真机验收。** 逐笔人工对照支付 App，记录真实成功付款数、自动正确入账数、错误数、漏采数和待核对数，分平台/模板列覆盖；误记率不能拿待核对伪装，未测路径写“未验证”。分别测回收/划走/强停、重启解锁、权限关闭、隐私隐藏、深度待机、升级保留数据；比较同机空闲耗电，确认无周期扫描。没有足够真实样本时本步保持未完成，不编造达标数据。
- [ ] **Step 8: 写交付报告与运行说明。** 记录构建/测试结果、SDK/设备/App/模板版本、未支持路径和人工补录方式；记录 APK 路径 `app/build/outputs/apk/debug/app-debug.apk` 与安装版本。安装使用 `adb install -r` 保留数据，不执行卸载/清数据；多设备时明确指定本人授权的目标。签名材料不入库。说明卸载/清数据无本 App 恢复入口；本轮没有导入、导出、退款账务或无障碍实现。
- [ ] **Step 9: 提交。** `git commit -m "feat: harden retention migrations and device acceptance"`。只有真实测试完成才勾完本任务和宣布首版验收通过。

## 依赖顺序与里程碑

按任务编号推进。Task 1 的真实样本采集可在自然使用中持续进行；缺设备/缺样本不会阻塞 Task 2—5 的手工账本，但 Task 6 不得启用未经验证模板，Task 9/13 的真实覆盖验收不能提前勾选。

| 里程碑 | 任务 | 可独立验证的结果 |
|---|---|---|
| 通知可行性 | 1 | 安全观测工具与真实能力表；不承诺自动覆盖 |
| 本地可用账本 | 2—5 | 无权限、离线也能手工增改删、分类并保留去重依据 |
| 通知自动化闭环 | 6—9 | 已验证模板入账，未知项核对，重发/重启不重复，显示真实连接状态 |
| 统计完整与可维护 | 10—13 | 月度/类别/趋势/同期/解释/下钻一致，保留期与迁移验证、本人真机验收 |

这些任务属于同一 App 的相互依赖链，无须拆成互不依赖的子项目。所有自动化承诺以能力表为边界；未来无障碍和预算另立需求，不作为本轮收尾补项。

## 自审记录与需求覆盖

本节是对计划的检查记录，不代表已经执行代码或测试。自审由当前编写者完成，不派发代理。

| 设计部分 | 计划覆盖 |
|---|---|
| 1—3 范围、可行性、通知+手工降级 | Global Constraints、Task 1/4/6/9；其余采集与文件功能明确排除 |
| 4—5 架构、依赖与 Java 技术栈 | 文件职责表、Task 1—3/6/7/9；不预建无障碍方法 |
| 6 八表、精度、日期、索引、身份唯一性 | Task 2/3，真实迁移在 Task 13 |
| 7 付款、手工、去重、分类、删除、人工锁定、恢复 | Task 4—9；删除/改金额影响原期间，不记退款收入 |
| 8 月/类别/趋势/环比/同期/解释/下钻 | Task 10—12；E/I/N，预算明确延后 |
| 9 页面与减少操作 | Task 4/5/8/9/10/12；手工保存一次、默认静默、未分类不阻塞 |
| 10 授权/后台/隐私/保留/升级 | Task 1/9/13；真实连接、无循环保活、系统备份排除、原文保留 |
| 11—12 阶段与验收风险 | Task 1/13 能力表和真机报告，五项 Review Focus 分别落到测试 |

检查要点：值类型与结果类型有唯一所有者；候选不等于正式账单；worker 输入是事件 ID；手工不走通知文本解析；图表直接携带相同 LedgerFilter；类型和字段不引入 REFUND、import_batch 或 ACCESSIBILITY 实现。临时测试辅助函数只定义在测试类内，不作为生产占位接口。计划中的文件导出仅指 Room schema 的开发工件，不是产品文件功能。

## 执行交接

建议 **Native：在当前会话按任务实施**，因为数据模型、去重、核对和统计接口紧密关联，个人项目优先减少每项重新交接的成本。也可选择 Subagent-driven：每项由新实现者及独立审阅者检查，成本更高但检查更细。

先请用户审阅本计划并选择执行方式。选 Native 后使用 superpowers:executing-plans；选 Subagent-driven 后使用 superpowers:subagent-driven-development。未得到该交接确认前，本轮只交付计划，不修改工程源码或安装依赖。git remote add origin https://github.com/higherthandeer/budgetApp.git

