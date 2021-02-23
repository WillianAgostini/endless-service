package com.robertohuertas.endless.dao

import com.orm.SugarRecord


class LogDao : SugarRecord(), IDao {
    override var enviado: Int = 0

    var deviceId: String? = null
    var terminal: String? = null
    var data_computador_bordo: String? = null
    var version = 0
    var log: String? = null



    override fun saveDao() {
        super.save()
    }
}
