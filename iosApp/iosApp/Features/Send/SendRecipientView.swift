import SwiftUI
import Shared

/// 送金フロー Step 1 — 送金先選択画面（iOS）。
///
/// - 上部: 戻る `<` / タイトル「送金」/ 余白
/// - 検索 TextField + 候補 List
/// - 候補タップで `.sheet` + `.presentationDetents([.medium])` で bottom sheet 確認
struct SendRecipientView: View {
    @ObservedObject var viewModel: ObservableSendFlowViewModel
    var onBack: () -> Void
    var onProceedToAmount: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            VStack(spacing: 12) {
                searchField
                resultsArea
            }
            .padding(.horizontal, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .sheet(item: $viewModel.confirmCandidate) { candidate in
            ConfirmCandidateSheet(
                candidate: candidate,
                onConfirm: {
                    viewModel.confirmCandidate(candidate)
                    onProceedToAmount()
                },
                onCancel: { viewModel.cancelCandidateConfirm() },
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }

    private var header: some View {
        ZStack {
            Text("送金")
                .font(FujuBankTypography.headline)
                .foregroundStyle(FujuBankPalette.textPrimary)
            HStack {
                Button(action: onBack) {
                    Image("ChevronLeft")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                Spacer()
                Color.clear.frame(width: 48, height: 48)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }

    private var searchField: some View {
        TextField("公開IDで送金先を検索", text: $viewModel.query)
            .textFieldStyle(.plain)
            .font(.system(size: 14))
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(FujuBankPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(FujuBankPalette.hairline, lineWidth: 1),
            )
            .padding(.top, 8)
            .submitLabel(.search)
    }

    @ViewBuilder
    private var resultsArea: some View {
        switch viewModel.searchState {
        case .idle:
            HintView(message: "公開IDを入力して送金先を検索してください")
        case .needsMoreChars:
            HintView(message: "2 文字以上で検索してください")
        case .loading:
            VStack {
                Spacer()
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(FujuBankPalette.brandPink)
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .ready(results):
            if results.isEmpty {
                HintView(message: "該当ユーザーが見つかりません")
            } else {
                ResultsList(
                    results: results,
                    onTap: { viewModel.tapCandidate($0) },
                )
            }
        case let .error(message):
            HintView(message: message, isError: true)
        }
    }
}

private struct HintView: View {
    let message: String
    var isError: Bool = false

    var body: some View {
        VStack {
            Text(message)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(isError ? Color.red : FujuBankPalette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, 32)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct ResultsList: View {
    let results: [UserSearchResult]
    let onTap: (UserSearchResult) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(results, id: \.id) { result in
                    Button(action: { onTap(result) }) {
                        CandidateRow(result: result)
                    }
                    .buttonStyle(.plain)
                    if result.id != results.last?.id {
                        Divider()
                            .background(FujuBankPalette.transactionDivider)
                            .padding(.leading, 64)
                    }
                }
            }
            .background(FujuBankPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

private struct CandidateRow: View {
    let result: UserSearchResult

    var body: some View {
        HStack(spacing: 12) {
            AvatarPlaceholder(size: 40)
            Text("@" + result.publicId)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(FujuBankPalette.textPrimary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

private struct ConfirmCandidateSheet: View {
    let candidate: UserSearchResult
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer().frame(height: 8)
            AvatarPlaceholder(size: 96)
            Text("@" + candidate.publicId)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)
            Spacer().frame(height: 8)
            Button(action: onConfirm) {
                Text("決定")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(FujuBankPalette.brandPink)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            Button(action: onCancel) {
                Text("キャンセル")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(FujuBankPalette.textSecondary)
            }
            .buttonStyle(.plain)
            Spacer().frame(height: 8)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(FujuBankPalette.surface)
    }
}

private struct AvatarPlaceholder: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(FujuBankPalette.avatarPerson)
            Image("AvatarTomato")
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(Circle())
        }
        .frame(width: size, height: size)
    }
}

// `Shared.UserSearchResult` を `.sheet(item:)` で扱うために Identifiable を後付けする。
extension UserSearchResult: Identifiable {}
