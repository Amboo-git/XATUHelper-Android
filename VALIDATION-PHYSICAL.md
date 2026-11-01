# 真机补测 · Android 0.4.0

日期：2026-11-01（Asia/Shanghai）。设备为用户连接的 Redmi Note 8 Pro，Android 11／API 30，1080×2340，440 dpi，原字体 1.0 倍；系统 WebView 为 87.0.4280.141。

使用独立 `cn.edu.xatu.helper.qa` 包和示例／测试数据。未清除或操作主应用账号，没有提交付款、签到、请销假，也没有切换手机网络。

## 已验证

- 解锁后界面专项复测 1 项通过：登录校验、示例导航、周次选择／校准、课表滑动／详情、重建、考试倒计时「还有 14 天」、搜索、设置、诊断、宿舍绑定、电费、校历、提醒与组件入口。
- 最终兼容修复后的非界面测试 13 项通过：数据库迁移和隔离、每日电费历史和 400 条保留、真实校历 OCR、实际闹钟通知到达、三个 AppWidget 宿主渲染／点击／退出清理、提醒设置、Mock 教务会话、加密存储及 Cookie 恢复。
- Cookie 专项存储测试在真机再次 4 项通过；相同测试也在现代 WebView 模拟器上 4 项通过。复测保留了原有恢复断言，另检查重建 Cookie 适配器、主机和域范围、根路径隔离及过期删除。

以上为分次验证结果，未将首次全套测试的失败记录改写为一次 14 项全通过。原始日志、分项结果及限制见 [真机摘要](artifacts/qa/physical/summary.json)。

[考试页真机截图](artifacts/qa/physical/standard-ui/exams.png)显示右侧「还有 14 天」。普通字号界面截图来自已通过的解锁界面复测；后续锁屏失败的尝试没有生成新的有效界面截图。

## 发现并修复

旧 WebView 不支持 GET_COOKIE_INFO，原代码无法导出加密 Cookie 快照。现在保存网络响应中的原始 Set-Cookie 属性，供旧 WebView 恢复 HttpOnly、业务路径和有效期；仅在旧 WebView 网页中生成且未经过网络 CookieJar 的 Cookie 仍由系统 CookieManager 保存，不能宣称已完整导出。

现代 WebView 的导出记录重放时，必须区分 host-only Cookie 与允许子域的 domain Cookie。新增可空 hostOnly 元数据并兼容旧快照，避免恢复时扩大主机范围；保留合法域 Cookie，正确处理删除和替换。

MIUI 禁止 shell pm clear，真机脚本改为使用测试本身的应用权限重置隔离数据；测试 Activity 加入保持亮屏标志，不改变全局息屏时间。

## 未完成的真机项目

后续界面尝试受自动锁屏影响，不能计为通过。MIUI 拒绝 adb 写入系统 font_scale，1.3 倍字号没有实际应用；读取确认手机仍为原来的 1.0 倍。大字体目前只有模拟器验收证据。

未实测真实账号、真实学校网页交互、长时间省电／后台杀进程后的通知可靠性、开机恢复及 MIUI 桌面手动添加组件。AppWidgetHost 测试不等同于所有桌面尺寸适配。真机没有运行 Release 优化包的独立性能基准，Debug Window 短采样不作为正式帧率承诺。

复现：解锁手机并保持可交互后，运行 `ADB=/你的/adb scripts/physical-tests.sh 设备序列号`。脚本只安装隔离 QA 包；系统可能限制部分 adb 操作，应以实际日志为准。
