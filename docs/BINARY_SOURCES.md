# APK 对应源码与第三方材料

0.2.0 预览 APK 的应用源码以 GPL-3.0-only 提供。下载同一 [Release](https://github.com/kaiyuechen626/Cyrene-Voice-Studio/releases/tag/v0.2.0-preview.1) 中的 `Cyrene-Voice-Studio-0.2.0-source.zip`，或检出 `v0.2.0-preview.1` 标签。构建命令见 README；默认生成未签名包，签名属于发布者的本地步骤，不要求共享私钥。

APK 包含未修改的官方 sherpa-onnx AAR 中的 ARM64 运行库（SHA-256 固定于构建和下载脚本），并非在本次重新编译上游原生库。Release 的 `Cyrene-Voice-Studio-0.2.0-upstream-sources.zip` 附上原生运行库的上游源码、构建脚本、依赖源码与来源摘要：

- sherpa-onnx 1.13.8，提交 `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`，包含 `build-android-arm64-v8a.sh` 和 `.github/workflows/android-static.yaml`。
- eSpeak NG Piper fork，提交 `ed530aa113046142eb5115cf2fc9157854d0ffe1`，归档摘要与 sherpa CMake 声明一致。
- ONNX Runtime 1.28.2，提交 `33ca9628233dc8f002435e868d4c2e9f82766ca1`；版本同时与上游 Android 静态链接配置及 ARM64 二进制中的版本字符串相符。
- sherpa CMake 固定的 asio、websocketpp、json、kaldi-native-fbank、kaldi-decoder、openfst、simple-sentencepiece、piper-phonemize、eigen、hclust-cpp 源码归档，均按上游 SHA-256 检查；包含可能未用于当前 TTS 路径的运行库其他模块。

原始源码位置也可独立访问：[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/tree/11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf)、[eSpeak fork](https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1)、[ONNX Runtime](https://github.com/microsoft/onnxruntime/tree/33ca9628233dc8f002435e868d4c2e9f82766ca1)。

ONNX Runtime 的传递依赖来源与摘要列于其源码归档内 `cmake/deps.txt`（也在上游源码包根目录另附副本），对应源码可从该清单所指的上游服务器免费获取；这些是源码归档地址，不是模型下载地址。其构建脚本会按配置选择依赖。不要把本次收集源码与源代码构建脚本描述为已执行过完整的原生库重编译或逐字节可复现验证。

Kotlin 1.9.22 标准库适用 Apache-2.0；[源码](https://github.com/JetBrains/kotlin/tree/v1.9.22)。静态 C++ 运行库适用 LLVM / libc++ 的许可证及运行库例外；[源码](https://github.com/llvm/llvm-project)。Android 平台库由设备系统提供。

`third_party/` 保留 GPL、Apache、MIT、BSD、Unicode、MPL 等上游许可文本与 ONNX Runtime 的完整 ThirdPartyNotices，并作为 APK assets 一同打包。原许可与版权不因应用 GPL 许可而改变。模型及录音不在 APK、应用源码包或上游源码包中，模型授权应按其自身来源另行核对。
