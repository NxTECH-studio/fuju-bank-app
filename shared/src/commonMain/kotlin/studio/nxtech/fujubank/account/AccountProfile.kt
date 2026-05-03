package studio.nxtech.fujubank.account

/**
 * アカウントハブ画面（Figma `697:8394`）に表示するプロフィール。
 *
 * 本タスクではダミー値を返すだけだが、将来的に AuthCore / Profile API から取得する想定で
 * 画面側は必ずこの型を経由して参照する。
 */
data class AccountProfile(
    /** 表示名（例: 「山田 花子」）。プロフィールカードと「表示名」フィールドの両方で使う。 */
    val displayName: String,
    /** メールアドレス（例: 「hanako@example.com」）。 */
    val email: String,
    /** プロフィールカード内の ID 表記（例: 「ID: 1293031294904」）。 */
    val accountId: String,
)
