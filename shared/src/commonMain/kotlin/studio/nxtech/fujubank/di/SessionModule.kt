package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.session.SessionStore

val sessionModule = module {
    single { SessionStore() }
}
