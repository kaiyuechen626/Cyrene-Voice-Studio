# 准备基础模型包

应用与源码不内置、不自动下载模型。只支持本文所列 ZipVoice-distill INT8 配置，不能通过改文件名加载其他模型。

官方文件（2026-10-21 核对 Release 页面）：

| 文件 | 官方来源 | SHA-256 |
| --- | --- | --- |
| sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2 | [tts-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) | `77219c8b40f4ee8d73a7f902305ff6c1128ef9b54461c41b4ca6ed890b6c2803` |
| vocos_24khz.onnx | [vocoder-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/vocoder-models) | `bcb3b970e384161c4d634f0bb9e999ff1c471b34c9bc0b1049a5014065ed3cc0` |

先自行下载并计算摘要，比对通过后再解压。把 `vocos_24khz.onnx` 放到解压后的模型目录，与 `encoder.int8.onnx`、`decoder.int8.onnx` 同级。目录还应有 tokens、lexicon、espeak-ng-data。打包脚本会补入已核对的官方 `third_party/ZipVoice-LICENSE`，因为上述模型压缩包未附 LICENSE 文件。

```powershell
Get-FileHash .\sherpa-onnx-zipvoice-distill-int8-zh-en-emilia.tar.bz2 -Algorithm SHA256
Get-FileHash .\vocos_24khz.onnx -Algorithm SHA256
python tools/package_model.py --model-dir "你的模型目录" --output "dist/Cyrene-Voice-Base.cyvoice"
```

脚本只打包 `model-files.sha256.json` 列出的已校验模型文件，逐文件校验后生成空角色列表；不打包 `test_wavs`、参考录音、个人角色或任意额外文件。不覆盖已有输出。该清单是本项目固定的模型文件集合，不是发布者数字签名。

手机上导入生成的基础包，再用有权使用的录音创建角色。导出的完整角色包包含模型、录音和原文，不应默认加入公开仓库。现有 Muse 音色继续保留，不需为新项目删除或重导。

参考：[官方 ZipVoice 文档](https://k2-fsa.github.io/sherpa/onnx/tts/zipvoice.html)。模型、声码器和发音数据的使用与再分发需分别保留其上游说明；基础包不是个人音色的公开授权。
