import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import cmp.shared.SharedApp
import cmp.shared.utils.initKoin
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.jetbrains.skiko.wasm.onWasmReady
import org.mifosx.openbanking.core.data.callback.ConsentRedirectBus
import org.mifosx.openbanking.core.data.callback.impl.WEB_CALLBACK_MARKER

/*
 * The entry point of the WebAssembly Compose application.
 *
 * 1. Initializes the Koin dependency injection framework to set up dependencies.
 * 2. Waits for the WebAssembly environment to be ready using `onWasmReady`.
 * 3. Creates a Compose viewport linked to the document body, where the UI is rendered.
 * 4. Invokes the `SharedApp` composable, which serves as the root of the app's UI.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {

    initKoin() // Set up Koin for dependency injection.

    // Web return leg of a bank sign-in: the relay lands here as `/#mifos-callback?code=…&state=w<i>.…`.
    // Hand it to the same bus as Android / iOS / desktop (replay = 1 covers the navigator subscribing
    // later), then drop the code from the address bar and history.
    if (window.location.hash.startsWith("#$WEB_CALLBACK_MARKER")) {
        ConsentRedirectBus.publish(window.location.href)
        window.history.replaceState(null, "", window.location.pathname + window.location.search)
    }

    // Apply stored language preference on startup
    val storedLanguage = localStorage.getItem("app_language")
    if (storedLanguage != null) {
        document.documentElement?.setAttribute("lang", storedLanguage)
    }

    onWasmReady {
        ComposeViewport(document.body!!) {
            // State to trigger recomposition when locale changes
            var localeVersion by remember { mutableStateOf(0) }

            // Use key() to force complete recomposition when locale changes
            key(localeVersion) {
                SharedApp(
                    updateScreenCapture = {},
                    handleRecreate = {
                        // Reload the page to apply locale changes
                        window.location.reload()
                    },
                    handleThemeMode = {},
                    handleAppLocale = { languageTag ->
                        if (languageTag != null) {
                            // Store language preference in localStorage
                            localStorage.setItem("app_language", languageTag)
                            // Set HTML lang attribute for accessibility
                            document.documentElement?.setAttribute("lang", languageTag)
                        } else {
                            // System Default: remove stored language preference
                            localStorage.removeItem("app_language")
                            // Reset to browser's default language
                            val browserLang = window.navigator.language
                            document.documentElement?.setAttribute("lang", browserLang)
                        }
                        // Reload page to apply language changes (required for web)
                        // Note: This will reload the page, and locale selection depends on browser settings
                        // window.location.reload()
                    },
                    onSplashScreenRemoved = {}
                )
            }
        }
    }
}
