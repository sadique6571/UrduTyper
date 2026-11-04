package me.nomi.urdutyper.presentation.ui.type.studio

import com.google.gson.Gson
import java.io.File
import java.util.UUID

/** Coordinates and type sizes are in document pixels, independent of screen density/zoom. */
data class StudioLayer(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "text",
    val text: String = "اپنا متن لکھیں",
    val fontId: String = "nastaliq",
    val imagePath: String = "",
    val cropImage: Boolean = false,
    val x: Float = 540f,
    val y: Float = 540f,
    val width: Float = 900f,
    val height: Float = 500f,
    val size: Float = 76f,
    val rotation: Float = 0f,
    val color: Int = 0xff123e34.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: String = "center",
    val lineSpacing: Float = 1.25f,
    val opacity: Float = 1f,
    val effects: List<String> = emptyList(),
    val effectIntensity: Float = 1f,
    val shadowStrength: Float = 1f,
    val gradientStart: Int? = null,
    val gradientEnd: Int? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val animation: LayerAnimation = LayerAnimation(),
    @Transient val activeWord: Int = -1
)

data class StudioDocument(
    val schemaVersion: Int = 2,
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled design",
    val width: Int = 1080,
    val height: Int = 1080,
    val backgroundColor: Int = 0xfff8f3e8.toInt(),
    val gradientColor: Int = 0xfff8f3e8.toInt(),
    val backgroundStyle: String = "",
    val backgroundImage: String = "",
    val backgroundVisible: Boolean = true,
    val layers: List<StudioLayer> = listOf(StudioLayer()),
    val updatedAt: Long = System.currentTimeMillis(),
    val video: VideoSettings = VideoSettings(),
    val audio: List<StudioAudio> = emptyList(),
    val sceneId: String = "first",
    val scenes: List<StudioScene> = emptyList(),
    val duckMusic: Boolean = true,
    val safeMargins: Boolean = false,
    val backgroundName: String = ""
) {
    fun captureScene() = StudioScene(sceneId, backgroundColor, gradientColor, backgroundStyle,
        backgroundImage, backgroundVisible, layers, video, backgroundName = backgroundName)
    fun orderedScenes(): List<StudioScene> = if (scenes.isEmpty()) listOf(captureScene()) else
        scenes.map { if (it.id == sceneId) captureScene().copy(transition = it.transition, transitionMs = it.transitionMs) else it }
    fun movieDurationMs(): Long = orderedScenes().sumOf { it.video.durationMs }
    fun sceneOffsetMs(): Long = orderedScenes().takeWhile { it.id != sceneId }.sumOf { it.video.durationMs }
    fun selectScene(id: String): StudioDocument {
        val all = orderedScenes()
        val scene = all.firstOrNull { it.id == id } ?: return this
        return scene.applyTo(this).copy(scenes = all)
    }
    fun musicGain(track: StudioAudio, timeMs: Long, totalMs: Long = movieDurationMs()): Float {
        val gain = track.gainAt(timeMs, totalMs)
        if (!duckMusic || track.role != "music") return gain
        val voice = audio.firstOrNull { it.role == "voice" } ?: return gain
        return gain * if (voice.gainAt(timeMs, totalMs) > .001f) .25f else 1f
    }
    fun resized(newWidth: Int, newHeight: Int): StudioDocument {
        require(newWidth in 320..4096 && newHeight in 320..4096)
        val sx = newWidth.toFloat() / width
        val sy = newHeight.toFloat() / height
        val ratio = minOf(sx, sy)
        fun resizeLayers(source: List<StudioLayer>) = source.map {
            it.copy(x = it.x * sx, y = it.y * sy, width = it.width * sx,
                height = it.height * sy, size = it.size * ratio)
        }
        return copy(width = newWidth, height = newHeight, layers = resizeLayers(layers),
            scenes = orderedScenes().map { it.copy(layers = resizeLayers(it.layers)) })
    }
}

/** Immutable snapshots include geometry, font identity, visibility and every background setting. */
class StudioHistory(private val limit: Int = 60) {
    init { require(limit >= 2) }
    private val undo = mutableListOf<StudioDocument>()
    private val redo = mutableListOf<StudioDocument>()
    fun reset(document: StudioDocument) { undo.clear(); redo.clear(); undo.add(document) }
    fun record(document: StudioDocument) {
        if (undo.lastOrNull() == document) return
        undo.add(document)
        while (undo.size > limit) undo.removeAt(0)
        redo.clear()
    }
    fun undo(): StudioDocument? {
        if (undo.size < 2) return null
        redo.add(undo.removeAt(undo.lastIndex))
        return undo.last()
    }
    fun redo(): StudioDocument? = if (redo.isEmpty()) null else redo.removeAt(redo.lastIndex).also { undo.add(it) }
    fun canUndo() = undo.size > 1
    fun canRedo() = redo.isNotEmpty()
}

