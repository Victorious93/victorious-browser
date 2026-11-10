package com.victorious.browser.osint.research

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper
import java.util.Locale

/**
 * Offline phone-number analysis via libphonenumber (Apache-2.0). No network.
 * Carrier is the *original* allocation (numbers can be ported), so it is a hint only.
 */
object PhoneLookup {

    data class Result(
        val input: String,
        val valid: Boolean,
        val possible: Boolean,
        val countryCode: Int,
        val regionCode: String?,
        val location: String?,
        val carrier: String?,
        val lineType: String,
        val timeZones: List<String>,
        val e164: String,
        val international: String,
        val national: String,
        val rfc3966: String,
    )

    sealed interface Outcome {
        data class Ok(val result: Result) : Outcome
        data class Error(val message: String) : Outcome
    }

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /** [defaultRegion] (ISO-3166 alpha-2) is used when the input has no leading `+`. */
    fun analyze(raw: String, defaultRegion: String = "US", locale: Locale = Locale.ENGLISH): Outcome {
        val input = raw.trim()
        if (input.isEmpty()) return Outcome.Error("Enter a phone number")
        if (input.length > 40) return Outcome.Error("Too long to be a phone number")
        val n = try {
            util.parse(input, defaultRegion.uppercase())
        } catch (e: NumberParseException) {
            return Outcome.Error(
                when (e.errorType) {
                    NumberParseException.ErrorType.INVALID_COUNTRY_CODE -> "Unknown country code (add +<code> or pick a region)"
                    NumberParseException.ErrorType.NOT_A_NUMBER -> "That doesn't look like a phone number"
                    NumberParseException.ErrorType.TOO_SHORT_NSN, NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD -> "Too short"
                    NumberParseException.ErrorType.TOO_LONG -> "Too long"
                    else -> e.message ?: "Could not parse"
                },
            )
        }
        val region = util.getRegionCodeForNumber(n)
        val type = util.getNumberType(n)
        val carrier = runCatching { PhoneNumberToCarrierMapper.getInstance().getNameForNumber(n, locale) }
            .getOrNull()?.takeIf { it.isNotBlank() }
        val location = runCatching { PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(n, locale) }
            .getOrNull()?.takeIf { it.isNotBlank() }
        val tz = runCatching { PhoneNumberToTimeZonesMapper.getInstance().getTimeZonesForNumber(n) }
            .getOrNull().orEmpty().filter { it != "Etc/Unknown" }
        return Outcome.Ok(
            Result(
                input = input,
                valid = util.isValidNumber(n),
                possible = util.isPossibleNumber(n),
                countryCode = n.countryCode,
                regionCode = region?.takeIf { it != "ZZ" },
                location = location,
                carrier = carrier,
                lineType = lineTypeLabel(type),
                timeZones = tz,
                e164 = util.format(n, PhoneNumberFormat.E164),
                international = util.format(n, PhoneNumberFormat.INTERNATIONAL),
                national = util.format(n, PhoneNumberFormat.NATIONAL),
                rfc3966 = util.format(n, PhoneNumberFormat.RFC3966),
            ),
        )
    }

    fun lineTypeLabel(t: PhoneNumberType): String = when (t) {
        PhoneNumberType.MOBILE -> "Mobile"
        PhoneNumberType.FIXED_LINE -> "Landline"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Landline or mobile"
        PhoneNumberType.TOLL_FREE -> "Toll-free"
        PhoneNumberType.PREMIUM_RATE -> "Premium rate"
        PhoneNumberType.SHARED_COST -> "Shared cost"
        PhoneNumberType.VOIP -> "VoIP"
        PhoneNumberType.PERSONAL_NUMBER -> "Personal number"
        PhoneNumberType.PAGER -> "Pager"
        PhoneNumberType.UAN -> "UAN"
        PhoneNumberType.VOICEMAIL -> "Voicemail"
        PhoneNumberType.UNKNOWN -> "Unknown"
    }

    /**
     * Public pivots: links the operator opens in a tab. The app never scrapes them.
     * WhatsApp/Telegram links only show whatever those sites show publicly.
     */
    fun pivots(r: Result): List<Pivot> {
        val digits = r.e164.removePrefix("+")
        val q = DorkComposer::encode
        return listOf(
            Pivot("Google: exact number", "https://www.google.com/search?q=" + q("\"${r.e164}\" OR \"${r.international}\" OR \"${r.national}\"")),
            Pivot("Bing: exact number", "https://www.bing.com/search?q=" + q("\"${r.e164}\" OR \"${r.national}\"")),
            Pivot("DuckDuckGo: exact number", "https://duckduckgo.com/?q=" + q("\"${r.international}\"")),
            Pivot("WhatsApp link (wa.me)", "https://wa.me/$digits"),
            Pivot("Telegram link (t.me)", "https://t.me/+$digits"),
            Pivot("Sites mentioning it (paste/social)", "https://www.google.com/search?q=" +
                q("\"$digits\" (site:pastebin.com OR site:facebook.com OR site:x.com OR site:reddit.com)")),
            Pivot("Reverse lookup sites (search)", "https://www.google.com/search?q=" +
                q("\"${r.national}\" (site:whitepages.com OR site:truecaller.com OR site:sync.me OR site:numlookup.com)")),
        )
    }
}

/** A labelled public URL the operator can open in a tab. */
data class Pivot(val label: String, val url: String)
