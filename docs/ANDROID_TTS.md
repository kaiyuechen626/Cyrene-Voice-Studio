# 通过 Android 系统接口调用

本项目已经实现 `TextToSpeechService`，Manifest 注册 `android.intent.action.TTS_SERVICE`。引擎包名固定为 `io.cyrene.voice`，音色名为 `<pack-id>/<role-id>`，不要把某个人的 UUID 硬编码进调用应用。

Android 11 以上调用应用需按官方要求在自己的 Manifest 声明包可见性：

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

调用流程：

1. 在主线程用 `TextToSpeech(context, listener, "io.cyrene.voice")` 创建实例。
2. 仅在初始化返回 `TextToSpeech.SUCCESS` 后读取 `tts.voices`，从 `isNetworkConnectionRequired == false` 的音色中让用户选择。选择后使用 `tts.setVoice(voice)`。
3. 注册 `UtteranceProgressListener`，为每次请求生成唯一 ID。可以用 `speak` 由系统播放，也可以用 `synthesizeToFile` 生成本机 WAV 后交给自己的播放器。
4. 处理立即返回的错误及异步 `onError`，在离开页面、取消或切换任务时正确调用 `stop`；释放实例时调用 `shutdown`。TTS 的排队合成不等于播放器暂停，文件播放的暂停/继续由调用方播放器处理。
5. 合成可能慢于实时，首次模型加载及长文需要等待；调用方应提供取消和超时处理，避免不断重试导致积压。请求长度遵守 `TextToSpeech.getMaxSpeechInputLength()`，长文自行按完整句子分段。

界面中的“默认音色”只在调用方未指定音色时使用。切换角色后需重新枚举，旧 ID 可能因删除角色或重新导入包而失效。当前角色均以中文 locale 发布，模型也接受英语；本版没有多语言音色分类 UI。

`Cyrene 兼容性测试` 是独立诊断应用，不是桥接组件，调用本引擎不需要它。Muse 也不需要合并该诊断应用。

## 手机验证

本次不自动安装或覆盖手机上的 Cyrene Voice。待自行构建并确认签名后，应分别验证：前台短句、长文、取消后新请求、系统 TTS 文件输出、断网、后台与锁屏。三星后台冻结是已知限制，不能把接口注册成功当作这些项目全部通过。

官方 API：[TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)、[TextToSpeechService](https://developer.android.com/reference/android/speech/tts/TextToSpeechService)。
