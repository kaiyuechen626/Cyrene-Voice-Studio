# 准备上传 GitHub

本仓库发布 Cyrene Voice Studio 0.2.0 的预览版源码。上传源码不会替换手机上的 Cyrene Voice。

推荐上传经白名单打包的源码：

```powershell
python tools/export_source.py
```

默认生成 `outputs/Cyrene-Voice-Studio-0.2.0-github-source.zip`。解压后上传其中 `Cyrene-Voice-Studio` 项目目录的内容；不要把电脑上整个旧 `Cyrene-Voice` 目录上传。与项目目录并列的 `SOURCE_SHA256.json` 是压缩包附带的完整性清单，不属于仓库文件。源码包包含构建脚本和模型哈希清单，排除本机缓存、模型、AAR、个人录音、音色包、签名文件和 APK。

如果直接使用 Git，也请先审查待提交文件，不能仅依赖忽略规则保护隐私。原始角色的录音与原文只留在本机及手机里。

应用原创源码采用 GPL-3.0-only，顶层 `LICENSE` 保存完整文本。第三方许可与来源在 `NOTICE`、`third_party` 和 `THIRD_PARTY_NOTICES.md` 中单独保留。

上传前运行 `python tools/verify_public_source.py --staged`，检查 Git 暂存内容是否与源码白名单一致，并扫描常见密钥及私人路径。自动检查不能证明所有内容都可公开，仍须检查实际文件清单。仓库的构建工作流只有读权限，不发布安装包或模型。

源码与 APK 应分开发布。默认 Release 构建不签名；GitHub 0.2.0 预览附件在构建后沿用旧开发证书签名，保留许可及对应源码，并明确尚未完成真机验收。参见 APK_RELEASE.md 和 BINARY_SOURCES.md。现存安装版和新构建的签名不同会拒绝覆盖，不能以卸载清数据作为常规升级方式。不要把此前 Muse 的签名或密钥用于公开项目源码。

独立项目沿用 `io.cyrene.voice` 包名以保留未来兼容升级的可能，目前没有改变安装版的签名或私有数据。
