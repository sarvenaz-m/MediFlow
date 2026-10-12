package dev.mediflow.benchmarks

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class RoutineBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStart() =
        benchmark.measureRepeated(
            packageName = "dev.mediflow.app",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(),
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.pkg("dev.mediflow.app").depth(0)), 5_000))
        }
}

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val profile = BaselineProfileRule()

    @Test
    fun routineJourney() =
        profile.collect(packageName = "dev.mediflow.app") {
            pressHome()
            startActivityAndWait()
            device.wait(Until.findObject(By.text("Explore a sample routine")), 3_000)?.click()
            checkNotNull(device.wait(Until.findObject(By.text("Cabinet")), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Insights")), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Today")), 5_000)).click()
        }
}
