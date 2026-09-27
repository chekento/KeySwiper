package cloud.kosch.keyswiper.stylus

import android.content.Context

class StylusActionStore(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "keyswiper_stylus_actions",
            Context.MODE_PRIVATE
        )

    fun actionFor(
        trigger: StylusTrigger
    ): StylusAction {
        val fallback = defaultAction(trigger)

        return StylusAction.fromStored(
            prefs.getString(
                trigger.storageKey,
                null
            ),
            fallback
        )
    }

    fun setAction(
        trigger: StylusTrigger,
        action: StylusAction
    ) {
        prefs.edit()
            .putString(
                trigger.storageKey,
                action.name
            )
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun defaultAction(
        trigger: StylusTrigger
    ): StylusAction =
        when (trigger) {
            StylusTrigger.PRIMARY_SINGLE ->
                StylusAction.VOICE_TOGGLE

            StylusTrigger.PRIMARY_DOUBLE ->
                StylusAction.TRANSLATE_SELECTION

            StylusTrigger.SECONDARY_SINGLE ->
                StylusAction.NEXT_CANDIDATE
        }
}
