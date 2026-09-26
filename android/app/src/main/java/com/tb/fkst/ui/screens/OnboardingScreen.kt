package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tb.fkst.ui.AppViewModel

/**
 * 首次启动引导：用户政策（须勾选同意才能继续；不同意只能退出）。
 *
 * 带持久化标记（Repository.policyAgreed），所以**只会在这台设备第一次打开 App 时出现**，
 * 之后无论登录、登出、重启都不再弹。
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        PolicyStep(
            onAgree = {
                vm.acceptPolicy()
                onDone()
            },
            onExit = { vm.exitApp() },
        )
    }
}

// ---------------------------------------------------------------- 用户政策

@Composable
private fun PolicyStep(onAgree: () -> Unit, onExit: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "欢迎使用「疯狂刷题」第三方客户端",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "第一次使用前，请先阅读并同意以下说明。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("使用须知", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                PolicyBullet("本项目是个人学习用途的非官方第三方客户端，"
                    + "与「疯狂刷题」官方及其运营方没有任何关系。")
                PolicyBullet("接口协议来自对官方客户端的逆向分析。使用本客户端产生的"
                    + "一切账号风险（封号、数据异常等）由使用者自行承担。")
                PolicyBullet("请勿将本项目用于商业用途。")
                Spacer(Modifier.height(12.dp))
                Text("隐私说明", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                PolicyBullet("登录凭据只保存在本机（应用私有存储），不会上传到任何第三方服务器。")
                PolicyBullet("本客户端不收集、不分享任何用户数据。")
                PolicyBullet("清除此应用的数据会登出账号并擦除本地设置。")
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Spacer(Modifier.height(0.dp))
            Text(
                text = "我已阅读并同意以上说明",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAgree,
            enabled = checked,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("同意并继续", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("不同意，退出", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PolicyBullet(bullet: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "· ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = bullet,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
