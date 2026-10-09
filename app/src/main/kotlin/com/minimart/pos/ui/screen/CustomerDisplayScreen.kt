package com.minimart.pos.ui.screen

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.minimart.pos.display.CustomerDisplayContent
import com.minimart.pos.display.CustomerDisplayHub
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CustomerDisplayViewModel @Inject constructor(val hub: CustomerDisplayHub) : ViewModel()

/** On-device customer view: turn the tablet around, or hold it up. Back or the ✕ returns to the till. */
@Composable
fun CustomerDisplayScreen(onExit: () -> Unit, vm: CustomerDisplayViewModel = hiltViewModel()) {
    val state by vm.hub.state.collectAsState()
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize()) {
        CustomerDisplayContent(state)
        Icon(Icons.Default.Close, "Exit customer view", tint = Color.White.copy(0.5f),
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(28.dp).clickable(onClick = onExit))
    }
}
