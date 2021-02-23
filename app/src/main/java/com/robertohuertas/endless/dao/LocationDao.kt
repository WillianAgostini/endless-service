package com.robertohuertas.endless.dao

import android.annotation.SuppressLint
import com.orm.SugarRecord
import java.text.SimpleDateFormat
import java.util.*

class LocationDao() : SugarRecord(), IDao  {
    override var enviado: Int = 0

    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var time: Long = 0
    var timeDate: String? = null

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