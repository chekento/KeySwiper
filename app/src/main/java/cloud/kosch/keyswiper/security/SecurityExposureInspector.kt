package cloud.kosch.keyswiper.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Reports visible exposure surfaces, NOT detection of a keylogger.
 * Accessibility services can be legitimate; malware outside this API is undetectable.
 */
object SecurityExposureInspector {
    enum class Level { NONE_OBSERVED, REVIEW, ELEVATED, UNKNOWN }
    data class AccessibleService(val name: String, val readsWindows: Boolean, val filtersKeys: Boolean)
    data class Result(val level: Level, val services: List<AccessibleService>, val explanation: String)

    fun assess(services: List<AccessibleService>): Level = when {
        services.any { it.readsWindows || it.filtersKeys } -> Level.ELEVATED
        services.isNotEmpty() -> Level.REVIEW
        else -> Level.NONE_OBSERVED
    }

    fun inspect(context: Context): Result = try {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .filter { it.resolveInfo?.serviceInfo?.packageName != context.packageName }
            .map { info ->
                val name = runCatching { info.resolveInfo.loadLabel(context.packageManager).toString() }
                    .getOrDefault(info.id ?: "Unbenannter Dienst")
                AccessibleService(name,
                    info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0,
                    info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0)
            }
        Result(assess(services), services, when (assess(services)) {
            Level.NONE_OBSERVED -> "Keine fremden aktiven Bedienungshilfen-Dienste sichtbar. " +
                "Dies schließt Keylogger, Screenreader, Overlays oder kompromittierte Systeme NICHT aus."
            Level.REVIEW -> "Aktive fremde Bedienungshilfen gefunden. Diese sind nicht automatisch schädlich. " +
                "Prüfe, ob du die Dienste selbst aktiviert hast."
            Level.ELEVATED -> "Mindestens ein aktiver Dienst meldet Fensterlese- oder Tastenfilter-Fähigkeiten. " +
                "Das ist ein potenzieller Zugriffspfad, KEIN Nachweis eines Keyloggers."
            Level.UNKNOWN -> "Prüfung nicht möglich."
        })
    } catch (_: Exception) {
        Result(Level.UNKNOWN, emptyList(), "Android hat die Prüfung aktiver Bedienungshilfen verhindert. " +
            "Keylogger können nicht zuverlässig durch eine Tastatur-App erkannt werden.")
    }
}
