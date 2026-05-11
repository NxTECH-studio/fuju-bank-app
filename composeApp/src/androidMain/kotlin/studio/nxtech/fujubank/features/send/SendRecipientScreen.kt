package studio.nxtech.fujubank.features.send

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.domain.model.UserSearchResult
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * 送金フロー Step 1 — 送金先選択画面。
 *
 * - 上部: 戻る `<` / タイトル「送金」(中央 17sp Bold) / 余白 (右 48dp)
 * - 検索 OutlinedTextField（プレースホルダ「公開IDで送金先を検索」）
 * - 検索結果 LazyColumn（候補タップで bottom sheet 表示 → 「決定」で Step 2）
 *
 * 親 (RootScaffold) のボトムナビは送金フロー中は非表示にする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendRecipientScreen(
    viewModel: SendFlowViewModel,
    onBack: () -> Unit,
    onProceedToAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        text = "公開IDで送金先を検索",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 14.sp,
                            color = FujuBankColors.TextTertiary,
                        ),
                    )
                },
                supportingText = {
                    Text(
                        text = "英数字 2〜32 文字",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 12.sp,
                            color = FujuBankColors.TextTertiary,
                        ),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FujuBankColors.BrandPink,
                    unfocusedBorderColor = FujuBankColors.Hairline,
                    cursorColor = FujuBankColors.BrandPink,
                    focusedContainerColor = FujuBankColors.Surface,
                    unfocusedContainerColor = FujuBankColors.Surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state.searchState) {
                    SendFlowState.SearchState.Idle -> Hint(
                        message = "公開IDを入力して送金先を検索してください",
                    )
                    SendFlowState.SearchState.NeedsMoreChars -> Hint(
                        message = "2 文字以上で検索してください",
                    )
                    SendFlowState.SearchState.InvalidChars -> Hint(
                        message = "英数字のみ、2〜32 文字で入力してください",
                    )
                    SendFlowState.SearchState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = FujuBankColors.BrandPink,
                            strokeWidth = 2.dp,
                        )
                    }
                    is SendFlowState.SearchState.Ready -> if (s.results.isEmpty()) {
                        Hint(message = "該当ユーザーが見つかりません")
                    } else {
                        ResultsList(
                            results = s.results,
                            onTap = viewModel::onCandidateTap,
                        )
                    }
                    is SendFlowState.SearchState.Error -> Hint(
                        message = s.message,
                        isError = true,
                    )
                }
            }
        }
    }

    val confirmCandidate = state.confirmCandidate
    if (confirmCandidate != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onCandidateConfirmCancel,
            sheetState = sheetState,
            containerColor = FujuBankColors.Surface,
        ) {
            ConfirmSheetContent(
                candidate = confirmCandidate,
                onConfirm = {
                    viewModel.onCandidateConfirm()
                    onProceedToAmount()
                },
                onCancel = viewModel::onCandidateConfirmCancel,
            )
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "送金",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "戻る",
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.size(48.dp).align(Alignment.CenterEnd))
    }
}

@Composable
private fun Hint(message: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = message,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isError) FujuBankColors.Error else FujuBankColors.TextSecondary,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsList(
    results: List<UserSearchResult>,
    onTap: (UserSearchResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(FujuBankColors.Surface),
    ) {
        items(results, key = { it.id }) { result ->
            CandidateRow(result = result, onClick = { onTap(result) })
            HorizontalDivider(
                color = FujuBankColors.TransactionDivider,
                thickness = 1.dp,
                modifier = Modifier.padding(start = 64.dp),
            )
        }
    }
}

@Composable
private fun CandidateRow(
    result: UserSearchResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarPlaceholder(size = 40.dp)
        Text(
            text = "@" + result.publicId,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujuBankColors.TextPrimary,
            ),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AvatarPlaceholder(size: androidx.compose.ui.unit.Dp) {
    // 実際のアイコン取得（Coil 等）は本タスクでは導入しない。トマト等の placeholder と同様、
    // 既存の `ic_avatar_tomato` を使い回して円形クロップ表示する。
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(FujuBankColors.AvatarPerson),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_avatar_tomato),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun ConfirmSheetContent(
    candidate: UserSearchResult,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarPlaceholder(size = 96.dp)
        Text(
            text = "@" + candidate.publicId,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Button(
            onClick = onConfirm,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FujuBankColors.BrandPink,
                contentColor = FujuBankColors.Surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "決定",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        TextButton(onClick = onCancel) {
            Text(
                text = "キャンセル",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = FujuBankColors.TextSecondary,
                ),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
    }
}