class StudioStore(private val root: File) {
    companion object {
        private val saveLock = Any()
    }
    private val gson = Gson()
    init { check(root.isDirectory || root.mkdirs()) { "Cannot open drafts directory" } }
    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        return File(root, "$id.json")
    }
    @Synchronized fun save(document: StudioDocument) {
        synchronized(saveLock) {
            val target = file(document.id)
            // A delayed autosave must never replace a newer onStop/manual save.
            if (target.isFile && (load(document.id)?.updatedAt ?: 0) > document.updatedAt) return
            val pending = File(root, ".${document.id}.tmp")
            try {
                pending.outputStream().use { output ->
                    output.write(gson.toJson(document).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                java.nio.file.Files.move(pending.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } finally { pending.delete() }
        }
    }
    @Synchronized fun load(id: String): StudioDocument? = runCatching {
        gson.fromJson(file(id).readText(), StudioDocument::class.java).also {
            require(it.schemaVersion == 2 && it.width in 320..4096 && it.height in 320..4096 && it.layers.size <= 100)
            require(it.id == id)
            require(it.video.durationMs in 1000..60000 && it.audio.size <= 2)
            require((it.scenes.map { scene -> scene.video }+it.video).all { video -> video.atmosphereIntensity.isFinite() && video.atmosphereIntensity in 0f..1f && video.atmosphereSpeed.isFinite() && video.atmosphereSpeed in .25f..2f })
            require(it.scenes.size <= 20 && it.movieDurationMs() in 1000..60000)
            require(it.scenes.isEmpty() || it.scenes.count { scene -> scene.id == it.sceneId } == 1)
            require(it.scenes.map { scene -> scene.id }.distinct().size == it.scenes.size)
            require(it.scenes.all { scene -> scene.video.durationMs in 1000..60000 && scene.layers.size <= 100 &&
                scene.transition in listOf("none", "fade", "slide", "dissolve") && scene.transitionMs in 100..2000 })
            require(it.audio.all { audio -> audio.volume.isFinite() && audio.volume in 0f..1f &&
                audio.startMs in 0..60000 && audio.trimStartMs >= 0 && audio.trimEndMs > audio.trimStartMs &&
                audio.trimEndMs <= audio.sourceDurationMs && audio.fadeInMs >= 0 && audio.fadeOutMs >= 0 })
            require((it.layers + it.scenes.flatMap { scene -> scene.layers }).all { layer -> layer.x.isFinite() && layer.y.isFinite() &&
                layer.width.isFinite() && layer.width > 0 && layer.height.isFinite() && layer.height > 0 &&
                layer.size.isFinite() && layer.size > 0 && layer.rotation.isFinite() &&
                layer.effectIntensity.isFinite() && layer.effectIntensity in 0f..2f &&
                layer.shadowStrength.isFinite() && layer.shadowStrength in 0f..1f })
        }
    }.getOrNull()
    @Synchronized fun list(): List<StudioDocument> = root.listFiles().orEmpty()
        .filter { it.extension == "json" }.mapNotNull { load(it.nameWithoutExtension) }.sortedByDescending { it.updatedAt }
    @Synchronized fun delete(id: String): Boolean = file(id).let { !it.exists() || it.delete() }
}


/** Scene snapshots use the same layer renderer as the poster editor. Audio stays on the global timeline. */
data class StudioScene(
    val id: String = UUID.randomUUID().toString(),
    val backgroundColor: Int = 0xfff8f3e8.toInt(),
    val gradientColor: Int = 0xfff8f3e8.toInt(),
    val backgroundStyle: String = "",
    val backgroundImage: String = "",
    val backgroundVisible: Boolean = true,
    val layers: List<StudioLayer> = emptyList(),
    val video: VideoSettings = VideoSettings(),
    val transition: String = "fade",
    val transitionMs: Long = 400,
    val backgroundName: String = ""
) {
    fun applyTo(document: StudioDocument) = document.copy(sceneId = id, backgroundColor = backgroundColor,
        gradientColor = gradientColor, backgroundStyle = backgroundStyle, backgroundImage = backgroundImage,
        backgroundVisible = backgroundVisible, layers = layers, video = video, backgroundName = backgroundName)
}
