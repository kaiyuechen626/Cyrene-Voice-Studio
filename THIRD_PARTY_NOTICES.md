# Third-party components

Original application source is licensed under GPL-3.0-only. This source repository does not redistribute runtime binaries, model weights, reference recordings or cloned voices. Third-party components retain their own licenses and copyright notices.

| Component | Version / source | License information |
| --- | --- | --- |
| sherpa-onnx Android runtime | [v1.13.8](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8) | Apache-2.0; copy in `third_party/sherpa-onnx-LICENSE` |
| ONNX Runtime, statically linked in the pinned AAR | [upstream](https://github.com/microsoft/onnxruntime) | MIT; license text in `third_party/onnxruntime-LICENSE`; additional upstream third-party notices still apply to binary distributions |
| ZipVoice | [upstream](https://github.com/k2-fsa/ZipVoice) | [Apache-2.0](https://github.com/k2-fsa/ZipVoice/blob/master/LICENSE); verified upstream copy in `third_party/ZipVoice-LICENSE` is added to base packs |
| eSpeak NG / pronunciation data | [upstream](https://github.com/espeak-ng/espeak-ng); sherpa-onnx v1.13.8 pins the [Piper fork at ed530aa](https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1) | GPL-3.0; full license in `third_party/espeak-ng-COPYING`; preserve applicable source, authorship and data notices when distributing |
| Vocos | [source](https://github.com/gemelo-ai/vocos), [converted model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/vocoder-models) | Vocos source is MIT; copy in `third_party/vocos-LICENSE`. This does not independently establish the license of every converted model; models are not redistributed here |
| Gradle Wrapper | Gradle 8.5 | Apache-2.0; copy in `third_party/gradle-LICENSE` and notice in `third_party/gradle-NOTICE` |

The pinned AAR's official SHA-256 is `b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471`, verified against the [official release assets](https://github.com/k2-fsa/sherpa-onnx/releases/expanded_assets/v1.13.8). Local downloads stay ignored and are not included in the public source ZIP. Before distributing an APK or model bundle, collect the applicable notices for every bundled component; this source package is not a completed binary redistribution license bundle.

Build provenance: sherpa-onnx's [eSpeak configuration](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake) pins the fork source and archive hash. Its [ARM64 static ONNX Runtime configuration](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/onnxruntime-android-aarch64-static.cmake) identifies the dependency used for that upstream build configuration. No upstream code in these components was modified in this source release.
