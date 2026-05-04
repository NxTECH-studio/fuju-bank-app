import SwiftUI
import UIKit

/// アカウント情報の単一フィールド（表示名 or メール）を編集するボトムシート。
///
/// Android `AccountInfoEditSheet.kt` と 1:1 の入力 UX を SwiftUI `.sheet` で再現する:
/// - 1 つの `TextField` を中央に配置し、下部に「保存」ボタン
/// - `validate(value)` が `false` の間は保存ボタンを disabled
/// - シート外スワイプダウン or 「キャンセル」ボタンで閉じる
/// - `presentationDetents([.medium, .large])` で半開きを許容しつつ、キーボード展開時は大きくなる
///
/// 入力値は `@State` で保持する。`.sheet(isPresented:)` で表示するたびに view が
/// 再生成されるため `init` で `_value = State(initialValue:)` で初期化する標準パターンを採る。
struct AccountInfoEditSheetView: View {
    let title: String
    let label: String
    let initialValue: String
    let keyboardType: UIKeyboardType
    let contentType: UITextContentType?
    let autocapitalization: TextInputAutocapitalization
    let validate: (String) -> Bool
    let onSave: (String) -> Void
    let onCancel: () -> Void

    @State private var value: String

    init(
        title: String,
        label: String,
        initialValue: String,
        keyboardType: UIKeyboardType = .default,
        contentType: UITextContentType? = nil,
        autocapitalization: TextInputAutocapitalization = .sentences,
        validate: @escaping (String) -> Bool,
        onSave: @escaping (String) -> Void,
        onCancel: @escaping () -> Void,
    ) {
        self.title = title
        self.label = label
        self.initialValue = initialValue
        self.keyboardType = keyboardType
        self.contentType = contentType
        self.autocapitalization = autocapitalization
        self.validate = validate
        self.onSave = onSave
        self.onCancel = onCancel
        _value = State(initialValue: initialValue)
    }

    private var trimmed: String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isValid: Bool { validate(trimmed) }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextField(label, text: $value)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(keyboardType)
                    .textContentType(contentType)
                    .textInputAutocapitalization(autocapitalization)
                    .autocorrectionDisabled(keyboardType == .emailAddress)
                    .submitLabel(.done)

                Spacer()

                Button(action: { onSave(trimmed) }) {
                    Text("保存")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(FujuBankPalette.brandPink)
                .disabled(!isValid)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(FujuBankPalette.surface.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("キャンセル", action: onCancel)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
