package com.verdi.app

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(VerdiPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
