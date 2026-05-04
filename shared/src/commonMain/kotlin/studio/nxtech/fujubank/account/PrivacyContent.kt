package studio.nxtech.fujubank.account

/**
 * プライバシー関連の外部リンク URL を保持する定数置き場。
 *
 * Figma `798:12559` の「法的情報」セクションは外部ブラウザでポリシー / 利用規約を
 * 開く方針のため、本文をアプリに埋め込まず URL のみ shared に持たせる。
 *
 * 現状の URL は法務確定前の仮値（`https://example.com/...`）。本番リリース前に
 * 確定原稿の URL に差し替える必要がある。
 *
 * 後続の iOS タスク（`client-bank-9-privacy-settings-ios`）およびアカウントハブ刷新
 * （`697:8394`）の「情報」セクションも同じ定数を参照する前提のため、
 * 本タスクでシグネチャを凍結する。
 */
object PrivacyContent {
    /** プライバシーポリシー URL（仮）。法務確定後に差し替え。 */
    const val PRIVACY_POLICY_URL: String = "https://example.com/privacy-policy"

    /** 利用規約 URL（仮）。法務確定後に差し替え。 */
    const val TERMS_OF_SERVICE_URL: String = "https://example.com/terms-of-service"
}
