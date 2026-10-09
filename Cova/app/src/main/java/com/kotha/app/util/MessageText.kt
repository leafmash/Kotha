package com.kotha.app.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.kotha.app.R
import com.kotha.app.data.model.Message

object MessageText {

    private val urlPattern = Regex("(https?://|www\\.)[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    fun preview(context: Context, message: Message): String = when {
        message.deleted -> context.getString(R.string.chat_deleted)
        else -> when (message.type) {
            "image" -> context.getString(R.string.common_photo)
            "video" -> context.getString(R.string.common_video)
            "audio" -> context.getString(R.string.common_voice_message)
            "file" -> message.name.ifEmpty { context.getString(R.string.common_file) }
            else -> message.text
        }
    }

    fun copyable(message: Message): String? = when {
        message.deleted -> null
        message.isText -> message.text.ifEmpty { null }
        else -> message.url.ifEmpty { null }
    }

    fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (match in urlPattern.findAll(text)) {
            val trimmed = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
            if (trimmed.isEmpty()) continue
            append(text.substring(last, match.range.first))
            val url = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) {
                append(trimmed)
            }
            last = match.range.first + trimmed.length
        }
        append(text.substring(last))
    }
}
