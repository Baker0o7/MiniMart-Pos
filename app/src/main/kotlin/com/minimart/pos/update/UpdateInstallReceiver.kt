package com.minimart.pos.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives Android's answer about an update: asks the owner to confirm, or reports why it failed. Not exported. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                              else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> { /* the app is replaced and restarted by Android */ }
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.installFailed("The update was cancelled. Nothing was changed.")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                updates.installFailed("Android refused the update because it does not match the installed app. Nothing was changed.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> updates.installFailed("Not enough free space to install the update.")
            else -> updates.installFailed("Android could not install the update. Nothing was changed.")
        }
    }
}
