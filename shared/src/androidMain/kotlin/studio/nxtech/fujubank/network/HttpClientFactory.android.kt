package studio.nxtech.fujubank.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import okhttp3.Dns
import java.net.Inet4Address

// Cloudflare 配下のホスト (`*.fujupay.app`) は AAAA を返すが、Android Emulator は
// IPv6 ルーティングが不安定で `ConnectException: Failed to connect to .../[v6addr]:443`
// を起こす。Dns.SYSTEM の解決結果を IPv4 (Inet4Address) 優先に並べ替えて返すことで、
// OkHttp の Happy Eyeballs が IPv4 を先に試すようにする。IPv4 が無い環境では
// IPv6 を返すため、IPv6-only ネットワークでも fallback で動く。
private val IPV4_PREFERRED_DNS = Dns { hostname ->
    Dns.SYSTEM.lookup(hostname).sortedByDescending { it is Inet4Address }
}

actual fun createHttpClient(config: HttpClientConfig): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                dns(IPV4_PREFERRED_DNS)
            }
        }
        applyCommon(config)
        if (config.enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("Ktor", message)
                    }
                }
            }
        }
    }
