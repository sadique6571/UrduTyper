package me.nomi.urdutyper.presentation.ui.dashboard.ui.main

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.ItemOpenImageBinding
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.utils.common.ImageMaker
import me.nomi.urdutyper.presentation.utils.common.Notification
import me.nomi.urdutyper.presentation.utils.extensions.adapter.BaseAdapter
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import me.nomi.urdutyper.presentation.utils.permissions.PermissionHandler
import me.nomi.urdutyper.presentation.utils.permissions.Permissions
import java.io.IOException
import java.security.AccessController.getContext


class FragmentViewPagerAdapter :
    BaseAdapter<Image, ItemOpenImageBinding>(R.layout.item_open_image) {
    var isCloud = true
    var onDelete: ((Image) -> Unit)? = null
    override fun bind(binding: ItemOpenImageBinding, item: Image) {
        val popup = PopupMenu(binding.root.context, binding.popupMenu)
        popup.menuInflater.inflate(R.menu.popup_menu, popup.menu)
        if (!isCloud) popup.menu.findItem(R.id.save).setVisible(false)
        Glide.with(binding.root.context).load(item.url).error(R.drawable.not_found)
            .into(binding.bigImage)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.delete -> {
                    binding.root.context.dialog("Are you sure want to delete this file?") {
                        negativeButton("No")
                        positiveButton("Yes") {
                            dismiss()
                            if (!isCloud)
                                ImageMaker.deleteFile(binding.root.context, item.url.toUri()) {
                                    removeItem(item)
                                    onDelete?.invoke(item)
                                }
                            else onDelete?.invoke(item)
                        }
                    }.show()
                }

                else -> {
                    checkPermission(binding.root.context, object : PermissionHandler {
                        override fun onGranted() {
                            val uri: Uri?
                            try {
                                uri = ImageMaker.getImageFromImageView(
                                    binding.root.context,
                                    binding.bigImage,
                                    System.currentTimeMillis().toString()
                                )
                                Notification.create(
                                    binding.root.context,
                                    "${item.name}.jpg",
                                    uri
                                )
                                binding.root.context.dialog("Saved: ${uri?.path}").show()
                            } catch (e: IOException) {
                                binding.root.context.dialog(
                                    e.localizedMessage ?: "Something went wrong"
                                )
                                    .show()
                            }
                        }

                        override fun onDenied() {
                            Toast.makeText(
                                binding.root.context,
                                "Required Permissions are denied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
                }
            }
            true
        }
        binding.popupMenu.setOnClickListener {
            popup.setForceShowIcon(true)
            popup.show()
        }
    }

    private fun checkPermission(context: Context, handler: PermissionHandler) {
        val firstPermission: String
        val secondPermission: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            firstPermission = Manifest.permission.READ_MEDIA_IMAGES
            secondPermission = Manifest.permission.POST_NOTIFICATIONS
        } else {
            firstPermission = Manifest.permission.READ_EXTERNAL_STORAGE
            secondPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        val permissions = arrayOf(firstPermission, secondPermission)
        Permissions.check(context, permissions.toSet().toTypedArray(), handler)
    }
}
