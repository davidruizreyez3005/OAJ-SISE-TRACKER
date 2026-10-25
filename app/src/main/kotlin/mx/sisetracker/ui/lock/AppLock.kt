package mx.sisetracker.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import mx.sisetracker.R
import mx.sisetracker.ui.theme.LocalBrandColors

/**
 * The device's own unlock: fingerprint, face or screen lock, through
 * Android's BiometricPrompt on Android 11+ and the screen-lock confirmation
 * before. Create it while the activity is being constructed (it registers
 * for an activity result).
 */
class DeviceUnlock(private val activity: ComponentActivity) {
    // Lazy: an activity has no system services before onCreate.
    private val keyguard: KeyguardManager? by lazy { activity.getSystemService(KeyguardManager::class.java) }
    private var pending: ((Boolean) -> Unit)? = null
    private val confirm = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.invoke(result.resultCode == Activity.RESULT_OK)
        pending = null
    }

    /** Whether the device has a screen lock to ask for: without one there's nothing to lock with. */
    val available: Boolean get() = keyguard?.isDeviceSecure == true

    /** Asks the user to unlock; [onResult] is true once they did (or if there's no screen lock at all). */
    fun authenticate(onResult: (Boolean) -> Unit) {
        if (!available) {
            onResult(true)
            return
        }
        val title = activity.getString(R.string.lock_prompt_title)
        val subtitle = activity.getString(R.string.lock_prompt_subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(
                    CancellationSignal(),
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                    },
                )
        } else {
            @Suppress("DEPRECATION") // The only way before Android 11 that also takes the PIN.
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent == null) {
                onResult(true)
                return
            }
            pending = onResult
            confirm.launch(intent)
        }
    }
}

/** The activity's [DeviceUnlock]; null in tests and previews. */
val LocalDeviceUnlock = staticCompositionLocalOf<DeviceUnlock?> { null }

/** Whether the app is showing its content, waiting for the unlock, or still reading the setting. */
enum class LockState { CHECKING, LOCKED, UNLOCKED }

/**
 * Shown instead of every screen while the app is locked, so no dialog or
 * sheet stays above it. Asks for the unlock once on its own; the button
 * asks again.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val brand = LocalBrandColors.current
    val unlock by rememberUpdatedState(onUnlock)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.withResumed { }
        unlock()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(brand.topBar, brand.topBarDeep)))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = brand.goldOnTopBar,
            modifier = Modifier
                .size(88.dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(22.dp),
        )
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = brand.onTopBar,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = brand.onTopBarVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = brand.onTopBar, contentColor = brand.topBar),
        ) { Text(stringResource(R.string.lock_unlock)) }
    }
}
