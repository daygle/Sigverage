package com.sigverage.app.data

import android.content.Context
import android.net.Uri
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles importing and exporting signal readings to/from CSV files.
 *
 * Implements RFC-4180 quoting and provides protection against spreadsheet
 * formula injection by guarding specific leading characters in text fields.
 */
class CsvManager(private val context: Context) {

    /**
     * Read readings from a CSV file at [source]. Returns the list of parsed
     * readings, or an empty list if none found or on error.
     */
    suspend fun importCsv(source: Uri): List<SignalReading> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(source)
                ?: return@runCatching emptyList<SignalReading>()
            val readings = mutableListOf<SignalReading>()
            stream.use { input ->
                input.bufferedReader().use { reader ->
                    // Skip header row.
                    reader.readLine()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val r = parseImportLine(line!!) ?: continue
                        readings += r
                    }
                }
            }
            readings
        }.getOrDefault(emptyList())
    }

    /**
     * Write [data] to a CSV file at [destination]. Returns the number of rows
     * written or -1 on failure.
     */
    suspend fun exportCsv(destination: Uri, data: List<SignalReading>): Int = withContext(Dispatchers.IO) {
        runCatching {
            if (data.isEmpty()) return@runCatching 0
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val stream = context.contentResolver.openOutputStream(destination)
                ?: return@runCatching 0
            stream.use { out ->
                out.bufferedWriter().use { writer ->
                    writer.append(
                        "timestamp,latitude,longitude,accuracy_m,provider," +
                                "network_type,signal_dbm,rsrp_dbm,rsrq_db,snr_db," +
                                "mcc,mnc,cell_id,operator\n"
                    )
                    for (r in data) writeRow(writer, r, sdf)
                }
            }
            data.size
        }.getOrDefault(-1)
    }

    private fun parseImportLine(line: String): SignalReading? {
        val fields = splitCsvLine(line) ?: return null
        if (fields.size < 14) return null

        val timestamp = fields[0].toLongOrNull() ?: return null
        val latitude = fields[1].toDoubleOrNull() ?: return null
        val longitude = fields[2].toDoubleOrNull() ?: return null
        val accuracy = fields[3].toFloatOrNull() ?: return null
        val provider = fields[4]
        val networkType = runCatching {
            NetworkType.valueOf(fields[5])
        }.getOrDefault(NetworkType.Unknown)
        val signalDbm = fields[6].toIntOrNull()
        val rsrpDbm = fields[7].toIntOrNull()
        val rsrqDb = fields[8].toIntOrNull()
        val snrDb = fields[9].toIntOrNull()
        val mcc = fields[10].toIntOrNull()
        val mnc = fields[11].toIntOrNull()
        val cellId = fields[12].toLongOrNull()
        val operator = fields[13].let { raw ->
            if (raw.isBlank()) null
            else if (raw.startsWith("'")) raw.drop(1).trim().ifBlank { null }
            else raw.trim().ifBlank { null }
        }

        return SignalReading(
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            provider = provider,
            networkType = networkType,
            signalDbm = signalDbm,
            rsrpDbm = rsrpDbm,
            rsrqDb = rsrqDb,
            snrDb = snrDb,
            mcc = mcc,
            mnc = mnc,
            cellId = cellId,
            operatorName = operator,
        )
    }

    private fun splitCsvLine(line: String): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i++
                    }
                }
                c == '"' && !inQuotes -> {
                    inQuotes = true
                    i++
                }
                c == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        if (inQuotes) return null
        result += current.toString()
        return result
    }

    private fun writeRow(
        writer: BufferedWriter,
        r: SignalReading,
        sdf: SimpleDateFormat
    ) {
        writer.append(sdf.format(Date(r.timestamp))).append(',')
        writer.append(r.latitude.toString()).append(',')
        writer.append(r.longitude.toString()).append(',')
        writer.append(r.accuracyMeters.toString()).append(',')
        writer.append(r.provider).append(',')
        writer.append(r.networkType.name).append(',')
        writer.append(r.signalDbm?.toString().orEmpty()).append(',')
        writer.append(r.rsrpDbm?.toString().orEmpty()).append(',')
        writer.append(r.rsrqDb?.toString().orEmpty()).append(',')
        writer.append(r.snrDb?.toString().orEmpty()).append(',')
        writer.append(r.mcc?.toString().orEmpty()).append(',')
        writer.append(r.mnc?.toString().orEmpty()).append(',')
        writer.append(r.cellId?.toString().orEmpty()).append(',')
        writer.append(r.operatorName?.let(::csvEscape) ?: "")
        writer.append('\n')
    }

    private fun csvEscape(s: String): String {
        val guarded = if (s.isNotEmpty() && s.first() in FORMULA_TRIGGERS) "'$s" else s
        val needsQuote = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = guarded.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    companion object {
        private const val FORMULA_TRIGGERS = "=+-@\t\r"
    }
}
