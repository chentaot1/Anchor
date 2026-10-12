package com.anchor.adhd.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.anchor.adhd.ai.ModelDownloadManager
import com.anchor.adhd.ui.onboarding.ModelOnboardingScreen
import com.anchor.adhd.ui.theme.AnchorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelOnboardingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun skipDoesNotDownload() {
        var downloads = 0
        var continued = 0
        compose.setContent {
            AnchorTheme {
                ModelOnboardingScreen("1.2 GB", ModelDownloadManager.Progress(0, 0, false),
                    installed = false, runtimeAvailable = true, enoughSpace = true,
                    onDownload = { downloads++ }, onPause = {}, onContinue = { continued++ })
            }
        }
        compose.onNodeWithText("Download model (1.2 GB)").performScrollTo().assertIsEnabled()
        assertEquals(0, downloads)
        compose.onNodeWithText("Skip for now").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, continued)
        assertEquals(0, downloads)
    }

    @Test fun downloadRequiresAClickAndCanContinue() {
        var downloads = 0
        compose.setContent {
            AnchorTheme {
                ModelOnboardingScreen("1.2 GB", ModelDownloadManager.Progress(0, 0, false),
                    installed = false, runtimeAvailable = true, enoughSpace = true,
                    onDownload = { downloads++ }, onPause = {}, onContinue = {})
            }
        }
        compose.onNodeWithText("Download model (1.2 GB)").performScrollTo().performClick()
        assertEquals(1, downloads)
    }
}
