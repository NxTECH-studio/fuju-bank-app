package studio.nxtech.fujubank.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MfaVerifyScreen(viewModel: MfaVerifyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "二段階認証",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        PrimaryTabRow(selectedTabIndex = if (state.mode == MfaInputMode.TOTP) 0 else 1) {
            Tab(
                selected = state.mode == MfaInputMode.TOTP,
                onClick = { viewModel.onModeChange(MfaInputMode.TOTP) },
                text = { Text("認証アプリ") },
            )
            Tab(
                selected = state.mode == MfaInputMode.RECOVERY,
                onClick = { viewModel.onModeChange(MfaInputMode.RECOVERY) },
                text = { Text("リカバリコード") },
            )
        }

        OutlinedTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            label = {
                Text(if (state.mode == MfaInputMode.TOTP) "6 桁コード" else "リカバリコード")
            },
            singleLine = true,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (state.mode == MfaInputMode.TOTP) KeyboardType.NumberPassword else KeyboardType.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (state.isSubmitting) "確認中..." else "確認")
        }

        TextButton(
            onClick = viewModel::cancel,
            enabled = !state.isSubmitting,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("キャンセル")
        }
    }
}
