package cn.edu.xatu.helper

import android.webkit.CookieManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.xatu.helper.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun additiveMigrationPreservesVersionOneTimetable() = runBlocking {
        val name = "migration-fixture.db"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, 0, null)
        old.execSQL("CREATE TABLE timetables (account TEXT NOT NULL, semesterId TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(account, semesterId, kind))")
        old.execSQL("INSERT INTO timetables VALUES ('fixture','101','STUDENT','preserved-cache',123)")
        old.version = 1
        old.close()
        val upgraded = Room.databaseBuilder(context, HelperDatabase::class.java, name).addMigrations(HelperDatabase.MIGRATION_1_2, HelperDatabase.MIGRATION_2_3).build()
        try {
            assertEquals("preserved-cache", upgraded.tables().find("fixture", "101", "STUDENT")?.payload)
            upgraded.academic().save(CachedAcademic("fixture", "GRADES", "101", "one"))
            upgraded.academic().save(CachedAcademic("fixture", "GRADES", "all", "all"))
            upgraded.academic().save(CachedAcademic("other", "GRADES", "101", "other"))
            assertEquals("one", upgraded.academic().find("fixture", "GRADES", "101")?.payload)
            assertEquals("all", upgraded.academic().find("fixture", "GRADES", "all")?.payload)
            assertEquals("other", upgraded.academic().find("other", "GRADES", "101")?.payload)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
    @Test fun versionTwoAcademicCacheSurvivesElectricityMigration() = runBlocking {
        val name = "migration-v2-fixture.db"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name,0,null)
        old.execSQL("CREATE TABLE timetables (account TEXT NOT NULL, semesterId TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(account, semesterId, kind))")
        old.execSQL("CREATE TABLE academic_cache (account TEXT NOT NULL, kind TEXT NOT NULL, scope TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(account,kind,scope))")
        old.execSQL("INSERT INTO academic_cache VALUES ('fixture','GRADES','all','preserved-grades')")
        old.version = 2; old.close()
        val db = Room.databaseBuilder(context,HelperDatabase::class.java,name).addMigrations(HelperDatabase.MIGRATION_2_3).build()
        try { assertEquals("preserved-grades",db.academic().find("fixture","GRADES","all")?.payload); assertTrue(db.electricity().history("fixture","dorm").isEmpty()) }
        finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun roomCachesAreIsolatedByAccountSemesterAndKind() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, HelperDatabase::class.java).build()
        try {
            val dao = database.tables()
            dao.save(CachedTable("alice", "1", "STUDENT", "alice-one", 1))
            dao.save(CachedTable("alice", "2", "STUDENT", "alice-two", 2))
            dao.save(CachedTable("alice", "1", "CLASS", "class", 3))
            dao.save(CachedTable("bob", "1", "STUDENT", "bob-one", 4))
            assertEquals("alice-one", dao.find("alice", "1", "STUDENT")?.payload)
            assertEquals("alice-two", dao.find("alice", "2", "STUDENT")?.payload)
            assertEquals("class", dao.find("alice", "1", "CLASS")?.payload)
            assertEquals("bob-one", dao.find("bob", "1", "STUDENT")?.payload)
            assertNull(dao.find("bob", "2", "STUDENT"))
            dao.clear(); assertNull(dao.find("alice", "1", "STUDENT"))
        } finally { database.close() }
    }
    @Test fun encryptedVaultAndHttpOnlyScopedCookiesSurviveRestore() {
        lateinit var manager: CookieManager
        instrumentation.runOnMainSync { manager = CookieManager.getInstance() }
        val vault = SecretVault(context)
        val cookies = SchoolCookies(manager, vault)
        val url = (SchoolUrls.eams + "homeExt.action").toHttpUrl()
        try {
            cookies.clear()
            cookies.saveFromResponse(url, listOf(Cookie.Builder().name("XatuMilestoneTest").value("session-fixture").hostOnlyDomain(url.host).path("/eams").httpOnly().build()))
            cookies.saveFromResponse(url, listOf(Cookie.Builder().name("XatuDomainTest").value("domain-fixture").domain(url.host).path("/eams").httpOnly().build()))
            assertTrue(manager.getCookie(url.toString()).orEmpty().contains("XatuMilestoneTest=session-fixture"))
            assertFalse(manager.getCookie("http://jwgl2018.xatu.edu.cn/").orEmpty().contains("XatuMilestoneTest"))
            cookies.persist(Credentials("fixture", "fixture-password"))
            assertEquals("fixture-password", vault.load().credentials?.password)
            assertFalse(File(context.noBackupFilesDir, "session.enc").readText().contains("fixture-password"))
            val records = vault.load().cookies
            assertTrue(records.any { it.value.contains("httponly", ignoreCase = true) && it.value.contains("path=/eams", ignoreCase = true) })
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync { manager.removeAllCookies { latch.countDown() } }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            // Recreate the adapter so restoration cannot rely on the original in-memory metadata.
            val restarted = SchoolCookies(manager, vault)
            restarted.restore()
            assertTrue(restarted.loadForRequest(url).any { it.name == "XatuMilestoneTest" })
            assertFalse(restarted.loadForRequest("http://jwgl2018.xatu.edu.cn/".toHttpUrl()).any { it.name == "XatuMilestoneTest" })
            val subdomain = manager.getCookie("http://sub.jwgl2018.xatu.edu.cn/eams/").orEmpty()
            assertFalse(subdomain.contains("XatuMilestoneTest"))
            assertTrue(subdomain.contains("XatuDomainTest=domain-fixture"))
            restarted.saveFromResponse(url, listOf(Cookie.Builder().name("XatuMilestoneTest").value("").hostOnlyDomain(url.host).path("/eams").expiresAt(0).build()))
            restarted.persist()
            val remaining = vault.load().cookies.filter { it.value.contains("XatuMilestoneTest") }
            assertTrue("Remaining fixture cookies: $remaining", remaining.isEmpty())
        } finally { cookies.clear() }
    }
}
