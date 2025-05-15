package com.example.lingobuddypck.ui.utils

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView

fun TextView.enableSelectableSaveAction(
    context: Context,
    onSave: (selectedText: String, note: String) -> Unit
) {
    this.setTextIsSelectable(true)

    this.customSelectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.add("Lưu từ này")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val start = selectionStart
            val end = selectionEnd
            val selectedText = text.substring(start, end).trim()

            if (item?.title == "Lưu từ này") {
                showNoteDialog(context, selectedText) { note ->
                    onSave(selectedText, note)
                }
                mode?.finish()
                return true
            }

            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {}
    }
}

private fun showNoteDialog(
    context: Context,
    selectedText: String,
    onSave: (note: String) -> Unit
) {
    val editText = EditText(context).apply {
        hint = "Ghi chú cho từ/câu này"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    AlertDialog.Builder(context)
        .setTitle("Lưu từ/câu")
        .setMessage("Bạn đã chọn: \"$selectedText\"\nThêm ghi chú (tuỳ chọn):")
        .setView(editText)
        .setPositiveButton("Lưu") { _, _ ->
            val note = editText.text.toString().trim()
            onSave(note)
        }
        .setNegativeButton("Hủy", null)
        .show()
}

