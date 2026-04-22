package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.ArtifactApi
import studio.nxtech.fujubank.data.repository.ArtifactRepository

val artifactModule = module {
    single { ArtifactApi(get()) }
    single { ArtifactRepository(get()) }
}
