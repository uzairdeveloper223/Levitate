package dev.uzair.levitate

import androidx.lifecycle.ViewModel

/** Retain a QR credential across theme recreation, never in backups or a saved-state Bundle. */
class PairingSession : ViewModel() {
    var pairing: Pairing? = null
}
