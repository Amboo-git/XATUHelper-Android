package cn.edu.xatu.helper

import android.app.Application
import android.webkit.CookieManager
import cn.edu.xatu.helper.data.*

class HelperApplication : Application() {
    lateinit var store: LocalStore
        private set
    lateinit var campusRepository: CampusRepository
        private set
    lateinit var repository: TimetableRepository
        private set
    override fun onCreate() {
        super.onCreate()
        val vault = SecretVault(this)
        val cookies = SchoolCookies(CookieManager.getInstance().apply { setAcceptCookie(true) }, vault)
        store = LocalStore(this)
        repository = TimetableRepository(store, vault, cookies, SchoolNetwork(cookies))
        campusRepository = CampusRepository(this, store, repository, PayNetwork(cookies))
        cn.edu.xatu.helper.platform.SystemCoordinator.maintain(this)
    }
}
