package br.com.devoaceitar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

internal enum class AnalysisSource(val label: String) {
    LAB("Laboratório"), REVIEW("Reavaliação"),
}

internal data class AnalysisEntry(
    val id: Long,
    val timeMs: Long,
    val source: AnalysisSource,
    val offer: Offer,
    val policy: AnalysisPolicy,
    val assessment: Assessment,
    val originalId: Long? = null,
)

internal class AnalysisHistory private constructor(context: Context) :
    SQLiteOpenHelper(context, "offer_analysis.db", null, 1) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE analyses (
                id INTEGER PRIMARY KEY,
                created_ms INTEGER NOT NULL,
                source TEXT NOT NULL,
                offer_key TEXT NOT NULL,
                offer TEXT NOT NULL,
                policy TEXT NOT NULL,
                assessment TEXT NOT NULL,
                original_id INTEGER
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX analyses_offer ON analyses(source, offer_key, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun prepare() = submit({ writableDatabase }, {})

    fun evaluateLab(key: String, offer: Offer, policy: AnalysisPolicy, done: (Result<AnalysisEntry>) -> Unit) =
        submit({ evaluate(AnalysisSource.LAB, key, offer, policy) }, done)

    fun review(entry: AnalysisEntry, policy: AnalysisPolicy, done: (Result<AnalysisEntry>) -> Unit) =
        submit({ evaluate(AnalysisSource.REVIEW, UUID.randomUUID().toString(), entry.offer, policy, entry.id) }, done)

    fun page(before: Long?, done: (Result<List<AnalysisEntry>>) -> Unit) = submit({
        val where = before?.let { "id < ?" }
        val args = before?.let { arrayOf(it.toString()) }
        readableDatabase.query("analyses", null, where, args, null, null, "id DESC", PAGE_SIZE.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.entry()) } }
    }, done)

    private fun evaluate(
        source: AnalysisSource, key: String, offer: Offer, policy: AnalysisPolicy, originalId: Long? = null,
    ): AnalysisEntry {
        val result = assess(offer, policy)
        val payload = offer.encode().toString()
        val rules = policy.encode().toString()
        val output = result.encode().toString()
        val time = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val duplicate = db.query("analyses", null, "source = ? AND offer_key = ?",
                arrayOf(source.name, key), null, null, "id DESC", "1").use { cursor ->
                if (cursor.moveToFirst() && cursor.string("offer") == payload &&
                    cursor.string("policy") == rules && cursor.string("assessment") == output) {
                    cursor.entry()
                } else null
            }
            val entry = duplicate ?: ContentValues().apply {
                put("created_ms", time)
                put("source", source.name)
                put("offer_key", key)
                put("offer", payload)
                put("policy", rules)
                put("assessment", output)
                originalId?.let { put("original_id", it) }
            }.let { row ->
                val id = db.insertOrThrow("analyses", null, row)
                AnalysisEntry(id, time, source, offer, policy, result, originalId)
            }
            db.setTransactionSuccessful()
            return entry
        } finally {
            db.endTransaction()
        }
    }

    private fun <T> submit(task: () -> T, done: (Result<T>) -> Unit) {
        worker.execute {
            val result = try { Result.success(task()) } catch (error: Exception) { Result.failure(error) }
            main.post { done(result) }
        }
    }

    private fun Cursor.entry(): AnalysisEntry {
        val original = getColumnIndexOrThrow("original_id")
        return AnalysisEntry(
            getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("created_ms")),
            AnalysisSource.valueOf(string("source")),
            JSONObject(string("offer")).offer(), JSONObject(string("policy")).policy(),
            JSONObject(string("assessment")).assessment(), if (isNull(original)) null else getLong(original),
        )
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

    companion object {
        const val PAGE_SIZE = 30

        private var instance: AnalysisHistory? = null

        @Synchronized
        fun get(context: Context): AnalysisHistory =
            instance ?: AnalysisHistory(context.applicationContext).also { instance = it }
    }
}

private fun TripMeasure.encode() = JSONObject().put("total", total).put("pickup", pickup).put("trip", trip)
private fun JSONObject.measure() = TripMeasure(int("total"), int("pickup"), int("trip"))

private fun Offer.encode() = JSONObject().put("kind", kind).put("pay", payCents)
    .put("distance", distance.encode()).put("duration", duration.encode())
    .put("destination", destination).put("rating", rating).put("wait", collectionWaitSeconds).put("extras", extraCostCents)

private fun JSONObject.offer() = Offer(getString("kind"), int("pay"), getJSONObject("distance").measure(),
    getJSONObject("duration").measure(), stringOrNull("destination"), double("rating"), int("wait"), int("extras"))

private fun AnalysisPolicy.encode() = JSONObject().put("costKm", reaisPerKm).put("hourTarget", minNetPerHour)
    .put("hourFloor", rejectBelowNetPerHour).put("rating", minRating).put("zone", avoidDestination)
    .put("kmTarget", grossPerKmBand?.target).put("kmFloor", grossPerKmBand?.floor)
    .put("fare", minimumFare).put("ratingMode", ratingMode.name)

private fun JSONObject.policy(): AnalysisPolicy {
    val floor = double("kmFloor")
    val target = double("kmTarget")
    return AnalysisPolicy(getDouble("costKm"), getDouble("hourTarget"), getDouble("hourFloor"),
        double("rating"), getString("zone"), if (floor != null && target != null) IncomeBand(floor, target) else null,
        double("fare"), RatingMode.valueOf(getString("ratingMode")))
}

private fun Assessment.encode() = JSONObject().put("decision", recommendation.name).put("reason", reason)
    .put("grossKm", grossPerKm).put("grossHour", grossPerHour).put("netHour", netPerHour).put("net", netAmount)
    .put("zone", avoidedDestination).put("rating", rating).put("margin", netMarginPercent)
    .put("totalCost", totalCost).put("pickupCost", pickupCost)

private fun JSONObject.assessment() = Assessment(
    Recommendation.valueOf(getString("decision")), getString("reason"), double("grossKm"),
    double("grossHour"), double("netHour"), double("net"), stringOrNull("zone"), double("rating"),
    double("margin"), double("totalCost"), double("pickupCost"),
)

private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.double(key: String): Double? = if (isNull(key)) null else getDouble(key)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
