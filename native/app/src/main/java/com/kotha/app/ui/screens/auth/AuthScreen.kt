package com.kotha.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kotha.app.ui.theme.BrandFont
import com.kotha.app.ui.theme.brandBrush
import com.kotha.app.ui.theme.coveBackdrop
import com.kotha.app.util.AppLanguage
import com.kotha.app.util.Links

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .coveBackdrop()
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(R.drawable.splash_icon),
                contentDescription = null,
                modifier = Modifier.size(132.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = BrandFont,
                    fontWeight = FontWeight.Bold,
                    brush = brandBrush()
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.auth_tag),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(visible = state.signUp) {
                Column {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
                        label = { Text(stringResource(R.string.auth_name)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
                label = { Text(stringResource(R.string.auth_email)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
                label = { Text(stringResource(R.string.auth_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    viewModel.submit()
                })
            )

            if (!state.signUp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::forgotPassword, enabled = !state.busy) {
                        Text(stringResource(R.string.auth_forgot))
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            GradientButton(
                text = stringResource(
                    when {
                        state.busy -> R.string.auth_wait
                        state.signUp -> R.string.auth_sign_up
                        else -> R.string.auth_sign_in
                    }
                ),
                enabled = !state.busy,
                onClick = {
                    focus.clearFocus()
                    viewModel.submit()
                }
            )

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            state.info?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.infoIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.auth_or),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF3C4043),
                    disabledContainerColor = Color(0xFFEDEDED),
                    disabledContentColor = Color(0xFF8A8D91)
                ),
                border = BorderStroke(1.dp, Color(0xFFDADCE0))
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.auth_google))
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (state.signUp) R.string.auth_have_account else R.string.auth_new_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = viewModel::toggleMode, enabled = !state.busy) {
                    Text(stringResource(if (state.signUp) R.string.auth_sign_in else R.string.auth_sign_up))
                }
            }

            val linkStyle = TextLinkStyles(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            )
            val legal = buildAnnotatedString {
                append(stringResource(R.string.auth_legal_prefix))
                append(" ")
                withLink(LinkAnnotation.Clickable("terms", linkStyle) { Links.openLegal(context, "terms") }) {
                    append(stringResource(R.string.legal_terms))
                }
                append(" ")
                append(stringResource(R.string.auth_legal_and))
                append(" ")
                withLink(LinkAnnotation.Clickable("privacy", linkStyle) { Links.openLegal(context, "privacy") }) {
                    append(stringResource(R.string.legal_privacy))
                }
                append(stringResource(R.string.auth_legal_suffix))
            }
            Text(
                text = legal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            TextButton(onClick = { AppLanguage.toggle() }) {
                Text(if (AppLanguage.code() == "en") "বাংলা" else "English")
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
)

@Composable
private fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val brush = brandBrush()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(brush, shape, alpha = if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}
