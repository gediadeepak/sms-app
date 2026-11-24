package com.gediadeepak.smsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.gediadeepak.smsapp.ui.RelayApp
import com.gediadeepak.smsapp.ui.RelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[RelayViewModel::class.java]
        setContent { RelayTheme { RelayApp(model) } }
    }
}
