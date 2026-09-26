package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.School
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
 * 首次启动引导，两步：
 *
 * 1. 用户政策（须勾选同意才能继续；不同意只能退出）
 * 2. 「为什么没有刷题模块」的说明（看完进入，或永远跳过）
 *
 * 两步都各自带持久化标记（Repository.policyAgreed / noExerciseExplained），
 * 所以**只会在这台设备第一次打开 App 时出现**，之后无论登录、登出、重启都不再弹。
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {

    var step by rememberSaveable { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        when (step) {
            0 -> PolicyStep(
                onAgree = {
                    vm.acceptPolicy()
                    step = 1
                },
                onExit = { vm.exitApp() },
            )
            else -> NoExerciseStep(
                onDone = {
                    vm.markNoExerciseExplained()
                    onDone()
                },
            )
        }
    }
}

// ---------------------------------------------------------------- 第一步：用户政策

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

// ---------------------------------------------------------------- 第二步：为什么没有刷题

@Composable
private fun NoExerciseStep(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Filled.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "为什么没有「刷题」模块？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "明明叫「疯狂刷题」，这个客户端却不带刷题——先说清楚，免得你找了半天。",
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
                Text(
                    "不是没做，是做了又拿掉了",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ExplainerPara("刷题部分的接口全部摸通过：知识点树、试卷库、题目流、错题本……"
                    + "一整个刷题界面其实做出来过一版。装上试用之后发现问题出在服务端，只好整块拿掉。")
                Spacer(Modifier.height(14.dp))
                Text(
                    "原因一：题目和知识点对不上",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ExplainerPara("官方接口不接受任何筛选参数——选「小学·数学·图形与几何」"
                    + "和选「高中·英语·完形填空」，服务端返回的是同一批按固定顺序排列的题。"
                    + "界面上选了知识点，出来的题却和它无关，这种「看起来能用」的功能"
                    + "比没有更糟糕，所以删掉了。")
                Spacer(Modifier.height(14.dp))
                Text(
                    "原因二：试卷点不进去",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ExplainerPara("试卷列表能拉到，但每张卷子的题量字段永远是 0，"
                    + "取题目列表的接口永远返回空，网页版答题页也有访问限制。"
                    + "也就是试卷只能看个标题，做不了。")
                Spacer(Modifier.height(14.dp))
                Text(
                    "现在：换成了「试卷库」",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ExplainerPara("后来把试卷那块的接口重新挖了一遍，发现题目其实是拿得到的——"
                    + "只是要四个参数一起传，之前差了一个。于是 1.9.0 开始有了「试卷」板块："
                    + "能按年级、教材版本浏览真实试卷，能搜、能收藏，"
                    + "点进去能看到整份卷子的题目、答案和解析。")
                Spacer(Modifier.height(8.dp))
                ExplainerPara("在线交卷本来对第三方一律拒绝访问，但 1.11.0 找到了解法："
                    + "用本账号的合法会话拼出官方 H5 实时答题页（paperExercises-v18），"
                    + "丢进内置浏览器——这样就能真答题、交卷了，做过的题在「我的 → 答题记录」里回看。"
                    + "所以它现在既是查卷看解析的工具，也能直接刷官方题。")
                Spacer(Modifier.height(14.dp))
                Text(
                    "社区这边照旧",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                ExplainerPara("发现、搜索、私信（防撤回/置顶/发图）、发布笔记（带音频）、"
                    + "收藏、投币这些都做了，而且能比官方客户端多做一点。"
                    + "什么时候官方接口把「按知识点出题」开放了，刷题模块随时可以加回来。")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("知道了，开始使用", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ExplainerPara(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
