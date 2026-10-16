package com.biko.client.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.ensureBuvid
import com.biko.client.data.loginBySms
import com.biko.client.data.qrcodeGenerate
import com.biko.client.data.qrcodePoll
import com.biko.client.data.refreshProfile
import com.biko.client.data.sendSmsCode
import com.biko.client.ui.theme.biko
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginState(
    val qrBitmap: Bitmap? = null,
    val status: String = "正在获取二维码…",
    val success: Boolean = false,
    val expired: Boolean = false,
    // 0 扫码 1 短信
    val mode: Int = 0,
    val phone: String = "",
    val code: String = "",
    val captchaKey: String = "",
    val countdown: Int = 0,
    val smsStatus: String = "",
    val smsBusy: Boolean = false,
)

class LoginViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private var qrcodeKey = ""

    init { generate() }

    fun generate() {
        viewModelScope.launch {
            _state.update { it.copy(status = "正在获取二维码…", expired = false, success = false) }
            try {
                runCatching { repo.ensureBuvid() }
                val url = repo.qrcodeGenerate()
                qrcodeKey = url.substringAfter("qrcode_key=").substringBefore("&")
                val bitmap = withContext(Dispatchers.Default) { generateQrBitmap(url, 720) }
                _state.update { it.copy(qrBitmap = bitmap, status = "打开哔哩哔哩 APP 扫码登录") }
                poll()
            } catch (e: Exception) {
                _state.update {
                    it.copy(status = "二维码获取失败：${e.message ?: "网络错误"}", expired = true)
                }
            }
        }
    }

    private suspend fun poll() {
        repeat(90) {
            delay(2000)
            val code = runCatching { repo.qrcodePoll(qrcodeKey) }.getOrNull() ?: return@repeat
            when (code) {
                0 -> {
                    // 先落盘再刷新资料，确保登录态一定写进去
                    runCatching { repo.refreshProfile() }
                    _state.update { it.copy(status = "登录成功！", success = true) }
                    return
                }
                86038 -> {
                    _state.update { it.copy(status = "二维码已过期，点击刷新", expired = true) }
                    return
                }
                86090 -> _state.update { it.copy(status = "已扫码，请在手机上确认") }
            }
        }
    }

    // ---- 短信登录 ----

    fun setMode(mode: Int) = _state.update { it.copy(mode = mode, smsStatus = "") }

    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v.filter(Char::isDigit).take(11)) }

    fun onCodeChange(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(6)) }

    fun sendSms() {
        val phone = _state.value.phone
        if (phone.length != 11) {
            _state.update { it.copy(smsStatus = "请输入 11 位手机号") }
            return
        }
        if (_state.value.smsBusy || _state.value.countdown > 0) return
        _state.update { it.copy(smsBusy = true, smsStatus = "正在发送…") }
        viewModelScope.launch {
            runCatching { repo.sendSmsCode(phone) }
                .onSuccess { key ->
                    _state.update { it.copy(smsBusy = false, captchaKey = key, smsStatus = "验证码已发送") }
                    // 60 秒重发倒计时
                    repeat(60) { i ->
                        _state.update { it.copy(countdown = 60 - i) }
                        delay(1000)
                    }
                    _state.update { it.copy(countdown = 0) }
                }
                .onFailure { e ->
                    _state.update { it.copy(smsBusy = false, smsStatus = e.message ?: "发送失败") }
                }
        }
    }

    fun loginBySms() {
        val s = _state.value
        if (s.smsBusy) return
        if (s.captchaKey.isEmpty()) {
            _state.update { it.copy(smsStatus = "请先获取验证码") }
            return
        }
        if (s.code.length != 6) {
            _state.update { it.copy(smsStatus = "请输入 6 位验证码") }
            return
        }
        _state.update { it.copy(smsBusy = true, smsStatus = "正在登录…") }
        viewModelScope.launch {
            runCatching { repo.loginBySms(s.phone, s.code, s.captchaKey) }
                .onSuccess {
                    runCatching { repo.refreshProfile() }
                    _state.update { it.copy(smsBusy = false, smsStatus = "登录成功！", success = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(smsBusy = false, smsStatus = e.message ?: "登录失败") }
                }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

/** 扫码登录内容（不含顶栏），「订阅」页未登录时直接内嵌复用。 */
@Composable
fun QrLoginPanel(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(236.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.card)
                .clickable(enabled = state.expired) { viewModel.generate() },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = state.qrBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "登录二维码",
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Text(
            text = state.status,
            fontSize = 14.sp,
            color = colors.t2,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "登录后可点赞 · 投币 · 收藏 · 评论 · 关注",
            fontSize = 12.sp,
            color = colors.t3,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun LoginScreen(onBack: () -> Unit, viewModel: LoginViewModel = viewModel()) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) { if (state.success) onBack() }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 5.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(start = 8.dp, end = 3.dp, top = 8.dp, bottom = 8.dp),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "登录",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        }

        // 方式切换：扫码 / 短信
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
        ) {
            listOf("扫码登录" to 0, "短信登录" to 1).forEach { (label, mode) ->
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (state.mode == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (state.mode == mode) colors.acc else colors.t3,
                    modifier = Modifier.clickable { viewModel.setMode(mode) },
                )
            }
        }

        if (state.mode == 0) {
            QrLoginPanel(
                modifier = Modifier.weight(1f).padding(bottom = 48.dp),
                viewModel = viewModel,
            )
        } else {
            SmsLoginPanel(
                state = state,
                onPhoneChange = viewModel::onPhoneChange,
                onCodeChange = viewModel::onCodeChange,
                onSend = viewModel::sendSms,
                onLogin = viewModel::loginBySms,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 短信登录表单：手机号 + 验证码 + 发送倒计时 + 登录按钮。「订阅」页未登录时复用。 */
@Composable
fun SmsLoginPanel(
    state: LoginState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSend: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = biko
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // 手机号
        androidx.compose.material3.OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // 验证码 + 发送按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text("短信验证码") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (state.countdown > 0) "${state.countdown}s 后重发" else "发送验证码",
                fontSize = 13.sp,
                color = if (state.countdown > 0) colors.t3 else colors.acc,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = state.countdown == 0 && !state.smsBusy, onClick = onSend)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        // 登录按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (state.code.length == 6) colors.acc else colors.card2)
                .clickable(enabled = state.code.length == 6 && !state.smsBusy, onClick = onLogin)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.smsBusy) "请稍候…" else "登录",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.code.length == 6) androidx.compose.ui.graphics.Color.White else colors.t3,
            )
        }
        if (state.smsStatus.isNotEmpty()) {
            Text(
                text = state.smsStatus,
                fontSize = 12.5.sp,
                color = colors.t2,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            text = "未注册的手机号将自动注册。若触发安全验证，请改用扫码登录。",
            fontSize = 12.sp,
            color = colors.t3,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
