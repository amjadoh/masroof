package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.presentation.common.MasroofFormField
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sequential edits must not rewrite previously typed digits in saved input or secrets. */
@RunWith(AndroidJUnit4::class)
class EditableDigitsPreservationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun password_preservesExactlyTypedCharacters_acrossSuccessiveEdits() {
        val value = mutableStateOf("")
        compose.setContent { MasroofTheme { MasroofFormField(value.value, { value.value = it },
            Modifier.testTag("secret"), visualTransformation = PasswordVisualTransformation()) } }
        compose.onNode(hasAnyAncestor(hasTestTag("secret")) and hasSetTextAction()).performTextInput("\u0661")
        compose.onNode(hasAnyAncestor(hasTestTag("secret")) and hasSetTextAction()).performTextInput("\u0662")
        compose.runOnIdle { assertEquals("\u0661\u0662", value.value) }
    }

    @Test fun publicInput_preservesUnderlyingCharacters_whileDigitsAreDisplayedThroughTransformation() {
        val value = mutableStateOf("")
        compose.setContent { MasroofTheme { MasroofFormField(value.value, { value.value = it }, Modifier.testTag("public-input")) } }
        compose.onNode(hasAnyAncestor(hasTestTag("public-input")) and hasSetTextAction()).performTextInput("\u0663")
        compose.onNode(hasAnyAncestor(hasTestTag("public-input")) and hasSetTextAction()).performTextInput("\u0664")
        compose.runOnIdle { assertEquals("\u0663\u0664", value.value) }
        val shown = com.baraa.masroof.presentation.locale.AppDigitsVisualTransformation.filter(
            androidx.compose.ui.text.AnnotatedString(value.value))
        assertEquals("34", shown.text.text)
    }
}
