package com.example.lingobuddypck.utils

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

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
                checkLanguageAndShowDialog(context, selectedText) { note ->
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

private fun checkLanguageAndShowDialog(
    context: Context,
    selectedText: String,
    onSave: (note: String) -> Unit
) {
    if (selectedText.length > 30) {
        AlertDialog.Builder(context)
            .setTitle("Không thể lưu")
            .setMessage("Văn bản đã chọn quá dài. Giới hạn là 30 ký tự.")
            .setPositiveButton("OK", null)
            .show()
        return
    }

    val languageIdentifier = LanguageIdentification.getClient()
    languageIdentifier.identifyLanguage(selectedText)
        .addOnSuccessListener { languageCode ->
            if (languageCode == "en") {
                showNoteDialog(context, selectedText, onSave)
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Không thể lưu")
                    .setMessage("Chỉ có thể lưu các từ tiếng Anh.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        .addOnFailureListener {
            // If language detection fails, we'll show the dialog anyway
            showNoteDialog(context, selectedText, onSave)
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

    val dialog = AlertDialog.Builder(context)
        .setTitle("Lưu từ/câu")
        .setMessage("Bạn đã chọn: \"$selectedText\"\nThêm ghi chú (tuỳ chọn):")
        .setView(editText)
        .setPositiveButton("Lưu") { _, _ ->
            val note = editText.text.toString().trim()
            onSave(note)
        }
        .setNegativeButton("Hủy", null)
        .setNeutralButton("Dịch", null) // We'll override this below
        .create()

    dialog.show()

    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
        translateText(context, selectedText) { translation ->
            editText.setText(translation)
        }
    }
}

private fun translateText(
    context: Context,
    text: String,
    onTranslated: (String) -> Unit
) {
    val options = TranslatorOptions.Builder()
        .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
        .setTargetLanguage(com.google.mlkit.nl.translate.TranslateLanguage.VIETNAMESE)
        .build()
    
    val translator = Translation.getClient(options)

    translator.downloadModelIfNeeded()
        .addOnSuccessListener {
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    onTranslated(translatedText)
                }
                .addOnFailureListener {
                    AlertDialog.Builder(context)
                        .setTitle("Lỗi")
                        .setMessage("Không thể dịch văn bản. Vui lòng thử lại sau.")
                        .setPositiveButton("OK", null)
                        .show()
                }
        }
        .addOnFailureListener {
            AlertDialog.Builder(context)
                .setTitle("Lỗi")
                .setMessage("Không thể tải mô hình dịch. Vui lòng kiểm tra kết nối mạng.")
                .setPositiveButton("OK", null)
                .show()
        }
}

