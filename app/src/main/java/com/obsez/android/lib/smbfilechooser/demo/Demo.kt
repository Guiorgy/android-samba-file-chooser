@file:Suppress("unused")

package com.obsez.android.lib.smbfilechooser.demo

import android.content.Context
import com.obsez.android.lib.smbfilechooser.FileChooserDialog

object Demo {
    fun demo1(context: Context, startPath: String, callback: FileChooserDialog.OnChosenListener) {
        FileChooserDialog.newDialog(context)
            .displayPath(true)
            .setIcon(R.mipmap.ic_launcher)
            .setFilterRegex(false, true, ".*\\.(jpe?g|png)")
            .setStartFile(startPath)
            .setResources(R.string.title_choose_file, R.string.title_choose, R.string.dialog_cancel)
            .setOnChosenListener(callback)
            .setNavigateUpTo { true }
            .setNavigateTo { true }
            .build()
            .show()
    }

    fun demo2(context: Context, startPath: String, onChosenListener: FileChooserDialog.OnChosenListener, onSelectedListener: FileChooserDialog.OnSelectedListener) {
        FileChooserDialog.newDialog(context)
            .displayPath(true)
            .setFilter(false, true, "jpg", "jpeg", "png")
            .setStartFile(startPath)
            .enableOptions(true)
            .enableMultiple(true, false)
            .setResources(R.string.title_choose_file, R.string.title_choose, R.string.dialog_cancel)
            .setOptionResources(R.string.option_create_folder, R.string.options_delete, R.string.new_folder_cancel, R.string.new_folder_ok)
            .setOnSelectedListener(onSelectedListener)
            .setOnChosenListener(onChosenListener)
            .setNavigateUpTo { true }
            .setNavigateTo { true }
            .setDateFormat("dd MMMM yyyy")
            .build()
            .show()
    }
}
