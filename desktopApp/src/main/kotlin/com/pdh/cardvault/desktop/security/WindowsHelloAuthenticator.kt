package com.pdh.cardvault.desktop.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

enum class SensitiveAction(val prompt: String) {
    RevealCardSecrets("请验证身份以显示银行卡完整信息"),
    EditCard("请验证身份以编辑银行卡"),
    DeleteCard("请验证身份以删除银行卡"),
    EditAddress("请验证身份以编辑地址"),
    DeleteAddress("请验证身份以删除地址"),
    ExportVault("请验证身份以导出 CardVault 加密文件"),
    ImportVault("请验证身份以导入 CardVault 加密文件"),
}

fun interface SensitiveActionAuthenticator {
    suspend fun authenticate(action: SensitiveAction): Boolean
}

enum class UserConsentVerificationOutcome {
    Verified,
    Canceled,
    Unavailable,
    Failed,
}

/** Injectable boundary around the Windows UserConsentVerifier API. */
fun interface UserConsentVerifier {
    fun requestVerification(prompt: String): UserConsentVerificationOutcome
}

class WindowsHelloAuthenticator(
    private val verifier: UserConsentVerifier = PowerShellUserConsentVerifier(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SensitiveActionAuthenticator {
    override suspend fun authenticate(action: SensitiveAction): Boolean = withContext(dispatcher) {
        runCatching { verifier.requestVerification(action.prompt) }
            .getOrDefault(UserConsentVerificationOutcome.Failed) == UserConsentVerificationOutcome.Verified
    }
}

/**
 * Calls the WinRT verifier through the inbox Windows PowerShell executable. The child process is
 * bounded, hidden, non-interactive, and its raw output is never surfaced to callers.
 */
class PowerShellUserConsentVerifier(
    private val systemRoot: String? = System.getenv("SystemRoot"),
    private val timeoutSeconds: Long = MAX_WAIT_SECONDS,
) : UserConsentVerifier {
    override fun requestVerification(prompt: String): UserConsentVerificationOutcome {
        if (timeoutSeconds !in 1..MAX_WAIT_SECONDS) return UserConsentVerificationOutcome.Failed
        val executable = resolvePowerShell() ?: return UserConsentVerificationOutcome.Unavailable
        val encodedCommand = buildEncodedCommand(prompt)
        val process = try {
            ProcessBuilder(
                executable.toString(),
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                encodedCommand,
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: Exception) {
            return UserConsentVerificationOutcome.Unavailable
        }

        return try {
            process.outputStream.close()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return UserConsentVerificationOutcome.Failed
            }
            if (process.exitValue() != 0) return UserConsentVerificationOutcome.Failed
            val output = process.inputStream.use { it.readNBytes(MAX_OUTPUT_BYTES + 1) }
            try {
                if (output.size > MAX_OUTPUT_BYTES) return UserConsentVerificationOutcome.Failed
                when {
                    output.contentEquals(VERIFIED_BYTES) -> UserConsentVerificationOutcome.Verified
                    output.contentEquals(CANCELED_BYTES) -> UserConsentVerificationOutcome.Canceled
                    output.contentEquals(UNAVAILABLE_BYTES) -> UserConsentVerificationOutcome.Unavailable
                    else -> UserConsentVerificationOutcome.Failed
                }
            } finally {
                output.fill(0)
            }
        } catch (_: Exception) {
            UserConsentVerificationOutcome.Failed
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    private fun resolvePowerShell(): Path? {
        val root = systemRoot?.takeIf(String::isNotBlank) ?: return null
        val rootPath = runCatching { Path.of(root).normalize() }.getOrNull() ?: return null
        if (!rootPath.isAbsolute) return null
        return rootPath.resolve("System32")
            .resolve("WindowsPowerShell")
            .resolve("v1.0")
            .resolve("powershell.exe")
            .normalize()
            .takeIf(Files::isRegularFile)
    }

    private fun buildEncodedCommand(prompt: String): String {
        val promptBytes = prompt.toByteArray(StandardCharsets.UTF_8)
        val promptBase64 = try {
            Base64.getEncoder().encodeToString(promptBytes)
        } finally {
            promptBytes.fill(0)
        }
        val script = listOf(
            "\$ErrorActionPreference='Stop'",
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "\$verifier=[Windows.Security.Credentials.UI.UserConsentVerifier,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]",
            "\$availabilityType=[Windows.Security.Credentials.UI.UserConsentVerifierAvailability,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]",
            "\$resultType=[Windows.Security.Credentials.UI.UserConsentVerificationResult,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]",
            "\$asTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{\$_.Name -eq 'AsTask' -and \$_.IsGenericMethodDefinition -and \$_.GetParameters().Count -eq 1 -and \$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'}|Select-Object -First 1)",
            "if(\$null -eq \$asTask){exit 2}",
            "\$availabilityOp=\$verifier::CheckAvailabilityAsync()",
            "\$availabilityTask=\$asTask.MakeGenericMethod(\$availabilityType).Invoke(\$null,@(\$availabilityOp))",
            "\$availabilityTask.Wait()",
            "if(\$availabilityTask.Result.ToString() -ne 'Available'){[Console]::Out.Write('Unavailable');exit 0}",
            "\$prompt=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$promptBase64'))",
            "\$operation=\$verifier::RequestVerificationAsync(\$prompt)",
            "\$task=\$asTask.MakeGenericMethod(\$resultType).Invoke(\$null,@(\$operation))",
            "\$task.Wait()",
            "[Console]::Out.Write(\$task.Result.ToString())",
        ).joinToString(";")
        val scriptBytes = script.toByteArray(StandardCharsets.UTF_16LE)
        return try {
            Base64.getEncoder().encodeToString(scriptBytes)
        } finally {
            scriptBytes.fill(0)
        }
    }

    private companion object {
        const val MAX_WAIT_SECONDS = 120L
        const val MAX_OUTPUT_BYTES = 64
        val VERIFIED_BYTES = "Verified".toByteArray(StandardCharsets.US_ASCII)
        val CANCELED_BYTES = "Canceled".toByteArray(StandardCharsets.US_ASCII)
        val UNAVAILABLE_BYTES = "Unavailable".toByteArray(StandardCharsets.US_ASCII)
    }
}
