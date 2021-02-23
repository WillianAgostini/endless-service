package com.robertohuertas.endless

import android.os.Environment
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

//fun log(msg: String) {
//    Log.d("ENDLESS-SERVICE", msg)
//}

fun log(msg: String?) {
    Log.d("ENDLESS-SERVICE", msg)
    val logFile = File(
        Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .toString() + "/file.log"
    )
    if (!logFile.exists()) {
        try {
            logFile.createNewFile()
        } catch (e: IOException) {
            Log.d("ENDLESS-SERVICE", e.message)
        }
    }
    try {
        //BufferedWriter for performance, true to set append to file flag
        val buf = BufferedWriter(FileWriter(logFile, true))
        buf.append(msg)
        buf.newLine()
        buf.close()
    } catch (e: IOException) {
        Log.d("ENDLESS-SERVICE", e.message)
    }
}