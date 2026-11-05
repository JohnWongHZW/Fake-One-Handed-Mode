# 构建

当前脚本面向 Windows，需要 Python 3.10+、JDK 17+，并将 `JAVA_HOME` 指向本机 JDK。

```powershell
python setup_tools.py
python tests/host_harness.py
python tests/projection_harness.py
python build.py
python package_release.py
```

`setup_tools.py` 下载 Android SDK Platform 36、Build Tools 36.0.0 和仅用于编译的 libxposed API 102，并校验下载文件。已有 SDK 时可设置 `ANDROID_HOME`；libxposed 编译依赖仍需放在 `tools/libxposed-api-102.jar`。

`build.py` 执行 Java 与 DEX 编译、资源打包、几何和反射测试、APK 签名及对齐校验。产物位于 `dist/`，`package_release.py` 生成源码压缩包和 SHA-256 校验文件。

首次本地构建会在 `tools/local-signing/` 生成自己的签名密钥。该目录不提交到 GitHub，也不包含在源码包中。自行构建的 APK 与官方 Release 签名不同，不能直接覆盖安装；自己的后续构建需保留同一份密钥。

测试使用 Android 边界替身，不代替真机的显示、触控与 LSPosed 注入测试。
