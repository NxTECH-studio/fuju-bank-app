import SwiftUI
import Shared

struct MfaVerifyView: View {
    @StateObject var viewModel: MfaVerifyViewModel

    var body: some View {
        VStack(spacing: 16) {
            Text("二段階認証")
                .font(.title2)

            Picker("入力モード", selection: Binding(
                get: { viewModel.mode },
                set: { viewModel.switchMode(to: $0) }
            )) {
                Text("認証アプリ").tag(MfaVerifyViewModel.InputMode.totp)
                Text("リカバリコード").tag(MfaVerifyViewModel.InputMode.recovery)
            }
            .pickerStyle(.segmented)

            TextField(viewModel.mode == .totp ? "6 桁コード" : "リカバリコード", text: $viewModel.code)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .keyboardType(viewModel.mode == .totp ? .numberPad : .default)
                .textFieldStyle(.roundedBorder)
                .disabled(viewModel.isSubmitting)

            if let message = viewModel.errorMessage {
                Text(message)
                    .font(.callout)
                    .foregroundColor(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                viewModel.submit()
            } label: {
                if viewModel.isSubmitting {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("確認中...")
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    Text("確認")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isSubmitting)

            Button("キャンセル") {
                viewModel.cancel()
            }
            .disabled(viewModel.isSubmitting)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}
