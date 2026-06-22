package com.xty.englishhelper.ui.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing

/**
 * 输入框行组件
 *
 * @param title 设置项标题
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
 * @param placeholder 占位符文字
 * @param isPassword 是否密码输入
 * @param keyboardType 键盘类型
 * @param singleLine 是否单行
 * @param supportingText 辅助说明文字
 */
@Composable
fun SettingsTextFieldRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null
) {
    val spacing = LocalEhSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it) } },
            visualTransformation = if (isPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = singleLine,
            supportingText = supportingText?.let { { Text(it) } }
        )
    }
}
