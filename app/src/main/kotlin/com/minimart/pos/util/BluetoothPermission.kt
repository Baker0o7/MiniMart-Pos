package com.minimart.pos.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * On Android 12+ every Bluetooth call that touches a paired device needs BLUETOOTH_CONNECT granted at runtime.
 * Callers check this first and degrade gracefully (empty list, "not allowed" error) instead of relying on a crash handler.
 */
fun Context.hasBluetoothConnectPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
