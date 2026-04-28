package com.example.qrattendance.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.qrattendance.R

@Composable
fun LoginScreen(state: LoginUiState, onUsernameChange: (String) -> Unit, onPasswordChange: (String) -> Unit, onLogin: () -> Unit, modifier: Modifier = Modifier) {
  var passwordVisible by remember { mutableStateOf(false) }
  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .imePadding()
      .background(MaterialTheme.colorScheme.background),
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(220.dp)
        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(76.dp))
        Text("学生考勤", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displayLarge)
        Text("扫码签到 · 请假申报 · 出勤记录", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f))
      }
    }

    Card(
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 100.dp),
    ) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
          value = state.username,
          onValueChange = onUsernameChange,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("账号") },
          placeholder = { Text("请输入账号") },
          supportingText = { Text("请输入账号") },
          leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
          singleLine = true,
        )
        OutlinedTextField(
          value = state.password,
          onValueChange = onPasswordChange,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("密码") },
          placeholder = { Text("请输入密码") },
          supportingText = { Text("请输入密码") },
          leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
            }
          },
          singleLine = true,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        )
        state.error?.let { message ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
          }
        }
        FilledTonalButton(enabled = !state.loading, onClick = onLogin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
          if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
          }
          Text("登录")
        }
        Text("如忘记账号请联系教务", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}
