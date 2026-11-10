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
    /** Messa a fuoco al momento della calibrazione (per la guida distanza). */
    val focusDiopters: Float? = null,
    /** Vettore gravità normalizzato al momento della calibrazione (per la guida inclinazione). */
    val gravity: FloatArray? = null,
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

/** Una verifica registrata nello storico (copia i dati del campione, così resta valida anche se il campione cambia). */
data class Check(
    val id: String,
    val time: Long,
    val lot: String,
    val buttonType: String,
    val material: String,
    val color: String,
    val refLab: Lab,
    val lab: Lab,
    val deltaE: Double,
    val threshold: Double,
    val ok: Boolean,
)

/** Indicazioni leggibili sulla differenza tra pezzo e campione. */
fun differenceHints(ref: Lab, piece: Lab): List<String> = buildList {
    val dl = piece.l - ref.l
    val da = piece.a - ref.a
    val db = piece.b - ref.b
    if (kotlin.math.abs(dl) >= 0.5) add(if (dl > 0) "più chiaro" else "più scuro")
    if (kotlin.math.abs(da) >= 0.5) add(if (da > 0) "tende al rosso" else "tende al verde")
    if (kotlin.math.abs(db) >= 0.5) add(if (db > 0) "tende al giallo" else "tende al blu")
}

/** Salvataggio locale in file JSON nella memoria dell'app. */
class Store(context: Context) {
    private val file = File(context.filesDir, "colormatch.json")
    private val historyFile = File(context.filesDir, "history.json")

    var data by mutableStateOf(load())
        private set

    /** Storico verifiche, dalla più recente. */
    var history by mutableStateOf(loadHistory())
        private set

    fun update(change: (AppData) -> AppData) {
        data = change(data)
        save(data)
    }

    fun addCheck(c: Check) {
        history = listOf(c) + history
        saveHistory()
    }

    fun deleteChecks(ids: Set<String>) {
        history = history.filterNot { it.id in ids }
        saveHistory()
    }

    private fun writeAtomic(target: File, text: String) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id).put("time", c.time).put("lot", c.lot)
                    .put("buttonType", c.buttonType).put("material", c.material).put("color", c.color)
                    .put("rL", c.refLab.l).put("ra", c.refLab.a).put("rb", c.refLab.b)
                    .put("L", c.lab.l).put("a", c.lab.a).put("b", c.lab.b)
                    .put("deltaE", c.deltaE).put("threshold", c.threshold).put("ok", c.ok)
            )
        }
        writeAtomic(historyFile, arr.toString())
    }

    private fun loadHistory(): List<Check> = try {
        if (!historyFile.exists()) emptyList() else {
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Check(
                    id = o.getString("id"),
                    time = o.getLong("time"),
                    lot = o.optString("lot", ""),
                    buttonType = o.getString("buttonType"),
                    material = o.getString("material"),
                    color = o.getString("color"),
                    refLab = Lab(o.getDouble("rL"), o.getDouble("ra"), o.getDouble("rb")),
                    lab = Lab(o.getDouble("L"), o.getDouble("a"), o.getDouble("b")),
                    deltaE = o.getDouble("deltaE"),
                    threshold = o.getDouble("threshold"),
                    ok = o.getBoolean("ok"),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun load(): AppData = try {
        if (file.exists()) fromJson(JSONObject(file.readText())) else AppData.defaults()
    } catch (_: Exception) {
        AppData.defaults()
    }

    private fun save(d: AppData) {
        writeAtomic(file, toJson(d).toString())
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
            p.focusDiopters?.let { po.put("focusDiopters", it.toDouble()) }
            p.gravity?.let { g -> po.put("gravity", JSONArray().apply { g.forEach { put(it.toDouble()) } }) }
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
                focusDiopters = if (p.has("focusDiopters")) p.getDouble("focusDiopters").toFloat() else null,
                gravity = p.optJSONArray("gravity")?.let { g ->
                    FloatArray(g.length()) { g.getDouble(it).toFloat() }
                },
            )
        }
        return AppData(materials, types, refs, profile)
    }
}
