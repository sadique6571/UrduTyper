package me.nomi.urdutyper.presentation.utils.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Permissions {

    fun check(context: Context, permissions: Array<String>, handler: PermissionHandler) {
        val permissionsList = ArrayList<String>()
        permissionsList.addAll(listOf(*permissions))
        var allPermissionProvided = true
        for (permission in permissionsList) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionProvided = false
                break
            }
        }
        if (allPermissionProvided) {
            handler.onGranted()
        } else {
            PermissionsActivity.permissionHandler = handler
            val intent = Intent(context, PermissionsActivity::class.java)
            intent.putExtra(PermissionsActivity.EXTRA_PERMISSIONS, permissionsList)
            context.startActivity(intent)
        }
    }
}