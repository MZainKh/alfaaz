package io.github.mzainkh.alfaaz.car

import android.annotation.SuppressLint
import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import io.github.mzainkh.alfaaz.BuildConfig

class LyricsCarAppService : CarAppService() {
    // The library's sample allowlist is the documented list of Google's Android Auto hosts.
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = LyricsSession()
}

private class LyricsSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = LyricsScreen(carContext)
}
