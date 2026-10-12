package dev.mediflow.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import java.util.TimeZone
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = MediFlowApplication::class,
    qualifiers = "w411dp-h891dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeRoutineJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var previousZone: TimeZone

    @Before
    fun setSampleTimeZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Lisbon"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousZone)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        val file = File("build/outputs/compose-previews/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
            }
        }
    }

    @Test
    fun sampleRoutineCanBeRecordedSearchedAndEdited() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("explore_sample").fetchSemanticsNodes().isNotEmpty()
        }
        capture("welcome")
        compose.onNodeWithTag("explore_sample").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Your day, in balance.").fetchSemanticsNodes().isNotEmpty()
        }
        capture("today")
        compose.onNodeWithTag("today_list").performScrollToNode(hasTestTag("take_demo-vitamin"))
        compose.onNodeWithTag("take_demo-vitamin").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Taken").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_cabinet").performClick()
        compose.waitForIdle()
        capture("cabinet")
        compose.onNodeWithTag("search_medicines").performTextInput("Magnesium")
        compose.onNodeWithTag("medicine_demo-magnesium").assertExists()
        compose.onNodeWithTag("medicine_demo-omega").assertDoesNotExist()
        compose.onNodeWithTag("search_medicines").performTextClearance()
        compose.onNodeWithTag("cabinet_add").performClick()
        compose.onNodeWithTag("save_medication").performClick()
        compose.onNodeWithTag("editor_error").assertTextContains("Enter a medication name.")
        compose.onNodeWithTag("medicine_name").performTextInput("Evening routine")
        compose.onNodeWithTag("medicine_quantity").performTextReplacement("0.5")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("medicine_name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("medicine_name").assertTextContains("Evening routine")
        compose.onNodeWithTag("medicine_quantity").assertTextContains("0.5")
        compose.onNodeWithTag("save_medication").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("save_medication").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("search_medicines").performTextInput("Evening routine")
        compose
            .onNode(hasText("Evening routine") and !hasSetTextAction(), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag("nav_insights").performClick()
        compose.waitForIdle()
        capture("insights")
        compose.onNodeWithText("Recorded as taken").assertExists()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.waitForIdle()
        capture("settings")
        compose.onNodeWithText("Routine reminders").assertExists()
    }
}
