package me.nomi.urdutyper.presentation.utils.common

import org.json.JSONArray
import org.json.JSONObject

/** Portable schema v1 for an editable Urdu design (not just a flattened screenshot). */
data class EditableTextLayer(
    val text: String,
    val fontId: String,
    val colorArgb: Int,
    val sizeSp: Float,
    val x: Float,
    val y: Float,
    val rotationDegrees: Float = 0f
)

data class EditableProject(
    val id: String,
    val width: Int,
    val height: Int,
    val backgroundArgb: Int,
    val layers: List<EditableTextLayer>
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(width in 1..10000 && height in 1..10000)
        require(layers.size <= 100)
    }

    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("id", id)
        put("width", width)
        put("height", height)
        put("backgroundArgb", backgroundArgb)
        put("layers", JSONArray().also { array ->
            layers.forEach { layer ->
                array.put(JSONObject().apply {
                    put("text", layer.text)
                    put("fontId", layer.fontId)
                    put("colorArgb", layer.colorArgb)
                    put("sizeSp", layer.sizeSp.toDouble())
                    put("x", layer.x.toDouble())
                    put("y", layer.y.toDouble())
                    put("rotationDegrees", layer.rotationDegrees.toDouble())
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): EditableProject {
            val root = JSONObject(json)
            require(root.getInt("schemaVersion") == 1) { "Unsupported project schema" }
            val layers = root.getJSONArray("layers")
            require(layers.length() <= 100)
            return EditableProject(
                id = root.getString("id"),
                width = root.getInt("width"),
                height = root.getInt("height"),
                backgroundArgb = root.getInt("backgroundArgb"),
                layers = (0 until layers.length()).map { index ->
                    val item = layers.getJSONObject(index)
                    EditableTextLayer(
                        text = item.getString("text"),
                        fontId = item.getString("fontId"),
                        colorArgb = item.getInt("colorArgb"),
                        sizeSp = item.getDouble("sizeSp").toFloat(),
                        x = item.getDouble("x").toFloat(),
                        y = item.getDouble("y").toFloat(),
                        rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat()
                    )
                }
            )
        }
    }
}
