package com.robertohuertas.endless

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import com.orm.SugarContext


class MainActivity : AppCompatActivity() {
    private val servicoLocalizacao: ServicoLocalizacao = ServicoLocalizacao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        servicoLocalizacao.askPermissions(this, applicationContext)

        setContentView(R.layout.activity_main)

        title = "Servico de Localizacao"

        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                log("START THE FOREGROUND SERVICE ON DEMAND")
                servicoLocalizacao.actionOnService(this, Actions.START)
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                log("STOP THE FOREGROUND SERVICE ON DEMAND")
                servicoLocalizacao.actionOnService(this, Actions.STOP)
            }
        }

        findViewById<Button>(R.id.buttonBatteryOptimization).let {
            it.setOnClickListener {
                servicoLocalizacao.requestBatteryOptimization(this)
            }
        }

        SugarContext.init(this)
    }


}
