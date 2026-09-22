import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.imageio.ImageIO

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.pdh.cardvault"
version = "1.5.6"

val generatedWindowsIcon = layout.buildDirectory.file("generated/cardvault/cardvault.ico")
val generateWindowsIcon = tasks.register("generateWindowsIcon") {
    outputs.file(generatedWindowsIcon)
    doLast {
        val target = generatedWindowsIcon.get().asFile
        target.parentFile.mkdirs()
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.paint = GradientPaint(28f, 20f, Color(42, 50, 66), 224f, 236f, Color(7, 9, 14))
            graphics.fill(RoundRectangle2D.Float(8f, 8f, 240f, 240f, 58f, 58f))
            graphics.color = Color(175, 198, 255)
            graphics.stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            graphics.draw(RoundRectangle2D.Float(54f, 66f, 148f, 98f, 24f, 24f))
            graphics.drawLine(66, 104, 190, 104)
            graphics.color = Color(244, 246, 252)
            graphics.fill(RoundRectangle2D.Float(72f, 184f, 112f, 13f, 7f, 7f))
        } finally {
            graphics.dispose()
        }
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        DataOutputStream(target.outputStream().buffered()).use { output ->
            fun littleShort(value: Int) {
                output.writeByte(value and 0xFF)
                output.writeByte(value ushr 8 and 0xFF)
            }
            fun littleInt(value: Int) {
                output.writeByte(value and 0xFF)
                output.writeByte(value ushr 8 and 0xFF)
                output.writeByte(value ushr 16 and 0xFF)
                output.writeByte(value ushr 24 and 0xFF)
            }
            littleShort(0)
            littleShort(1)
            littleShort(1)
            output.writeByte(0)
            output.writeByte(0)
            output.writeByte(0)
            output.writeByte(0)
            littleShort(1)
            littleShort(32)
            littleInt(png.size)
            littleInt(22)
            output.write(png)
        }
        png.fill(0)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":sync-core"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.pdh.cardvault.desktop.MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "CardVault"
            packageVersion = "1.5.6"
            description = "CardVault offline encrypted card and address wallet"
            vendor = "pdh"
            windows {
                menuGroup = "CardVault"
                upgradeUuid = "9d7356af-889d-4436-a31c-f436ac29cb5c"
                iconFile.set(generatedWindowsIcon)
            }
        }
    }
}

// Gradle 9.6 pre-creates the Compose plugin's @OutputDirectory immediately before its
// jlink action, while JDK 21 refuses an already-existing --output directory. Keep this
// task deliberately output-less so Gradle does not recreate the directory after cleanup.
val runtimeImageDir = layout.buildDirectory.dir("compose/tmp/main/runtime")
val createCompatibleRuntimeImage = tasks.register<Exec>("createCompatibleRuntimeImage") {
    group = "compose desktop"
    description = "Creates the minimal desktop runtime without Gradle pre-creating jlink output"
    doFirst {
        val runtimeDir = runtimeImageDir.get().asFile
        delete(runtimeDir)
        val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "jlink.exe"
        } else {
            "jlink"
        }
        val jlink = file("${System.getProperty("java.home")}/bin/$executableName")
        require(jlink.isFile) {
            "A full JDK with jlink is required to package CardVault: ${jlink.absolutePath}"
        }
        commandLine(
            jlink.absolutePath,
            // JNA discovers sun.misc.Unsafe reflectively. Keep jdk.unsupported in the
            // packaged runtime so Windows DPAPI behaves like it does under the full test JDK.
            "--add-modules", "java.base,java.desktop,java.logging,jdk.crypto.ec,jdk.unsupported",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--strip-native-commands",
            "--output", runtimeDir.absolutePath,
        )
    }
}

tasks.matching { it.name == "createRuntimeImage" }.configureEach {
    enabled = false
}

tasks.matching { task ->
    task.name.contains("Distributable", ignoreCase = true) ||
        task.name.contains("Package", ignoreCase = true)
}.configureEach {
    dependsOn(generateWindowsIcon, createCompatibleRuntimeImage)
}
