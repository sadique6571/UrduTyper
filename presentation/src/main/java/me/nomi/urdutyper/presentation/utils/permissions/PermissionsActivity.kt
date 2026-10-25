package me.nomi.urdutyper.presentation.utils.permissions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.presentation.utils.extensions.common.toast

@AndroidEntryPoint
class PermissionsActivity : AppCompatActivity() {

    private var cleanHandlerOnDestroy = true
    private var allPermissions: List<String> = emptyList()
    private var deniedPermissions: ArrayList<String> = ArrayList()
    private val handler get() = permissionHandler!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        if (intent == null || !intent.hasExtra(EXTRA_PERMISSIONS)) {
            finish()
            return
        }

        window.setBackgroundDrawable(null)
        allPermissions = intent.getStringArrayListExtra(EXTRA_PERMISSIONS) ?: emptyList()

        for (permission in allPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission)
            }
        }

        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted.")
            grant()
        } else {
            Log.d(TAG, "Requesting permissions.")
            requestPermissions(deniedPermissions.toTypedArray(), PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            if (!shouldShowRequestPermissionRationale(permissions[0])) {
                showSettingsDialog()
                return
            } else {
                toast("Permission denied.")
                finish()
            }
        }
    }

    private fun showSettingsDialog() {
        val permissionNames = deniedPermissions.joinToString(", ") { permission ->
            val permissionInfo = packageManager.getPermissionInfo(permission, 0)
            permissionInfo.loadLabel(packageManager).toString()
        }

        val message =
            "Required permissions ($permissionNames) are not granted. Please go to settings and enable them."

        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
                        "package",
                        packageName, null
                    )
                )
                settingsActivityResultLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                deny()
            }
            .show()
    }

    private val settingsActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && permissionHandler != null) {
                Permissions.check(this, allPermissions.toTypedArray(), handler)
                cleanHandlerOnDestroy = false
            }
            finish()
        }

    override fun onDestroy() {
        if (cleanHandlerOnDestroy) {
            permissionHandler = null
        }
        super.onDestroy()
    }

    private fun deny() {
        if (permissionHandler != null) {
            handler.onDenied()
        }
        finish()
    }

    private fun grant() {
        if (permissionHandler != null) {
            handler.onGranted()
        }
        finish()
    }

    companion object {
        internal const val EXTRA_PERMISSIONS = "permissions"
        internal var permissionHandler: PermissionHandler? = null
        internal const val TAG = "PermissionsActivity"
        private const val PERMISSION = 101
    }
}