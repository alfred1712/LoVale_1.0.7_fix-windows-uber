package com.example.lovale2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Explicit device test; uses the existing browser session, never submits forms. */
class MovidaLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensMovidaWebsite() {
        compose.onNodeWithContentDescription("Movida Ya").performClick()
        compose.onNodeWithText("Abrir Movida Ya").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        var found = false
        while (!found && android.os.SystemClock.elapsedRealtime() < deadline) {
            val text = visibleText(automation.rootInActiveWindow).lowercase()
            // Installed PWA opens without a browser URL bar on this device.
            found = (text.contains("movidaya.com.ar") && text.contains("recibir mi link")) ||
                (text.contains("fichas disponibles") && text.contains("ver diagnóstico") &&
                    text.contains("pronóstico gratis"))
            if (!found) Thread.sleep(300)
        }
        automation.takeScreenshot()?.let { bitmap ->
            try {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                java.io.File(context.getExternalFilesDir(null), "movida-test.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { bitmap.recycle() }
        }
        assertTrue("Movida Ya login or existing session must be visible in the browser", found)
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Abrir Movida Ya")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Abrir Movida Ya").assertIsDisplayed()
    }
    private fun visibleText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        try {
            return buildString {
                if (!node.isEditable) append(node.text).append(' ').append(node.contentDescription).append(' ')
                for (i in 0 until node.childCount) append(visibleText(node.getChild(i)))
            }
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
