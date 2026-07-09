package com.linuxhost

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { LinuxHostDatabase.get(androidContext()) }
    single { get<LinuxHostDatabase>().instanceDao() }
    single { get<LinuxHostDatabase>().eventDao() }
    single { get<LinuxHostDatabase>().storageDao() }
    single { get<LinuxHostDatabase>().backupDao() }
    single { get<LinuxHostDatabase>().downloadDao() }

    single { ProotEngine(androidContext()) }
    single { TerminalSession(androidContext()) }

    factory { InstallViewModel(get()) }
}

fun initKoin(context: Context) {
    org.koin.core.context.startKoin {
        androidContext(context)
        modules(appModule)
    }
}
