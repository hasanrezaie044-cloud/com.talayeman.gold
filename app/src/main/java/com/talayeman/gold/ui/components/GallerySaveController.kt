package com.talayeman.gold.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.talayeman.gold.util.GallerySaver
import kotlinx.coroutines.launch

/**
 * Returns a function that copies images (Files or Uris) from the app into the phone's gallery.
 * Only runs when the caller invokes it (a button tap): photos always stay inside the app too.
 * On Android 8-9 the legacy storage permission is requested at that moment.
 * [onResult] receives (saved, total).
 */
@Composable
fun rememberGallerySave(onResult: (saved: Int, total: Int) -> Unit): (List<Any>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<List<Any>>(emptyList()) }

    fun run(models: List<Any>) {
        scope.launch {
            var ok = 0
            models.forEach { if (GallerySaver.save(context, it)) ok++ }
            callback(ok, models.size)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val models = pending
        pending = emptyList()
        if (granted) run(models) else callback(0, models.size)
    }

    return { models ->
        if (models.isNotEmpty()) {
            if (GallerySaver.needsLegacyPermission &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pending = models
                permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                run(models)
            }
        }
    }
}
