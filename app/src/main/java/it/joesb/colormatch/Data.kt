package it.joesb.colormatch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Materiale con la sua soglia di accettazione ΔE00. */
data class Material(val name: String, val threshold: Double)

/** Campione di riferimento: tipo tasto + materiale + colore, misurato in cabina. */
data class Reference(
    val id: String,
    val buttonType: String,
    val material: String,
    val color: String,
    val lab: Lab,
    val samples: Int,
    val spread: Double,
    val profileId: Long,
    val createdAt: Long,
)

/** Parametri fissi della fotocamera per la cabina luce. */
data class CameraProfile(
    val id: Long,
    val exposureNs: Long,
    val iso: Int,
    val gains: FloatArray?,
    val transform: IntArray?,
)

data class AppData(
    val materials: List<Material>,
    val buttonTypes: List<String>,
    val refs: List<Reference>,
    val profile: CameraProfile?,
) {
    fun thresholdFor(material: String): Double =
        materials.firstOrNull { it.name == material }?.threshold ?: 2.0

    companion object {
        fun defaults() = AppData(
            materials = listOf(
                Material("Fenix", 1.5),
                Material("Metallo spazzolato", 2.0),
                Material("Metallo lucido", 2.5),
                Material("Vetro", 1.5),
                Material("Plastica", 1.0),
            ),
            buttonTypes = listOf(
                "Tasto singolo",
                "Pulsantiera 2 tasti",
                "Pulsantiera 4 tasti",
                "Pulsantiera 6 tasti",
                "Placca",
            ),
            refs = emptyList(),
            profile = null,
        )
    }
}

/** Salvataggio locale in un file JSON nella memoria dell'app. */
class Store(context: Context) {
    private val file = File(context.filesDir, "colormatch.json")

    var data by mutableStateOf(load())
        private set

    fun update(change: (AppData) -> AppData) {
        data = change(data)
        save(data)
    }

    private fun load(): AppData = try {
        if (file.exists()) fromJson(JSONObject(file.readText())) else AppData.defaults()
    } catch (_: Exception) {
        AppData.defaults()
    }

    private fun save(d: AppData) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(toJson(d).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun toJson(d: AppData): JSONObject {
        val o = JSONObject()
        o.put("version", 1)
        o.put("materials", JSONArray().apply {
            d.materials.forEach { put(JSONObject().put("name", it.name).put("threshold", it.threshold)) }
        })
        o.put("buttonTypes", JSONArray().apply { d.buttonTypes.forEach { put(it) } })
        o.put("refs", JSONArray().apply {
            d.refs.forEach { r ->
                put(
                    JSONObject()
                        .put("id", r.id)
                        .put("buttonType", r.buttonType)
                        .put("material", r.material)
                        .put("color", r.color)
                        .put("L", r.lab.l).put("a", r.lab.a).put("b", r.lab.b)
                        .put("samples", r.samples)
                        .put("spread", r.spread)
                        .put("profileId", r.profileId)
                        .put("createdAt", r.createdAt)
                )
            }
        })
        d.profile?.let { p ->
            val po = JSONObject()
                .put("id", p.id)
                .put("exposureNs", p.exposureNs)
                .put("iso", p.iso)
            p.gains?.let { g -> po.put("gains", JSONArray().apply { g.forEach { put(it.toDouble()) } }) }
            p.transform?.let { t -> po.put("transform", JSONArray().apply { t.forEach { put(it) } }) }
            o.put("profile", po)
        }
        return o
    }

    private fun fromJson(o: JSONObject): AppData {
        val def = AppData.defaults()
        val materials = o.optJSONArray("materials")?.let { arr ->
            (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                Material(m.getString("name"), m.getDouble("threshold"))
            }
        } ?: def.materials
        val types = o.optJSONArray("buttonTypes")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: def.buttonTypes
        val refs = o.optJSONArray("refs")?.let { arr ->
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                Reference(
                    id = r.getString("id"),
                    buttonType = r.getString("buttonType"),
                    material = r.getString("material"),
                    color = r.getString("color"),
                    lab = Lab(r.getDouble("L"), r.getDouble("a"), r.getDouble("b")),
                    samples = r.optInt("samples", 1),
                    spread = r.optDouble("spread", 0.0),
                    profileId = r.optLong("profileId", 0L),
                    createdAt = r.optLong("createdAt", 0L),
                )
            }
        } ?: emptyList()
        val profile = o.optJSONObject("profile")?.let { p ->
            CameraProfile(
                id = p.getLong("id"),
                exposureNs = p.getLong("exposureNs"),
                iso = p.getInt("iso"),
                gains = p.optJSONArray("gains")?.let { g ->
                    FloatArray(g.length()) { g.getDouble(it).toFloat() }
                },
                transform = p.optJSONArray("transform")?.let { t ->
                    IntArray(t.length()) { t.getInt(it) }
                },
            )
        }
        return AppData(materials, types, refs, profile)
    }
}
