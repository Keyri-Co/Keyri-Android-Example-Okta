package com.keyri.exampleokta

import android.content.Context
import android.graphics.Color
import com.okta.oidc.OIDCConfig
import com.okta.oidc.Okta.WebAuthBuilder
import com.okta.oidc.clients.web.WebAuthClient
import com.okta.oidc.storage.SharedPreferenceStorage
import java.util.concurrent.Executors

class AuthClient(context: Context) {

    @Volatile
    var webAuthClient: WebAuthClient
        private set

    init {
        val config = OIDCConfig.Builder()
            .withJsonFile(context, R.raw.okta_oidc_config)
            .create()

        webAuthClient = WebAuthBuilder()
            .withConfig(config)
            .withContext(context)
            .withStorage(SharedPreferenceStorage(context))
            .withCallbackExecutor(Executors.newSingleThreadExecutor())
            .withTabColor(Color.BLUE)
            .supportedBrowsers(
                CHROME_BROWSER,
                FIRE_FOX
            ) //.setRequireHardwareBackedKeyStore(false) // required for emulators
            .create()
    }

    companion object {
        private const val FIRE_FOX = "org.mozilla.firefox"
        private const val CHROME_BROWSER = "com.android.chrome"
    }
}
