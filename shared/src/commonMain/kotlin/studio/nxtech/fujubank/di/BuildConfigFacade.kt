package studio.nxtech.fujubank.di

import studio.nxtech.fujubank.BuildKonfig

/**
 * BuildKonfig は internal なため、composeApp など他モジュールから参照するための公開ファサード。
 * Debug/Release・プラットフォームによって値が切り替わる。
 */
fun defaultBankApiBaseUrl(): String = BuildKonfig.BANK_API_BASE_URL

/**
 * ActionCable の WebSocket エンドポイント。Debug/Release・プラットフォームで切り替わる。
 */
fun defaultCableUrl(): String = BuildKonfig.CABLE_URL
