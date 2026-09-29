package moe.frost.kochenite

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test fun scrubRemovesMessagesButKeepsExceptionTypes() {
        val event =
            SentryEvent().apply {
                message = Message().apply { formatted = "Groceries 12.34" }
                exceptions =
                    listOf(
                        SentryException().apply {
                            type = "IllegalStateException"
                            value = "Merchant Groceries amount 12.34"
                        },
                    )
                setExtra("note", "Synthetic note")
                request = Request().apply { url = "https://example.invalid" }
                addBreadcrumb(Breadcrumb("Paid Groceries").apply { category = "ui.lifecycle" })
            }

        val scrubbed = Diagnostics.scrub(event)

        assertNull(scrubbed.message)
        assertEquals("IllegalStateException", scrubbed.exceptions!!.single().type)
        assertNull(scrubbed.exceptions!!.single().value)
        assertTrue(scrubbed.extras.isNullOrEmpty())
        assertNull(scrubbed.request)
        assertNull(scrubbed.breadcrumbs!!.single().message)
        assertEquals("ui.lifecycle", scrubbed.breadcrumbs!!.single().category)
    }
}
