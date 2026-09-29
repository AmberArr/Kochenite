package moe.frost.kochenite

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.SentryTransaction
import moe.frost.kochenite.persistence.ledger.StorageDiagnostics

/**
 * Crash, ANR, release-health and app-start/frame diagnostics. Events carry exception types and stack
 * frames only: messages, which could echo financial input, are removed before sending.
 */
internal object Diagnostics {
    fun start(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = BuildConfig.BUILD_TYPE
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.isAttachServerName = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isAnrEnabled = true
            options.isEnableAutoSessionTracking = true
            options.tracesSampleRate = 1.0
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
            options.beforeSendTransaction = SentryOptions.BeforeSendTransactionCallback { transaction, _ -> scrub(transaction) }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ -> scrub(breadcrumb) }
        }
        StorageDiagnostics.reporter = { Sentry.captureException(it) }
    }

    fun scrub(event: SentryEvent): SentryEvent {
        event.message = null
        event.exceptions?.forEach { it.value = null }
        return scrubBase(event)
    }

    fun scrub(transaction: SentryTransaction): SentryTransaction = scrubBase(transaction)

    private fun <T : SentryBaseEvent> scrubBase(event: T): T {
        event.extras = emptyMap()
        event.request = null
        event.breadcrumbs?.forEach(::scrub)
        return event
    }

    fun scrub(breadcrumb: Breadcrumb): Breadcrumb {
        breadcrumb.message = null
        return breadcrumb
    }
}
