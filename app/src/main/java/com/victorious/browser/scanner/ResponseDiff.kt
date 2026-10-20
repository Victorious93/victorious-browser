package com.victorious.browser.scanner

/**
 * Pure response-analysis heuristics for the active scanner: reflection check,
 * SQL-error signatures, boolean-diff, and timing judgement. Fully unit-tested in
 * [ResponseDiffTest]. No network, no Android types.
 *
 * SQL error signature strings are drawn from well-known public DBMS error
 * fingerprints (as popularized by sqlmap / OWASP); see NOTICE for attribution.
 */
object ResponseDiff {

    /** True when [payload] (or its marker) appears verbatim in the response body. */
    fun reflected(payload: String, body: String?): Boolean {
        if (body.isNullOrEmpty() || payload.isEmpty()) return false
        return body.contains(payload)
    }

    fun markerReflected(marker: String?, body: String?): Boolean {
        if (marker.isNullOrEmpty() || body.isNullOrEmpty()) return false
        return body.contains(marker)
    }

    /** DBMS error fingerprints keyed by a short DB label. */
    val SQL_ERROR_SIGNATURES: List<Pair<String, Regex>> = listOf(
        "MySQL" to Regex("SQL syntax.*MySQL|valid MySQL result|MySqlClient\\.|com\\.mysql\\.jdbc", RegexOption.IGNORE_CASE),
        "MariaDB" to Regex("check the manual that corresponds to your (MySQL|MariaDB) server version", RegexOption.IGNORE_CASE),
        "PostgreSQL" to Regex("PostgreSQL.*ERROR|pg_query\\(\\)|PG::SyntaxError|unterminated quoted string", RegexOption.IGNORE_CASE),
        "MSSQL" to Regex("Unclosed quotation mark after the character string|Microsoft SQL (Native|Server)|System\\.Data\\.SqlClient", RegexOption.IGNORE_CASE),
        "Oracle" to Regex("ORA-[0-9]{5}|Oracle error|quoted string not properly terminated", RegexOption.IGNORE_CASE),
        "SQLite" to Regex("SQLite/JDBCDriver|SQLite\\.Exception|sqlite3\\.OperationalError|unrecognized token", RegexOption.IGNORE_CASE),
    )

    /** Returns the DB label whose error signature matched, or null. */
    fun sqlErrorMatch(body: String?): String? {
        if (body.isNullOrEmpty()) return null
        for ((db, rx) in SQL_ERROR_SIGNATURES) {
            if (rx.containsMatchIn(body)) return db
        }
        return null
    }

    /** Length delta between two bodies. */
    fun lengthDelta(a: String?, b: String?): Int =
        kotlin.math.abs((a?.length ?: 0) - (b?.length ?: 0))

    /**
     * Boolean-based SQLi heuristic: TRUE condition response should look like the
     * baseline, FALSE condition should diverge. Returns true when that pattern
     * holds beyond [minDelta] characters of divergence.
     */
    fun booleanDiffSuspicious(
        baseline: String?,
        trueResp: String?,
        falseResp: String?,
        minDelta: Int = 32,
    ): Boolean {
        val trueLikeBaseline = lengthDelta(baseline, trueResp) <= minDelta / 2
        val falseDiverges = lengthDelta(baseline, falseResp) > minDelta
        val trueFalseDiverge = lengthDelta(trueResp, falseResp) > minDelta
        return trueLikeBaseline && falseDiverges && trueFalseDiverge
    }

    /**
     * Time-based heuristic: test response took at least [expectedDelayMs] longer
     * than baseline (with a margin). Guards against a slow baseline by also
     * requiring the test to exceed an absolute floor.
     */
    fun timingSuspicious(
        baselineMs: Long,
        testMs: Long,
        expectedDelayMs: Long = 5_000,
        marginMs: Long = 1_500,
    ): Boolean {
        val floor = expectedDelayMs - marginMs
        return testMs >= floor && (testMs - baselineMs) >= floor
    }
}
