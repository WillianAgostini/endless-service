package com.robertohuertas.endless

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

class ServicoLocalizacao {

    fun askPermissions(activity: Activity, context: Context) {

        Dexter.withActivity(activity)
            .withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    log("onPermissionsChecked")
                    actionOnService(context, Actions.START)
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                    log("onPermissionRationaleShouldBeShown")
                }
            })
            .check()
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimization(context: Context) {
        val packageName = context.packageName

        val intent = Intent()
        val pm = context.getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager

        if (pm.isIgnoringBatteryOptimizations(packageName))
            intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        else {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
        }


        context.startActivity(intent)
    }

    fun actionOnService(context: Context, action: Actions) {
        if (getServiceState(context) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(context, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                context.startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            context.startService(it)
        }
    }

}