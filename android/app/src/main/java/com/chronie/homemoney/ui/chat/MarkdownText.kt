package com.chronie.homemoney.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Thin adapter around the Apache-2.0 multiplatform-markdown-renderer.
 *
 * While tokens are still arriving we render plain text — re-parsing an
 * incomplete Markdown document on every delta would flicker and churn the
 * AST. The full Markdown renderer runs once the message is complete.
 *
 * Keeping the third-party call isolated here makes future upgrades a
 * one-file change.
 */
@Composable
fun ChatMarkdownText(
    content: String,
    isStreaming: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    if (isStreaming) {
        // A trailing caret makes it obvious that the model is still writing;
        // it is render-only and never persisted into the conversation.
        Text(
            text = content + " ▍",
            style = MiuixTheme.textStyles.body2,
            color = textColor,
            modifier = modifier
        )
    } else {
        // m3 defaults read MaterialTheme (not MiuixTheme), which would render
        // dark text on dark bubbles. Bind the body color to the active theme.
        Markdown(
            content = content,
            modifier = modifier,
            colors = markdownColor(text = textColor)
        )
    }
}
