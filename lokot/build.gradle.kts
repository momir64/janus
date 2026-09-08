plugins {
    kotlin("multiplatform") version "2.4.0"
}

group = "rs.moma.janus"
version = "0.0.1"

repositories {
    mavenCentral()
}

val hostOs: String = System.getProperty("os.name")
val isWindows = hostOs.startsWith("Windows")
val hostMachine = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "aarch64" else "x86_64"
val machine = (findProperty("lokot.arch") as String? ?: hostMachine).also {
    if (it !in setOf("x86_64", "aarch64")) throw GradleException("unknown lokot.arch '$it'")
}
val crossing = machine != hostMachine
if (crossing && isWindows) throw GradleException("cross-compiling for $machine needs a Linux x86_64 host")
val fidoInclude: String = layout.projectDirectory.dir("vendor/libfido2/include").asFile.absolutePath
val libresslInclude: String = layout.projectDirectory.dir("vendor/libressl/include").asFile.absolutePath
val multiArch = "$machine-linux-gnu"
val fidoLib = layout.projectDirectory.dir(
    if (isWindows) "vendor/libfido2/win64" else "vendor/libfido2/linux-$machine"
).asFile

val sysroot: String =
    if (crossing) layout.projectDirectory.dir("vendor/downloads/sysroot-$machine").asFile.absolutePath else ""
layout.buildDirectory.set(layout.projectDirectory.dir("build/${if (isWindows) "mingw" else "linux-$machine"}"))

kotlin {
    jvmToolchain(21)
    jvm {
        compilations.getByName("main").compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
        }
    }
    sourceSets.getByName("jvmTest").dependencies { implementation(kotlin("test")) }

    val hostTarget = when {
        isWindows -> mingwX64("host")
        hostOs != "Linux" -> throw GradleException("lokot has no target for host $hostOs")
        machine == "aarch64" -> linuxArm64("host")
        else -> linuxX64("host")
    }

    hostTarget.apply {
        compilations.getByName("main") {
            cinterops {
                listOf("libfido2", "crypto").forEach { library ->
                    create(library) {
                        if (isWindows) {
                            includeDirs(fidoInclude, libresslInclude)
                            extraOpts("-libraryPath", fidoLib.absolutePath)
                        } else {
                            includeDirs(fidoInclude, "$sysroot/usr/include", "$sysroot/usr/include/$multiArch")
                            extraOpts(
                                "-libraryPath", fidoLib.absolutePath,
                                "-libraryPath", "$sysroot/usr/lib/$multiArch",
                            )
                        }
                    }
                }
            }
        }

        compilations.getByName("main").defaultSourceSet.kotlin.srcDir(
            if (isWindows) "src/windowsMain/kotlin" else "src/posixMain/kotlin"
        )

        compilations.getByName("test").defaultSourceSet.dependencies {
            implementation(kotlin("test"))
        }

        binaries {
            executable { entryPoint = "rs.moma.janus.lokot.main" }

            all {
                linkerOpts += "-L${fidoLib.absolutePath}"
                linkerOpts += if (isWindows) listOf("-lfido2", "-lcrypto") else listOf(
                    "-L$sysroot/usr/lib/$multiArch", "-lfido2", "-lcbor", "-lcrypto",
                    "-ludev", "-lz", "-Wl,--allow-shlib-undefined",
                )
            }
        }
    }
}

val copyVendorDlls = tasks.register("copyVendorDlls") {
    group = "build"
    description = "Places libfido2's runtime DLLs beside each executable so it can start."
    val source = layout.projectDirectory.dir("vendor/libfido2/win64")
    val targets = listOf("debugExecutable", "releaseExecutable", "debugTest", "releaseTest")
        .map { layout.buildDirectory.dir("bin/host/$it") }

    doLast {
        targets.map { it.get().asFile }.filter { it.isDirectory }.forEach { target ->
            source.asFile.listFiles { file -> file.name.endsWith(".dll") }
                ?.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    if (isWindows) finalizedBy(copyVendorDlls) else doFirst { checkLinuxVendorLibraries() }
}

// A cross-build cannot run its own tests, so there is nothing to link them for.
if (crossing) tasks.matching { it.name.contains("Test") }.configureEach { enabled = false }

fun checkLinuxVendorLibraries() {
    val forArch = if (crossing) "LOKOT_ARCH=$machine " else ""
    val archive = fidoLib.resolve("libfido2.a")
    if (!archive.isFile) throw GradleException("$archive is missing - run ${forArch}./vendor/build-libfido2.sh")
    val missing = mapOf(
        "libcbor.so" to "libcbor-dev", "libudev.so" to "libudev-dev",
        "libcrypto.so" to "libssl-dev", "libz.so" to "zlib1g-dev",
    ).filterKeys { !File("$sysroot/usr/lib/$multiArch/$it").exists() }
    if (missing.isEmpty()) return
    throw GradleException(
        if (crossing) "$sysroot is missing ${missing.keys.sorted()} - copy them from the $machine target"
        else "install ${missing.values.sorted().joinToString(" ")} (missing ${missing.keys.sorted()})"
    )
}
