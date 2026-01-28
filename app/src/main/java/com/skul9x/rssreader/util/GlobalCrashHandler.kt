package com.skul9x.rssreader.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.skul9x.rssreader.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter

class GlobalCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            val errorReport = StringBuilder()
                .append("************ CAUSE OF ERROR ************\n\n")
                .append(stackTrace.toString())
                .append("\n************ DEVICE INFORMATION ************\n")
                .append("Brand: ${android.os.Build.BRAND}\n")
                .append("Device: ${android.os.Build.DEVICE}\n")
                .append("Model: ${android.os.Build.MODEL}\n")
                .append("Id: ${android.os.Build.ID}\n")
                .append("Product: ${android.os.Build.PRODUCT}\n")
                .append("\n************ FIRMWARE ************\n")
                .append("SDK: ${android.os.Build.VERSION.SDK_INT}\n")
                .append("Release: ${android.os.Build.VERSION.RELEASE}\n")
                .append("Incremental: ${android.os.Build.VERSION.INCREMENTAL}\n")
                .toString()

            val intent = Intent(context, CrashActivity::class.java)
            intent.putExtra("error_report", errorReport)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)

            Process.killProcess(Process.myPid())
            System.exit(10)
        } catch (e: Exception) {
            // If our handler fails, let the default handler take over
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
