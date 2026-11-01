# XATUHelper-Android

西安工业大学校园助手的独立原生 Android 工程。使用 Kotlin、Jetpack Compose 和官方 Material Design 3 组件，以 iOS XATUHelper 的业务行为为参考。工程不依赖 Swift、Skip 或原 iOS 工程。

> iOS App 与 Widget 请前往 [XATUHelper](https://github.com/Kelejun/XATUHelper)。本仓库维护原生 Android 版，替代原 Skip 迁移版本。

## 当前功能（1.0.3）

- MD3 登录、首页、课程表、校园导航，动态配色与深色模式。
- 统一身份认证密码登录；需要验证码或动态码时使用内置学校网页。
- OkHttp 与 WebView 共享 Cookie，支持 HttpOnly、业务路径与加密会话快照；旧 WebView 保留网络响应 Cookie 的原始属性，恢复时保持主机／域范围。
- 账号密码通过 Android Keystore 的 AES-GCM 加密保存在本机；退出登录清理凭据、Cookie 与课表缓存。
- 学期列表、学生／班级课表、滑动翻周、前后周切换、直接跳周、返回本周／当前学生课表、持久保存周末显示。
- 连续节次合并、课程重叠分栏、课程详情、第一周日期校准。
- 课表使用独立的 12 色课程配色，按整学期课程分配；12 门以内不重复，同一课程跨周保持同色，超过 12 门循环使用。使用 Material 官方 HCT 色彩模型（chroma 36），浅色容器／文字为 tone 90／10，深色为 30／90，搭配同色系细色条；不受系统壁纸配色影响。色值由 `scripts/GenerateCoursePalette.java` 生成，无额外运行时依赖。
- Room 按账号、学期和课表类型保存快照；优先显示缓存，失败保留旧数据。
- 首页展示当天课程及进行状态、上完看明天；历史学期不充当今日安排，学期开始前不显示虚假的第一周课程。课表缓存超过 168 小时后尝试刷新。

- 考试安排：按学期查看时间、考场和座位，首页显示已缓存的下一场考试。
- 成绩：当前／历史学期和所有学期、搜索、分项成绩与教务绩点汇总；兼容尚未公布成绩的空表。
- 学业完成情况：毕业学分、模块展开、课程搜索、开课学期筛选、课程性质和学时。
- 教务数据按账号、类型和学期隔离缓存；新考试、新成绩变化以应用内提示展示。
- 设置页集中账号、周次校准、周末、电费自动刷新和诊断复制；退出账号只出现在设置页；登录页采用校园助手整体介绍。

- 电费：按项目、校区、楼栋、房间绑定宿舍，余额查询、每日记录（每宿舍最近 400 天）、近 7 天扣费／余额趋势、有效日均扣费和充值网页入口。
- 校园网页：网费、电费充值、打卡、请假、教务和一站式大厅；打卡页按需请求位置权限。
- 校历：官方原图下载、本机中文 OCR、离线原图和日期缓存；展示寒暑假范围，暑假以图中最后一天结束。
- 提醒：上课提醒、考试前一晚／当天早晨／考前提醒，系统权限状态和开机恢复；默认关闭，真实账号首次进入课表／考试页提供一次提醒引导。
- 桌面小组件：课程（切换今天／明天）、电费（主动刷新）和假期，点击进入对应页面。

已绑定宿舍的页面只保留低强调的解除绑定入口；选择宿舍仅在未绑定时出现。

从 0.1.0／0.2.0 更新使用 Room 1→2→3 增量迁移，保留账号、凭据和课表，不需要重新安装或清除数据。

## 构建

需要 JDK 17 或 21、Android SDK 36。最低支持 Android 8.0（API 26）。依赖版本固定，便于复现；Gradle Wrapper 已包含。

1. 用 Android Studio 打开本目录，配置本机 SDK，或设置 `ANDROID_HOME`。
2. 如需要，创建被 Git 忽略的 `local.properties`：`sdk.dir=/你的/Android/sdk`。
3. 执行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`；开启 R8 和资源压缩，需要你自己的发布签名后才能分发。

Debug 与 Release 交付包均无示例账号入口。示例数据仅用于独立 QA／Benchmark 自动化测试包，不作为交付安装包。旧调试版本已保存的示例账号在交付版本启动时清理，真实账号不受影响。

设置页显示 `版本号 (Git 短 commit)`，并提供 [GitHub 仓库](https://github.com/Kelejun/XATUHelper-Android)入口。构建时自动从 Git HEAD 读取 commit；源码压缩包没有 Git 历史时显示 `unknown`。交付前先提交最终源码，再构建和签名，确保版本对应实际提交。`scripts/package-release.py` 会拒绝 commit 与 HEAD 不一致的安装包。

## 登录与离线使用

填写学号、统一认证密码后点击「登录」。网页登录前同样需要填写学号，用于区分本地课表；请在网页中使用同一个学号，成功进入教务后点击「完成登录」。网页登录不保存你在网页输入的密码。

周次优先使用本地校准日期、学期起始日期或已有缓存；当前学期缺少日期时从教务首页的官方教学周推算。取不到日期时会明确提示，可在登录后通过课表页或设置页校准第一周日期。不会硬编码某个学期的开学日期。

断网和登录失效时仍保留已有课表。首次选择没有缓存的历史学期或班级课表需要联网；缺少开学日期时需要校准。系统时间计算统一使用 Asia/Shanghai。

## 结构

```text
app/src/main/java/cn/edu/xatu/helper/
  data/       CAS、Cookie、网络、解析、Room、DataStore、Keystore、Repository
  model/      课程、学期、周次、作息与重叠布局
  ui/         MD3 界面、ViewModel、校园 WebView
  platform/   AlarmManager 提醒、WorkManager 维护、AppWidget
app/src/test/         解析、周次、AES 和 HTTP 协议测试
app/src/androidTest/ 原生交互、完整数据流程、存储和 Cookie 测试
app/schemas/          Room 数据库版本
performance/          独立进程的优化构建 UIAutomator 检查
```

网络、HTML 解析和存储在后台执行；Compose 只消费不可变状态。按周过滤后再合并节次，避免单双周课程互相污染。认证串行处理，自动恢复最多每分钟一次、每小时三次；浏览页面不会触发反复登录。

教务系统及校园网自助平台包含 HTTP 地址，仅对 `jwgl2018.xatu.edu.cn` 和 `self.xatu.edu.cn` 放行明文流量；校园网自助平台的统一认证回跳与页面接口依赖此兼容规则。其余连接维持 HTTPS 校验。Debug／Benchmark 为本地集成测试额外允许 localhost，Release 不包含此例外。不会忽略 TLS 证书错误。

## 许可

MIT。课表协议与解析规则参考原 XATUHelper；保留原作者版权声明，见 [LICENSE](LICENSE)。
