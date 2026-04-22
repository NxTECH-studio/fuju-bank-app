package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.LedgerApi
import studio.nxtech.fujubank.data.repository.LedgerRepository

val ledgerModule = module {
    single { LedgerApi(get()) }
    single { LedgerRepository(get()) }
}
