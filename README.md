1. 项目概况
本工程基于早期小米便签开源版本进行适配，目前已在 Android 13.0 (API 33) 环境下跑通，解决了原版代码中的旧版资源冲突及 API 过时问题。

2. 必备环境要求
为了确保你能直接运行项目而不报错，请检查以下配置：

Android Studio: 建议版本为 Jellyfish (2023.3.1) 或更高。

SDK: 必须安装 Android 13.0 ("Tiramisu") API 33。

JDK: 项目采用 JDK 17 (在 File -> Project Structure -> SDK Location -> Gradle Settings 中确认)。

模拟器: 推荐使用 Pixel 7 或同级别设备，系统镜像选择 API 33 (Google APIs)。

3. 关键修复说明（避雷区）
项目中已处理以下核心冲突，开发时请遵循：

⚠️ Switch-Case 语法限制
由于新版 Gradle 插件将 R.id 设置为非常量，本项目中的所有点击事件及菜单处理必须使用 if-else 结构，严禁在 switch 语句中直接使用 R.id.xxx。

错误范例： case R.id.delete: ... (会导致编译器报错：需要常量表达式)

🌐 遗产库支持 (Apache HTTP)
本项目部分逻辑依赖旧版 Apache HTTP 库。已在 build.gradle 中添加 useLibrary 'org.apache.http.legacy'，请勿删除，否则 GTaskClient.java 等同步相关代码会大面积报错。

🖼️ 模拟器皮肤问题
如果启动模拟器时提示 The skin directory does not point to a valid skin 导致无法开启，请在 Device Manager 中编辑对应设备，进入 Advanced Settings，取消勾选 Enable device frame。

4. 快速上手步骤
Clone 项目: git clone [项目地址]

打开项目: 用 Android Studio 打开根目录，等待 Gradle 自动构建。

检查 SDK: 若提示找不到 SDK，请在 File -> Settings -> Appearance & Behavior -> System Settings -> Android SDK 中手动指定。

同步: 点击工具栏右上角的 "Sync Project with Gradle Files" (大象图标)。

运行: 建议先执行 Build -> Clean Project，再点击 Run 按钮。
