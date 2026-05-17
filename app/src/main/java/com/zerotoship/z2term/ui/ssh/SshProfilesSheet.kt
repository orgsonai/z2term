package com.zerotoship.z2term.ui.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.ui.theme.TerminalFontFamily
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary
import com.zerotoship.z2term.ui.theme.ZtsBgSecondary
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextPrimary
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import java.util.UUID

/**
 * SSH プロファイルの一覧 + 追加/編集 + 接続。
 *
 * セキュリティ注意: M5 段階ではパスワードは平文 DataStore 保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshProfilesSheet(
    profiles: List<SshProfile>,
    onSave: (SshProfile) -> Unit,
    onDelete: (String) -> Unit,
    onConnect: (SshProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<SshProfile?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZtsBgSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ZtsBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SSH プロファイル",
                    color = ZtsGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    editing = SshProfile(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        host = "",
                        user = ""
                    )
                }) {
                    Text("+ 追加", color = ZtsGreen)
                }
            }

            if (profiles.isEmpty()) {
                Text(
                    "プロファイルがありません。「+ 追加」で作成してください。",
                    color = ZtsTextSecondary,
                    fontSize = 13.sp
                )
            } else {
                profiles.forEach { p ->
                    ProfileRow(
                        profile = p,
                        onClick = { onConnect(p) },
                        onEdit = { editing = p },
                        onDelete = { onDelete(p.id) }
                    )
                }
            }

            Text(
                "⚠ パスワードは平文で保存されます。M6 で Android Keystore 暗号化予定。",
                color = ZtsTextSecondary,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { current ->
        ProfileEditorDialog(
            initial = current,
            onCancel = { editing = null },
            onSave = { p ->
                onSave(p)
                editing = null
            }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: SshProfile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ZtsBorder, RoundedCornerShape(8.dp))
            .background(ZtsBgPrimary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = profile.name.ifBlank { profile.host },
                color = ZtsTextPrimary,
                fontFamily = TerminalFontFamily,
                fontSize = 14.sp
            )
            Text(
                text = "${profile.user}@${profile.host}:${profile.port}",
                color = ZtsTextSecondary,
                fontSize = 11.sp
            )
        }
        TextButton(onClick = onEdit) {
            Text("編集", color = ZtsTextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = ZtsGreen,
                contentColor = ZtsBgPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("接続", fontSize = 12.sp)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "削除",
                tint = ZtsTextSecondary
            )
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    initial: SshProfile,
    onSave: (SshProfile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.user) }
    var authType by remember { mutableStateOf(initial.authType) }
    var password by remember { mutableStateOf(initial.password) }
    var privateKey by remember { mutableStateOf(initial.privateKey) }
    var passphrase by remember { mutableStateOf(initial.keyPassphrase) }
    var initCmd by remember { mutableStateOf(initial.initCommand) }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = ZtsBgSecondary,
        title = { Text("SSH プロファイル", color = ZtsGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorField("表示名 (任意)", name) { name = it }
                EditorField("ホスト", host) { host = it }
                EditorField("ポート", port, keyboardType = KeyboardType.Number) { port = it }
                EditorField("ユーザー", user) { user = it }

                // 認証タイプ切替
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SshProfile.AuthType.entries.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            RadioButton(
                                selected = authType == t,
                                onClick = { authType = t },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ZtsGreen,
                                    unselectedColor = ZtsTextSecondary
                                )
                            )
                            Text(
                                text = if (t == SshProfile.AuthType.PASSWORD) "パスワード" else "公開鍵",
                                color = ZtsTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (authType == SshProfile.AuthType.PASSWORD) {
                    EditorField("パスワード", password, password = true) { password = it }
                } else {
                    EditorField("秘密鍵 (PEM 全文)", privateKey, multiLine = true) { privateKey = it }
                    EditorField("パスフレーズ (任意)", passphrase, password = true) { passphrase = it }
                }

                EditorField("接続後に実行 (任意)", initCmd) { initCmd = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(),
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 22,
                        user = user.trim(),
                        authType = authType,
                        password = if (authType == SshProfile.AuthType.PASSWORD) password else "",
                        privateKey = if (authType == SshProfile.AuthType.PUBLIC_KEY) privateKey else "",
                        keyPassphrase = if (authType == SshProfile.AuthType.PUBLIC_KEY) passphrase else "",
                        initCommand = initCmd
                    )
                )
            }) {
                Text("保存", color = ZtsGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("キャンセル", color = ZtsTextSecondary)
            }
        }
    )
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    multiLine: Boolean = false,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = ZtsTextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multiLine,
            maxLines = if (multiLine) 6 else 1,
            textStyle = TextStyle(
                fontFamily = TerminalFontFamily,
                fontSize = if (multiLine) 11.sp else 14.sp,
                color = ZtsTextPrimary
            ),
            cursorBrush = SolidColor(ZtsGreen),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, ZtsBorder, RoundedCornerShape(6.dp))
                .background(ZtsBgPrimary)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
