package me.nomi.urdutyper.presentation.utils.extensions.common

import android.accounts.AccountManager
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.DownloadManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.SearchManager
import android.app.UiModeManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothManager
import android.content.ClipboardManager
import android.content.Intent
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRouter
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSessionManager
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.nfc.NfcManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.DropBoxManager
import android.os.PowerManager
import android.os.UserManager
import android.os.Vibrator
import android.os.storage.StorageManager
import android.print.PrintManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import android.view.inputmethod.InputMethodManager
import android.view.textservice.TextServicesManager
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController

fun Fragment.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT): Unit =
    requireActivity().toast(text, duration)

inline fun <reified T : Any> Fragment.start(extras: Intent.() -> Unit) =
    requireActivity().start<T>(extras)

inline fun <reified T : Any> Fragment.start() = requireActivity().start<T>()

inline fun <reified T : Any> Fragment.startActivityForResult(
    requestCode: Int,
    options: Bundle? = null,
    action: String? = null
) = requireActivity().startActivityForResult<T>(requestCode, options, action)

inline fun <reified T : Any> Fragment.startActivity() = requireActivity().startActivity<T>()

fun Fragment.getNavController(@IdRes navHostFragmentId: Int): NavController =
    requireActivity().getNavController(navHostFragmentId)

fun Fragment.findNavController(
    @IdRes viewId: Int
): NavController = requireActivity().findNavController(viewId)

fun Fragment.navigate(
    @IdRes viewId: Int,
    @IdRes action: Int
) = requireActivity().findNavController(viewId).navigate(action)

fun Fragment.finish() = requireActivity().finish()

val Fragment.displayWidth: Int
    get() = requireContext().resources.displayMetrics.widthPixels

val Fragment.displayHeight: Int
    get() = requireContext().resources.displayMetrics.heightPixels

fun Fragment.inflateLayout(
    layoutResId: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = false
): View = LayoutInflater.from(requireActivity()).inflate(layoutResId, parent, attachToRoot)

val Fragment.packageManager: PackageManager
    get() = requireActivity().packageManager

val Fragment.accessibilityManager: AccessibilityManager
    get() = requireContext().accessibilityManager

val Fragment.accountManager: AccountManager
    get() = requireContext().accountManager

val Fragment.activityManager: ActivityManager
    get() = requireContext().activityManager

val Fragment.alarmManager: AlarmManager
    get() = requireContext().alarmManager

val Fragment.appWidgetManager: AppWidgetManager
    get() = requireContext().appWidgetManager

val Fragment.appOpsManager: AppOpsManager
    get() = requireContext().appOpsManager

val Fragment.audioManager: AudioManager
    get() = requireContext().audioManager

val Fragment.batteryManager: BatteryManager
    get() = requireContext().batteryManager

val Fragment.bluetoothManager: BluetoothManager
    get() = requireContext().bluetoothManager

val Fragment.cameraManager: CameraManager
    get() = requireContext().cameraManager

val Fragment.captioningManager: CaptioningManager
    get() = requireContext().captioningManager

val Fragment.clipboardManager: ClipboardManager
    get() = requireContext().clipboardManager

val Fragment.connectivityManager: ConnectivityManager
    get() = requireContext().connectivityManager

val Fragment.consumerIrManager: ConsumerIrManager
    get() = requireContext().consumerIrManager

val Fragment.devicePolicyManager: DevicePolicyManager
    get() = requireContext().devicePolicyManager

val Fragment.displayManager: DisplayManager
    get() = requireContext().displayManager

val Fragment.downloadManager: DownloadManager
    get() = requireContext().downloadManager

val Fragment.dropBoxManager: DropBoxManager
    get() = requireContext().dropBoxManager

val Fragment.inputMethodManager: InputMethodManager
    get() = requireContext().inputMethodManager

val Fragment.inputManager: InputManager
    get() = requireContext().inputManager

val Fragment.jobScheduler: JobScheduler
    get() = requireContext().jobScheduler

val Fragment.keyguardManager: KeyguardManager
    get() = requireContext().keyguardManager

val Fragment.launcherApps: LauncherApps
    get() = requireContext().launcherApps

val Fragment.locationManager: LocationManager
    get() = requireContext().locationManager

val Fragment.mediaProjectionManager: MediaProjectionManager
    get() = requireContext().mediaProjectionManager

val Fragment.mediaRouter: MediaRouter
    get() = requireContext().mediaRouter

val Fragment.mediaSessionManager: MediaSessionManager
    get() = requireContext().mediaSessionManager

val Fragment.nfcManager: NfcManager
    get() = requireContext().nfcManager

val Fragment.notificationManager: NotificationManager
    get() = requireContext().notificationManager

val Fragment.nsdManager: NsdManager
    get() = requireContext().nsdManager

val Fragment.powerManager: PowerManager
    get() = requireContext().powerManager

val Fragment.printManager: PrintManager
    get() = requireContext().printManager

val Fragment.restrictionsManager: RestrictionsManager
    get() = requireContext().restrictionsManager

val Fragment.searchManager: SearchManager
    get() = requireContext().searchManager

val Fragment.sensorManager: SensorManager
    get() = requireContext().sensorManager

val Fragment.storageManager: StorageManager
    get() = requireContext().storageManager

val Fragment.telecomManager: TelecomManager
    get() = requireContext().telecomManager

val Fragment.telephonyManager: TelephonyManager
    get() = requireContext().telephonyManager

val Fragment.textServicesManager: TextServicesManager
    get() = requireContext().textServicesManager

val Fragment.tvInputManager: TvInputManager
    get() = requireContext().tvInputManager

val Fragment.uiModeManager: UiModeManager
    get() = requireContext().uiModeManager

val Fragment.usbManager: UsbManager
    get() = requireContext().usbManager

val Fragment.userManager: UserManager
    get() = requireContext().userManager

val Fragment.vibrator: Vibrator
    get() = requireContext().vibrator

val Fragment.wallpaperManager: WallpaperManager
    get() = requireContext().wallpaperManager

val Fragment.wifiP2pManager: WifiP2pManager
    get() = requireContext().wifiP2pManager

val Fragment.wifiManager: WifiManager
    get() = requireContext().wifiManager

val Fragment.windowManager: WindowManager
    get() = requireContext().windowManager

val Fragment.defaultSharedPreferences: SharedPreferences
    get() = requireContext().defaultSharedPreferences