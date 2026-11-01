package cn.edu.xatu.helper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.xatu.helper.model.TableKind
import cn.edu.xatu.helper.data.*
import cn.edu.xatu.helper.ui.CampusController
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalParityStorageTest {
    @Test fun electricityKeepsLatestFourHundredDaysWithoutTrimmingOtherDorms() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, HelperDatabase::class.java).build()
        try {
            val dao = db.electricity()
            val first = java.time.LocalDate.of(2025, 1, 1)
            dao.record(ElectricDaily("a", "other-dorm", first.toString(), "99"))
            repeat(402) { day -> dao.record(ElectricDaily("a", "dorm", first.plusDays(day.toLong()).toString(), "10")) }
            assertEquals(400, dao.history("a", "dorm").size)
            assertEquals(first.plusDays(2).toString(), dao.history("a", "dorm").first().day)
            dao.record(ElectricDaily("a", "dorm", first.plusDays(401).toString(), "8"))
            assertEquals(400, dao.history("a", "dorm").size)
            assertEquals("8", dao.history("a", "dorm").last().balance)
            assertEquals("99", dao.history("a", "other-dorm").single().balance)
        } finally { db.close() }
    }
    @Test fun reminderOnboardingIsDurableAndIsolatedByAccount() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HelperApplication
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = CampusController(app.campusRepository, scope) {}
        try {
            app.store.clear()
            app.store.select("onboarding-fixture", "1", TableKind.STUDENT)
            controller.load()
            assertFalse(controller.state.value.promptedClasses)
            assertFalse(controller.state.value.promptedExams)
            controller.acknowledgeReminders(false)
            withTimeout(5000) { while (!controller.state.value.promptedExams || controller.state.value.busy) delay(10) }
            controller.load()
            assertTrue(controller.state.value.promptedExams)
            assertFalse(controller.state.value.promptedClasses)
            controller.acknowledgeReminders(true, enable = true)
            withTimeout(5000) { while (!controller.state.value.promptedClasses || controller.state.value.busy) delay(10) }
            controller.load()
            assertTrue(controller.state.value.promptedClasses)
            assertTrue(controller.state.value.reminders.classes)
            assertTrue(controller.state.value.reminders.exams)
            assertEquals(30, controller.state.value.reminders.classLead)
            assertEquals(30, controller.state.value.reminders.examLead)
            assertEquals(21 * 60, controller.state.value.reminders.eveMinutes)
            assertEquals(8 * 60, controller.state.value.reminders.morningMinutes)
            app.store.select("another-onboarding-fixture", "1", TableKind.STUDENT)
            controller.load()
            assertFalse(controller.state.value.promptedClasses)
            assertFalse(controller.state.value.promptedExams)
        } finally { scope.cancel(); app.store.clear() }
    }
}
