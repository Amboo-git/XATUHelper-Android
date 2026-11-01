package cn.edu.xatu.helper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cn.edu.xatu.helper.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var helperModel: HelperViewModel
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        helperModel.openDestination(intent.getStringExtra("open_page"))
        intent.removeExtra("open_page")
    }
    override fun onResume() { super.onResume(); if (::helperModel.isInitialized) helperModel.resume() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = HelperViewModel((application as HelperApplication).repository, (application as HelperApplication).campusRepository) { cn.edu.xatu.helper.platform.SystemCoordinator.synchronize(application) } as T
        })[HelperViewModel::class.java]
        helperModel = model
        model.openDestination(intent.getStringExtra("open_page")); intent.removeExtra("open_page")
        setContent { HelperTheme { HelperApp(model) } }
    }
}
