# Cyrene Voice Studio

一个独立的 Android 本地音色克隆与语音合成项目。用参考录音和准确原文创建角色音色，在手机上生成新文本的语音；也可作为标准 Android TTS 引擎供阅读器、聊天应用等调用。

无需 Muse、Termux、F-Droid 或额外兼容程序。安装模型后，合成不需要网络；应用没有网络权限，不包含云端 TTS 或 HTTP 服务。推理使用 sherpa-onnx 1.13.8 + ZipVoice-distill INT8，当前面向 Android 8.0 以上 ARM64 手机。

当前为 **0.2.0 源码预览版**，由 [kaiyuechen626](https://github.com/kaiyuechen626) 维护。应用界面、角色管理、音频处理及系统 TTS 接入在本项目中实现；底层推理和语音模型分别来自 sherpa-onnx、ZipVoice 等上游项目，不宣称这些组件为本项目自研。已完成本机构建和自动测试，新版尚未完成真机验收。

## 能做什么

- 导入 WAV / MP3 / M4A 录音，填写原文，创建、编辑、删除多个角色。
- 从已有录音中选择 1–6 秒完整短句，另存角色，保留原角色。
- 试听参考录音和新文本，边生成后续段落边播放；暂停、继续、停止及当前段进度控制。
- 导出 WAV，或导出含模型、参考录音和角色原文的 `.cyvoice` 音色包。
- 向系统提供可枚举的离线 TTS 音色，供支持选择引擎的第三方应用使用。

这属于参考音频条件生成，不是在手机上训练新模型。建议用 2–6 秒清晰单人完整短句，并填写准确原文；效果、速度和相似度因录音、文本及设备而异。长文可能出现等待，不承诺实时生成或无缝播放。

## 使用

1. 自行构建应用，并准备来源经过校验的基础 `.cyvoice` 模型包，见[模型准备](docs/MODELS.md)。仓库不附带模型或个人音色。
2. 在应用导入基础包，选择“上传录音，创建音色”，填写角色名称和录音原文。
3. 输入不同于参考录音的文字，选择角色并朗读；首次合成需要加载模型。
4. 供其他应用调用时，点击“将当前角色设为系统 TTS 默认音色”，再在系统或调用方选择 Cyrene Voice。调用方的音色选择可能覆盖默认角色。
5. Muse Cyrene 可直接导入导出的 `.cyvoice` 包，使用其应用内离线引擎；已有音色无需重复导入。

导出的角色包含原录音与文字，请只分享自己有权使用且愿意公开的内容。

## 构建

需要 JDK 17、Android SDK 34、Python 3.10+。首次准备依赖及 Gradle/Maven 缓存需要网络；运行时合成不需要网络。

```powershell
python tools/fetch_dependency.py
python tools/verify_public_source.py
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
python tools/verify_native_callback.py app/build/outputs/apk/release/app-release-unsigned.apk
```

Linux/macOS 可用 `bash gradlew`。`ANDROID_HOME` 指向本机 Android SDK，或自行创建不提交的 `local.properties`。Android Studio 也可以打开项目。GitHub Actions 会从仓库源码执行构建、单元测试、Lint 和发布包 JNI 回调检查，不上传 APK 或模型。

运行库固定到官方 Release 的 SHA-256，下载脚本和构建前检查均拒绝不匹配的文件。源码包不包含 AAR；`libs/` 中的本机缓存被 Git 忽略。Release 默认未签名，发布时应使用项目自己的稳定签名，勿把签名文件或密码提交到 GitHub。不同签名无法直接覆盖旧版，**不要为此卸载已有应用**，否则会丢失其私有音色数据。

## 接入其他应用

使用 Android `TextToSpeech`，引擎包名为 `io.cyrene.voice`，等初始化成功后枚举 `voices` 并选择离线音色。没有专用 Muse API，也没有本地 HTTP 接口。完整说明见[系统 TTS 接入](docs/ANDROID_TTS.md)。

## 验证与限制

本次构建、静态检查和真机测试结果分别记录在[验证记录](docs/VALIDATION.md)，不能互相替代。

三星 Galaxy S25 曾在后台冻结独立 TTS 引擎，导致调用方超时。将电池设为不受限制并不能保证解决；本版没有宣称修复该系统行为，也没有全局关闭系统节能。供其他应用长时间后台调用仍需逐机验证。Muse 当前接受的方案是应用内离线引擎，不依赖独立应用存活。

## 项目与第三方许可

应用原创源码采用 **GNU GPL v3.0（GPL-3.0-only）**，完整条款见 [LICENSE](LICENSE)。第三方代码、模型、发音数据和构建工具保留各自的许可与版权，不因本项目的许可证而改变，详见 [NOTICE](NOTICE) 与[第三方说明](THIRD_PARTY_NOTICES.md)。

本仓库只发布源码、Gradle Wrapper 和文档，不分发运行库 AAR、APK、模型、用户录音或个人音色。发布包含这些组件的二进制或模型包时，还需满足相应组件的许可、署名及适用的对应源码提供义务。选择 GPLv3 不等于已完成任何二进制或模型分发审核。

官方资料：[sherpa-onnx 1.13.8](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8)、[ZipVoice](https://github.com/k2-fsa/ZipVoice)、[ZipVoice 使用文档](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)。
