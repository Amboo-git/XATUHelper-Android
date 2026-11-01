> 本文件为 0.1.0 历史记录。当前 0.2.0 的构建、真实账号和独立测试包结果见 [第二阶段记录](VALIDATION-STAGE2.md)。

# 首个里程碑验证记录

日期：2026-11-01（Asia/Shanghai）。状态：代码、可复现构建与模拟器验收完成；真实学生账号和实体设备验收仍待进行。

## 环境

- macOS arm64，JBR 21，Gradle Wrapper 9.0.0，AGP 8.12.0，Kotlin 2.2.10。
- Android SDK / target 36，最低 API 26。
- XatuHelper_API36 模拟器：Android 16 / API 36、arm64、1080×2400，软件图形后端。
- 本机 SDK 的 ADB 37.0.1 反复崩溃，最终使用隔离下载的官方 ADB 35.0.2，未替换共享 SDK。`scripts/device-tests.sh` 支持通过 `ADB` 指定路径。

## 已完成的验证

| 检查 | 结果 |
|---|---|
| Debug APK 构建 | 通过，可安装 |
| Release APK 构建 | 通过，R8 + 资源压缩，待正式签名 |
| Benchmark APK 构建 | 通过，独立包名、非 debuggable、完整 R8 优化、本机调试签名 |
| JVM 单元测试 | 17 / 17 通过 |
| Android Lint | 0 errors；剩余为版本更新、KAPT 和 KTX 建议等警告 |
| Debug 仪器测试 | 4 / 4 通过 |
| 独立 UIAutomator 优化构建测试 | 1 / 1 通过 |
| 进程结束 + Wi-Fi／移动网络关闭后恢复课表 | Debug、Benchmark 均通过 |
| 明暗模式与 1.3 倍字体 | 截图检查通过；已修复大字体日期栏裁切 |
| 公开 CAS 登录页核对 | 可达，密码表单与 AES 参数仍存在；已处理同页重复字段 ID |
| App 内 WebView 加载公开学校认证页 | 通过；未输入密码、未提交登录 |

17 项 JVM 测试覆盖：脱敏原始 EAMS 课表脚本、单双周掩码、重复活动合并、连排节次、线上与占位课程过滤、合法空学期与损坏响应的区分、课程重叠分栏、跨年周次、学期列表及教务外壳解析、作息例外、CAS AES、隐藏字段、同页多登录表单、验证码转网页登录、会话失效和 HTTP 错误。

4 项 Debug 仪器测试覆盖：登录输入校验、示例课表、导航、翻周、详情、Activity 重建；完整的认证→教务外壳→学期→课表→Room 流程；网络失败保留旧缓存、会话恢复与重登冷却、退出清理；Keystore 加密文件与 HttpOnly /eams Cookie 的保存恢复；Room 的账号、学期、课表类型隔离。协议集成使用本地 MockWebServer，不代表真实学生账号已验收。

## 优化构建性能样本

使用独立 `performance` 模块驱动 App，测试进程与 App 分离，避免向生产代码添加测试保留规则。App 为非调试、R8 和资源压缩开启的 Benchmark 变体；先预热两轮，再左右翻周八轮，使用真实输入时间并保存系统 gfxinfo。

| 指标 | 本次模拟器结果 |
|---|---:|
| 渲染帧数 | 835 |
| Janky frames（系统当前口径） | 10 / 835，1.20% |
| 帧耗时 P50 | 17 ms |
| 帧耗时 P95 | 24 ms |
| 帧耗时 P99 | 26 ms |
| Slow UI thread | 0 |
| 关闭网络后的进程冷启动 TotalTime | 173 ms |

原始结果：`artifacts/qa/benchmark/optimized-gfxinfo.txt`、`optimized-offline-launch.txt`。冷启动使用示例缓存，不包含在线认证和下载耗时。软件渲染、模拟器宿主和样本规模均会影响测量；不能据此承诺实体设备帧率，也没有与旧 Skip App 做同条件性能比较。

Debug 的短 Window 帧采样受 Compose 测试时钟影响，仅作诊断。性能结论以独立驱动器的真实事件样本为记录依据。

## 截图与结果文件

- `artifacts/qa/debug/`：登录、首页、周课表及 4 项仪器测试结果。
- `artifacts/qa/offline/`：断网进程恢复、深色模式和大字体课表，网络状态与冷启动记录。
- `artifacts/qa/browser/`：公开认证页面的 App 内截图和 UI 树，无真实账号密码。
- `artifacts/qa/benchmark/`：优化包截图、帧耗时原始记录及独立测试通过记录。
- `artifacts/qa/build-summary.json`：测试计数、APK 大小和 SHA-256。

这些构建与截图产物被 Git 忽略，可按 README 的命令在本机重新生成。Room schema 和脱敏解析样本属于源码。

## 仍需真实环境验收

1. 使用本人学号完成密码登录及验证码／动态码流程，核对课表、教师、教室、周次、学期列表和班级课表。
2. 在真实会话自然失效后验证 SSO／保存密码恢复，以及学校限流时的提示。
3. 在实体设备的优化构建上测量冷启动、连续切周、竖向滚动、内存与后台恢复；当前没有连接实体设备。
4. API 26、其他 Android 版本、平板和更多字体规模仍需补充兼容性实测。

真实账号凭据没有从仓库寻找，也没有以占位学号提交认证请求。正式分发还需要用户的发布签名。
