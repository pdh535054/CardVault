package com.pdh.cardvault.desktop.data

import com.sun.jna.platform.win32.Crypt32Util

interface LocalKeyProtector {
    fun protect(plainKey: ByteArray): ByteArray
    fun unprotect(protectedKey: ByteArray): ByteArray
}

/** Binds the local vault key to the currently signed-in Windows account using DPAPI. */
class WindowsDpapiKeyProtector : LocalKeyProtector {
    init {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Windows 本地密钥保护不可用。"
        }
    }

    override fun protect(plainKey: ByteArray): ByteArray = try {
        Crypt32Util.cryptProtectData(plainKey)
    } catch (_: Throwable) {
        throw LocalKeyUnavailableException()
    }

    override fun unprotect(protectedKey: ByteArray): ByteArray = try {
        Crypt32Util.cryptUnprotectData(protectedKey)
    } catch (_: Throwable) {
        throw LocalKeyUnavailableException()
    }
}

class LocalKeyUnavailableException : IllegalStateException("当前 Windows 用户无法解锁本地卡包。")
