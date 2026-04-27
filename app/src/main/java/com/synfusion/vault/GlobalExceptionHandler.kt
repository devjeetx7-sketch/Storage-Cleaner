package com.synfusion.vault

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val stackTrace = exception.stackTraceToString()

        val intent = Intent(context, CrashActivity::class.java).apply {
            putExtra("EXTRA_STACK_TRACE", stackTrace)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        context.startActivity(intent)

        // Kill the app completely
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}
