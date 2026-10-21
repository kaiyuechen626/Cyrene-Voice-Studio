package io.cyrene.voice.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VoiceRole(val id: String, val name: String, val referenceText: String, val speed: Float = 1f, val stableProsody: Boolean = false) {
    fun json() = JSONObject().put("id", id).put("name", name).put("referenceText", referenceText).put("speed", speed).put("stableProsody", stableProsody)
}

/** Portable ZipVoice pack. Model, reference recordings and transcripts travel with the roles. */
class VoicePackStore(private val root: File) {
    init { check(root.isDirectory || root.mkdirs()) }
    fun packs(): List<File> = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && File(it, "manifest.json").isFile }.orEmpty()
    fun manifest(pack: File) = JSONObject(File(pack, "manifest.json").readText())
    fun roles(pack: File): List<VoiceRole> = readRoles(manifest(pack))

    @Synchronized fun saveRoles(pack: File, roles: List<VoiceRole>) {
        require(roles.size <= 256) { "最多 256 个角色" }
        val json = manifest(pack).put("roles", JSONArray().also { a -> roles.forEach { a.put(it.json()) } })
        readRoles(json)
        val hashes = json.getJSONObject("sha256")
        hashes.keys().asSequence().filter { it.startsWith("references/") }.toList().forEach { hashes.remove(it) }
        roles.forEach { role ->
            val path = "references/${role.id}.wav"
            val file = File(pack, path)
            require(file.isFile) { "角色缺少参考录音" }
            hashes.put(path, sha256(file))
        }
        val tmp = File(pack, "manifest.new")
        tmp.writeText(json.toString(2))
        java.nio.file.Files.move(tmp.toPath(), File(pack, "manifest.json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    /** Bounds, traversal checks and hashes apply before installation; failed imports leave existing packs intact. */
    @Synchronized fun importPack(input: InputStream): File {
        val stage = File(root, ".import-${UUID.randomUUID()}").also { check(it.mkdir()) }
        try {
            var total = 0L
            var count = 0
            val names = HashSet<String>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 10000) { "音色包文件数过多" }
                    val name = entry.name
                    require(name.length <= 512 && !name.contains('\\') && !name.startsWith('/') && !name.contains(':')) { "非法文件路径" }
                    require(name.split('/').none { it == ".." || it == "." }) { "非法文件路径" }
                    require(names.add(name)) { "音色包含重复文件" }
                    val out = File(stage, name).canonicalFile
                    require(out.path.startsWith(stage.canonicalPath + File.separator)) { "非法文件路径" }
                    if (entry.isDirectory) { check(out.mkdirs() || out.isDirectory); continue }
                    check(out.parentFile!!.mkdirs() || out.parentFile!!.isDirectory)
                    out.outputStream().use { sink ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 1024L * 1024 * 1024) { "解压后音色包超过 1 GB" }
                            sink.write(buffer, 0, n)
                        }
                    }
                }
            }
            val mf = File(stage, "manifest.json")
            require(mf.isFile && mf.length() <= 1024 * 1024) { "缺少有效 manifest.json" }
            val m = manifest(stage)
            require(m.getInt("formatVersion") == 2 && m.getString("engine") == "zipvoice-distill-int8") { "不支持的音色包版本" }
            val roles = readRoles(m)
            val hashes = m.getJSONObject("sha256")
            val required = listOf("encoder.int8.onnx", "decoder.int8.onnx", "vocos_24khz.onnx", "tokens.txt", "lexicon.txt", "LICENSE")
            required.forEach { require(hashes.has("model/$it")) { "缺少模型文件: $it" } }
            roles.forEach { require(hashes.has("references/${it.id}.wav")) { "缺少角色参考录音" } }
            require(File(stage, "model/espeak-ng-data").isDirectory) { "缺少发音数据" }
            stage.walkTopDown().filter { it.isFile && it != mf }.forEach { f ->
                val key = f.relativeTo(stage).invariantSeparatorsPath
                require(hashes.has(key) && sha256(f) == hashes.getString(key)) { "文件校验失败: $key" }
            }
            hashes.keys().forEach { key ->
                require((key.startsWith("model/") || key.startsWith("references/")) && !key.contains("..") && !key.contains('\\') && !key.contains(':')) { "非法校验路径" }
                require(File(stage, key).isFile) { "缺少文件: $key" }
            }
            roles.forEach { ReferenceAudio.read(File(stage, "references/${it.id}.wav")) }
            val destination = File(root, UUID.randomUUID().toString())
            check(stage.renameTo(destination)) { "安装失败" }
            return destination
        } finally { if (stage.exists()) stage.deleteRecursively() }
    }

    @Synchronized fun exportPack(pack: File, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            val allowed = manifest(pack).getJSONObject("sha256")
            pack.walkTopDown().filter { it.isFile && (it.name == "manifest.json" || allowed.has(it.relativeTo(pack).invariantSeparatorsPath)) }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(pack).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    companion object {
        fun readRoles(m: JSONObject): List<VoiceRole> {
            val a = m.getJSONArray("roles")
            require(a.length() in 0..256) { "角色数量无效" }
            val roles = (0 until a.length()).map {
                val r = a.getJSONObject(it)
                val id = r.getString("id")
                val name = r.getString("name").trim()
                val referenceText = r.getString("referenceText").trim()
                val speed = r.getDouble("speed").toFloat()
                require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && name.length in 1..80 && referenceText.length in 1..1000 && speed.isFinite() && speed in 0.5f..2f) { "请填写角色名称、录音原文和有效语速" }
                val stableProsody = if (r.has("stableProsody")) r.getBoolean("stableProsody") else false
                VoiceRole(id, name, referenceText, speed, stableProsody)
            }
            require(roles.map { it.id }.distinct().size == roles.size) { "角色 ID 重复" }
            return roles
        }
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(65536)
                while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
