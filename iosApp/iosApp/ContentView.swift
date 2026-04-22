import SwiftUI
import Shared

struct ContentView: View {
    @State private var showContent = false
    var body: some View {
        VStack {
            Button("Click me!") {
                withAnimation {
                    showContent = !showContent
                }
            }

            // TODO: remove after smoke test
            Button("Smoke test: UserApi.get") {
                Task { await runUserApiSmokeTest() }
            }

            if showContent {
                VStack(spacing: 16) {
                    Image(systemName: "swift")
                        .font(.system(size: 200))
                        .foregroundColor(.accentColor)
                    Text("SwiftUI: \(Greeting().greet())")
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding()
    }
}

// TODO: remove after smoke test
private let smokeTestTag = "FujuBankSmoke"

// TODO: remove after smoke test
private let smokeTestUserId = "00000000-0000-0000-0000-000000000000"

// TODO: remove after smoke test
private func runUserApiSmokeTest() async {
    let api = KoinIosKt.userApi()
    do {
        let result = try await api.get(userId: smokeTestUserId)
        print("[\(smokeTestTag)] result=\(result)")
    } catch {
        print("[\(smokeTestTag)] threw: \(error)")
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
