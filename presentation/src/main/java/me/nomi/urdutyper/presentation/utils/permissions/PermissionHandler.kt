package me.nomi.urdutyper.presentation.utils.permissions

interface PermissionHandler {
    fun onGranted()
    fun onDenied()
}