package rs.moma.janus.privezak.ui.utils

import android.content.Context
import android.widget.Toast
import android.os.Handler
import android.os.Looper

object SingleToast {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    fun show(context: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() == Looper.getMainLooper())
            showInternal(context, text, duration)
        else
            mainHandler.post { showInternal(context, text, duration) }
    }

    private fun showInternal(context: Context, text: CharSequence, duration: Int) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context.applicationContext, text, duration)
        currentToast?.show()
    }
}
