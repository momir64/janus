plugins {
    kotlin("multiplatform") version "2.4.0"
}

repositories {
    mavenCentral()
}

val hostOs: String = System.getProperty("os.name")
val isWindows = hostOs.startsWith("Windows")

layout.buildDirectory.set(layout.projectDirectory.dir("build/${if (isWindows) "mingw" else "linux"}"))

kotlin {
    val hostTarget = when {
        isWindows -> mingwX64("host")
        hostOs == "Linux" && System.getProperty("os.arch") == "aarch64" -> linuxArm64("host")
        hostOs == "Linux" -> linuxX64("host")
        else -> throw GradleException("lokot has no target for host $hostOs")
    }

    val vendorInclude = layout.projectDirectory.dir("vendor/libfido2/include").asFile.absolutePath
    val vendorLib = layout.projectDirectory.dir("vendor/libfido2/win64").asFile.absolutePath
    val multiArch = if (System.getProperty("os.arch") == "aarch64") "aarch64-linux-gnu" else "x86_64-linux-gnu"

    hostTarget.apply {
        compilations.getByName("main") {
            cinterops {
                listOf("libfido2", "crypto").forEach { library ->
                    create(library) {
                        if (isWindows) {
                            includeDirs(vendorInclude)
                            extraOpts("-libraryPath", vendorLib)
                        } else {
                            includeDirs("/usr/include", "/usr/include/$multiArch")
                            extraOpts("-libraryPath", "/usr/lib/$multiArch")
                        }
                    }
                }
            }
        }

        compilations.getByName("test").defaultSourceSet.dependencies {
            implementation(kotlin("test"))
        }

        binaries {
            executable { entryPoint = "rs.moma.janus.lokot.main" }

            all {
                linkerOpts += listOf("-lfido2", "-lcrypto")
                linkerOpts += if (isWindows) "-L$vendorLib" else "-L/usr/lib/$multiArch"
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

if (isWindows) {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
        finalizedBy(copyVendorDlls)
    }
}
