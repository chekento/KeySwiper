package cloud.kosch.keyswiper.test

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class ImeServiceSmokeTest {

    @Test(timeout = 120_000L)
    fun testImeCanBeSelectedAndShownWithoutCrashing() {
        val testInstrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = testInstrumentation.targetContext
        val targetPackage = targetContext.packageName
        val serviceId = "$targetPackage/.KeySwiperImeService"
        val previousIme = Settings.Secure.getString(
            targetContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val hostActivity =
            "${testInstrumentation.context.packageName}/cloud.kosch.keyswiper.test.ImeSmokeHostActivity"

        var inputMethodState = ""

        try {
            // Force-stopping the target package can also terminate the instrumentation process.
            runShell("ime enable $serviceId")
            runShell("ime set $serviceId")

            val settingsLaunch = runShell(
                "am start -W -n $targetPackage/.settings.SettingsActivity"
            )
            assertTrue(
                "KeySwiper Settings did not launch:\n$settingsLaunch",
                settingsLaunch.contains("Status: ok")
            )

            val hostLaunch = runShell("am start -W -n $hostActivity")
            assertTrue(
                "IME test host did not launch:\n$hostLaunch",
                hostLaunch.contains("Status: ok")
            )

            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                inputMethodState = runShell("dumpsys input_method")
                val normalizedState = inputMethodState.replace(" ", "")
                val serviceIsCurrent =
                    normalizedState.contains("mCurId=$serviceId") ||
                        normalizedState.contains("mCurMethodId=$serviceId")
                val inputIsShown =
                    normalizedState.contains("mInputShown=true") ||
                        normalizedState.contains("mIsInputViewShown=true")

                if (serviceIsCurrent && inputIsShown) {
                    break
                }

                SystemClock.sleep(300L)
            }

            val normalizedState = inputMethodState.replace(" ", "")
            val serviceIsCurrent =
                normalizedState.contains("mCurId=$serviceId") ||
                    normalizedState.contains("mCurMethodId=$serviceId")
            val inputIsShown =
                normalizedState.contains("mInputShown=true") ||
                    normalizedState.contains("mIsInputViewShown=true")

            assertTrue(
                "KeySwiper did not become the visible IME. Last input-method state:\n$inputMethodState",
                serviceIsCurrent && inputIsShown
            )
        } finally {
            runShell("input keyevent 4")
            runShell("input keyevent 4")

            if (!previousIme.isNullOrBlank() && previousIme != serviceId) {
                runShell("ime set $previousIme")
            } else if (previousIme.isNullOrBlank()) {
                runShell("ime reset")
            }
        }
    }

    private fun runShell(command: String): String {
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(result)
            .bufferedReader()
            .use { it.readText() }
    }
}
