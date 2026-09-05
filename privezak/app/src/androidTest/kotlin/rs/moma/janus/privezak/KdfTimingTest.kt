package rs.moma.janus.privezak

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.system.measureTimeMillis
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.runner.RunWith
import android.os.Bundle
import android.util.Log
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class KdfTimingTest {
    private fun derive(algorithm: String, iterations: Int): Double {
        val salt = ByteArray(16) { it.toByte() }
        val factory = SecretKeyFactory.getInstance(algorithm)
        // One throwaway run so provider init and JIT are not counted.
        factory.generateSecret(PBEKeySpec("warmup".toCharArray(), salt, 10_000, 256))
        return (1..5).map {
            val time = measureTimeMillis {
                factory.generateSecret(PBEKeySpec("123456".toCharArray(), salt, iterations, 256))
            }
            Log.i("KDFBENCH", "$algorithm run $it: ${time}ms")
            time
        }.average()
    }

    @Test
    fun compareDerivationCost() {
        val sha256 = derive("PBKDF2WithHmacSHA256", 600_000)
        val sha512 = derive("PBKDF2WithHmacSHA512", 220_000)
        val report = "SHA256@600k=${sha256}ms SHA512@220k=${sha512}ms"
        Log.i("KDFBENCH", report)
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(0, Bundle().apply { putString("stream", report) })
    }
}