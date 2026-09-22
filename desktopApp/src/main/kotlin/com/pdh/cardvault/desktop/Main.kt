package com.pdh.cardvault.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pdh.cardvault.desktop.data.DesktopCardRepository
import com.pdh.cardvault.desktop.data.EncryptedDesktopVault
import com.pdh.cardvault.desktop.sync.SyncCoreDesktopGateway
import com.pdh.cardvault.desktop.ui.CardVaultDesktopApp
import com.pdh.cardvault.desktop.ui.DesktopAppController
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.contains("--self-test-local-vault")) {
        val directory = EncryptedDesktopVault.defaultDirectory()
        Files.createDirectories(directory)
        val resultFile = directory.resolve("self-test-result.txt")
        val result = runCatching { EncryptedDesktopVault().load() }
        val diagnostic = result.fold(
            onSuccess = { "OK" },
            onFailure = { error -> "ERROR:${error::class.java.name}" },
        )
        Files.writeString(resultFile, diagnostic)
        exitProcess(if (result.isSuccess) 0 else 2)
    }
    launchApplication()
}

private fun launchApplication() = application {
    val state = rememberWindowState(width = 1440.dp, height = 900.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "CardVault",
    ) {
        window.minimumSize = Dimension(1180, 760)
        val initialized = remember {
            runCatching {
                DesktopAppController(
                    repository = DesktopCardRepository(EncryptedDesktopVault()),
                    syncGateway = SyncCoreDesktopGateway(),
                )
            }
        }
        initialized.fold(
            onSuccess = { controller ->
                DisposableEffect(window, controller) {
                    val listener = object : WindowAdapter() {
                        override fun windowLostFocus(event: WindowEvent?) {
                            controller.concealSensitiveInformation()
                        }

                        override fun windowStateChanged(event: WindowEvent?) {
                            if (event != null && event.newState and Frame.ICONIFIED != 0) {
                                controller.concealSensitiveInformation()
                            }
                        }
                    }
                    window.addWindowFocusListener(listener)
                    window.addWindowStateListener(listener)
                    onDispose {
                        window.removeWindowFocusListener(listener)
                        window.removeWindowStateListener(listener)
                        controller.close()
                    }
                }
                CardVaultDesktopApp(controller)
            },
            onFailure = {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF191919)), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("无法安全打开 CardVault")
                            Spacer(Modifier.height(8.dp))
                            Text("本地密钥或加密数据不可用。为保护数据，应用没有继续读取。", color = Color.White.copy(alpha = 0.48f))
                        }
                    }
                }
            },
        )
    }
}
