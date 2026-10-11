package com.baraa.masroof.presentation.locale

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.baraa.masroof.application.locale.AppDigits

/** One character stays one character, so the cursor and underlying input are preserved. */
object AppDigitsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        AnnotatedString(AppDigits.display(text.text), text.spanStyles, text.paragraphStyles),
        OffsetMapping.Identity,
    )
}
