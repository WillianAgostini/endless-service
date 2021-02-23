package com.robertohuertas.endless.dao

import android.annotation.SuppressLint
import com.orm.SugarRecord
import java.text.SimpleDateFormat
import java.util.*


class LogDao : SugarRecord(), IDao {
    var isIgnoringBatteryOptimizations: Boolean = false
    var isDeviceIdleMode: Boolean = false
    var isPowerSaveMode: Boolean = false
    override var enviado: Int = 0

    var deviceId: String? = null
    var terminal: String? = null
    var data_computador_bordo: String? = null
    var version = 0
    var log: String? = null

    @SuppressLint("SimpleDateFormat")
    var creationDate: String? = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmmZ").format(
        Date(
            System.currentTimeMillis()
        )
    )

    override fun saveDao() {
        super.save()
    }
}
