package com.fahadh.notifyme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fahadh.notifyme.model.DismissMessage

/** Fired by the mirror's delete intent when the user swipes/clears it. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(MirrorNotifier.EXTRA_DISMISS_KEY) ?: return
        MirrorConnection.send(DismissMessage(id = key))
    }
}
