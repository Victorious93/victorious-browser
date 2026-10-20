package com.victorious.browser.netid

import java.util.Locale

/** Spoofed HTML5 Geolocation settings. Off by default = native WebView behavior. */
data class GeoSpoofConfig(
    val enabled: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 25.0,
)

object GeoSpoof {
    data class Preset(val name: String, val lat: Double, val lng: Double)

    val presets = listOf(
        Preset("New York, US", 40.7128, -74.0060),
        Preset("London, UK", 51.5074, -0.1278),
        Preset("Frankfurt, DE", 50.1109, 8.6821),
        Preset("Tokyo, JP", 35.6762, 139.6503),
        Preset("São Paulo, BR", -23.5505, -46.6333),
        Preset("Sydney, AU", -33.8688, 151.2093),
        Preset("Null Island", 0.0, 0.0),
    )

    sealed class Result {
        data class Ok(val config: GeoSpoofConfig) : Result()
        data class Error(val message: String) : Result()
    }

    fun parseCoordinate(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun isValidLatitude(v: Double) = v.isFinite() && v in -90.0..90.0
    fun isValidLongitude(v: Double) = v.isFinite() && v in -180.0..180.0
    fun isValidAccuracy(v: Double) = v.isFinite() && v > 0.0 && v <= 100_000.0

    fun validate(enabled: Boolean, lat: String, lng: String, accuracy: String): Result {
        val la = parseCoordinate(lat) ?: return Result.Error("Latitude is not a number")
        val lo = parseCoordinate(lng) ?: return Result.Error("Longitude is not a number")
        val acc = if (accuracy.isBlank()) 25.0 else parseCoordinate(accuracy)
            ?: return Result.Error("Accuracy is not a number")
        if (!isValidLatitude(la)) return Result.Error("Latitude must be between -90 and 90")
        if (!isValidLongitude(lo)) return Result.Error("Longitude must be between -180 and 180")
        if (!isValidAccuracy(acc)) return Result.Error("Accuracy must be >0 and ≤100000 m")
        return Result.Ok(GeoSpoofConfig(enabled, la, lo, acc))
    }

    private fun num(v: Double) = String.format(Locale.US, "%.7f", v)

    /**
     * JS that replaces navigator.geolocation.getCurrentPosition / watchPosition /
     * clearWatch with fixed coordinates. Empty string when disabled.
     */
    fun shim(config: GeoSpoofConfig): String {
        if (!config.enabled) return ""
        return """
(function(){
  try {
    if (window.__vbGeoShim) return; window.__vbGeoShim = true;
    var LAT=${num(config.latitude)}, LNG=${num(config.longitude)}, ACC=${num(config.accuracyMeters)};
    function pos(){ var c={latitude:LAT,longitude:LNG,accuracy:ACC,altitude:null,altitudeAccuracy:null,heading:null,speed:null};
      return {coords:c,timestamp:Date.now()}; }
    var ids=0, timers={};
    var geo={
      getCurrentPosition:function(ok,err,opt){ if(typeof ok==='function') setTimeout(function(){ok(pos());},0); },
      watchPosition:function(ok,err,opt){ var id=++ids; if(typeof ok==='function'){ setTimeout(function(){ok(pos());},0);
        timers[id]=setInterval(function(){ok(pos());},5000);} return id; },
      clearWatch:function(id){ if(timers[id]){clearInterval(timers[id]); delete timers[id];} }
    };
    Object.defineProperty(navigator,'geolocation',{get:function(){return geo;},configurable:true});
    if (navigator.permissions && navigator.permissions.query) {
      var q=navigator.permissions.query.bind(navigator.permissions);
      navigator.permissions.query=function(d){ if(d&&d.name==='geolocation') return Promise.resolve({state:'granted',onchange:null}); return q(d); };
    }
  } catch(e) {}
})();
""".trim()
    }
}
