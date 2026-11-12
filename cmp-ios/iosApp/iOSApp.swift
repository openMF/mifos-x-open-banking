import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // HSBC's consent redirect. The registered HTTPS callback page relays the response
                // to our custom scheme, which iOS delivers here. Forwarded straight into shared
                // Kotlin, where the data layer parses and authenticates it.
                .onOpenURL { url in
                    ConsentRedirectBridgeKt.handleConsentRedirect(url: url.absoluteString)
                }
        }
    }
}
