import Foundation
import EmbraceIO

@objcMembers class EmbraceInitializer: NSObject {
    // Start the EmbraceSDK with the minimum required settings, for more advanced configuration options see:
    // https://embrace.io/docs/ios/open-source/embrace-options/
    static func start() -> Void {
        do {
            try Embrace
                .setup(
                    options: Embrace.Options(
                        appId: "cvKeD",
                        platform: .reactNative
                    )
                )
                .start()
        } catch let e {
            print("Error starting Embrace \(e.localizedDescription)")
        }
    }
}
