package cloud.kosch.keyswiper.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import cloud.kosch.keyswiper.KeySwiperImeService

data class ImeSetupState(
    val installed: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val selectedImeLabel: String?
)

object ImeSetupStateReader {

    fun read(
        context: Context
    ): ImeSetupState {
        val imm =
            context.getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        val installedMethods =
            imm.inputMethodList

        val keySwiper =
            installedMethods
                .firstOrNull {
                    it.serviceInfo.packageName ==
                        context.packageName &&
                        it.serviceInfo.name ==
                        KeySwiperImeService::class.java.name
                }
                ?: installedMethods
                    .firstOrNull {
                        it.serviceInfo.packageName ==
                            context.packageName
                    }

        val enabled =
            keySwiper != null &&
                imm.enabledInputMethodList
                    .any {
                        it.id ==
                            keySwiper.id
                    }

        val selectedId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )

        val selected =
            keySwiper != null &&
                selectedId ==
                    keySwiper.id

        val selectedLabel =
            installedMethods
                .firstOrNull {
                    it.id == selectedId
                }
                ?.serviceInfo
                ?.loadLabel(
                    context.packageManager
                )
                ?.toString()

        return ImeSetupState(
            installed =
                keySwiper != null,
            enabled = enabled,
            selected = selected,
            selectedImeLabel =
                selectedLabel
        )
    }
}
