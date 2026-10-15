package com.mindflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindflow.app.ui.AppViewModel
import com.mindflow.app.ui.MindflowRoot
import com.mindflow.app.ui.theme.MindflowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MindflowApp
        setContent {
            MindflowTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(app))
                MindflowRoot(vm)
            }
        }
    }
}
