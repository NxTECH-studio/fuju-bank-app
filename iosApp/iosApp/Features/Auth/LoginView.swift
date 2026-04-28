import SwiftUI
import Shared

struct LoginView: View {
    @StateObject var viewModel: LoginViewModel

    var body: some View {
        VStack(spacing: 16) {
            Text("fuju bank")
                .font(.largeTitle)
            Text("ログイン")
                .font(.title3)
                .foregroundColor(.secondary)

            TextField("メールアドレス または 公開ID", text: $viewModel.identifier)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .keyboardType(.emailAddress)
                .textFieldStyle(.roundedBorder)
                .disabled(viewModel.isSubmitting)

            SecureField("パスワード", text: $viewModel.password)
                .textFieldStyle(.roundedBorder)
                .disabled(viewModel.isSubmitting)

            if let message = viewModel.errorMessage {
                Text(message)
                    .font(.callout)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                viewModel.submit()
            } label: {
                if viewModel.isSubmitting {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("ログイン中...")
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    Text("ログイン")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isSubmitting)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}
