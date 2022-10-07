package com.keyri.exampleokta

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.keyri.exampleokta.databinding.ActivityMainBinding
import com.keyrico.keyrisdk.Keyri
import com.keyrico.scanner.easyKeyriAuth
import com.okta.oidc.AuthorizationStatus
import com.okta.oidc.RequestCallback
import com.okta.oidc.ResultCallback
import com.okta.oidc.Tokens
import com.okta.oidc.clients.sessions.SessionClient
import com.okta.oidc.clients.web.WebAuthClient
import com.okta.oidc.net.params.TokenTypeHint
import com.okta.oidc.net.response.IntrospectInfo
import com.okta.oidc.net.response.UserInfo
import com.okta.oidc.util.AuthorizationException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val client: WebAuthClient by lazy { AuthClient.createClient(this) }
    private val sessionClient: SessionClient by lazy { client.sessionClient }

    private val easyKeyriAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val text = if (it.resultCode == RESULT_OK) "Authenticated" else "Failed to authenticate"

            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        registerClientCallback()

        with(binding) {
            bOktaAuth.setOnClickListener {
                client.signIn(this@MainActivity, null)
            }

            bLogout.setOnClickListener {
                client.signOutOfOkta(this@MainActivity)
            }
        }

        checkIsAuthenticated()
    }

    private fun registerClientCallback() {
        client.registerCallback(object :
            ResultCallback<AuthorizationStatus, AuthorizationException> {
            override fun onSuccess(status: AuthorizationStatus) {
                if (status == AuthorizationStatus.AUTHORIZED) {
                    Log.i(TAG, "Auth success")

                    runOnUiThread {
                        binding.bOktaAuth.visibility = View.GONE
                        binding.bLogout.visibility = View.VISIBLE

                        showUserInfo()
                    }
                } else if (status == AuthorizationStatus.SIGNED_OUT) {
                    Log.i(TAG, "Sign out success")

                    runOnUiThread {
                        binding.bOktaAuth.visibility = View.VISIBLE
                        binding.bLogout.visibility = View.GONE
                    }
                }
            }

            override fun onCancel() {
                Log.i(TAG, "Auth cancelled")
            }

            override fun onError(msg: String?, exception: AuthorizationException?) {
                Log.e(TAG, msg, exception)
            }
        }, this)
    }

    private fun checkIsAuthenticated() {
        if (sessionClient.isAuthenticated) {
            Log.i(TAG, "User is already authenticated")
            binding.bOktaAuth.visibility = View.GONE
            binding.bLogout.visibility = View.VISIBLE

            showUserInfo()

            try {
                if (sessionClient.tokens.isAccessTokenExpired) {
                    sessionClient.refreshToken(object :
                        RequestCallback<Tokens?, AuthorizationException> {
                        override fun onSuccess(result: Tokens) {
                            Log.i(TAG, "Token refreshed successfully")
                        }

                        override fun onError(msg: String, error: AuthorizationException) {
                            Log.e(TAG, msg, error)
                        }
                    })
                }
            } catch (error: AuthorizationException) {
                Log.e(TAG, error.message, error)
            }
        } else {
            sessionClient.clear()
        }
    }

    private fun showUserInfo() {
        sessionClient.getUserProfile(object : RequestCallback<UserInfo, AuthorizationException> {
            override fun onSuccess(result: UserInfo) {
                val username = result["name"] as String
                val email = result["preferred_username"] as String

                introspectToken(username, email)

                runOnUiThread {
                    binding.tvHaveAccount.text =
                        String.format(
                            "%s %s",
                            getString(R.string.welcome_user),
                            "$username, $email"
                        )
                }
            }

            override fun onError(msg: String, error: AuthorizationException) {
                Log.e(TAG, msg, error)
            }
        })
    }

    private fun introspectToken(username: String, email: String) {
        sessionClient.introspectToken(
            sessionClient.tokens.refreshToken,
            TokenTypeHint.ACCESS_TOKEN,
            object : RequestCallback<IntrospectInfo, AuthorizationException> {
                override fun onSuccess(result: IntrospectInfo) {
                    createPayload(sessionClient.tokens, result, username, email)
                }

                override fun onError(error: String?, exception: AuthorizationException?) {
                    Log.e(TAG, error, exception)
                }
            }
        )
    }

    private fun createPayload(
        tokens: Tokens,
        tokenResult: IntrospectInfo,
        username: String,
        email: String
    ) {
        val tokensData = JSONObject().apply {
            put("accessToken", tokens.accessToken)
            put("idToken", tokens.idToken)
            put("refreshToken", tokens.refreshToken)
            put("isAccessTokenExpired", tokens.isAccessTokenExpired)
            put("expiresIn", tokens.expiresIn)
        }

        val tokenResultData = JSONObject().apply {
            put("aud", tokenResult.aud)
            put("tokenType", tokenResult.tokenType)
            put("clientId", tokenResult.clientId)
            put("exp", tokenResult.exp)
            put("iat", tokenResult.iat)
            put("deviceId", tokenResult.deviceId)
            put("isActive", tokenResult.isActive)
            put("iss", tokenResult.iss)
            put("jti", tokenResult.jti)
            put("nbf", tokenResult.nbf)
            put("sub", tokenResult.sub)
            put("uid", tokenResult.uid)
            put("username", tokenResult.username)
        }

        val userData = JSONObject().apply {
            put("username", username)
            put("email", email)
        }

        val data = JSONObject().apply {
            put("user", userData)
            put("tokens", tokensData)
            put("tokenResult", tokenResultData)
        }

        val signingData = JSONObject().apply {
            put("timestamp", System.currentTimeMillis()) // Optional
            put("email", email) // Optional
            put("uid", tokenResult.uid) // Optional
        }.toString()

        val keyri = Keyri(this)

        val signature = keyri.generateUserSignature(email, signingData)

        val payload = JSONObject().apply {
            put("data", data)
            put("signingData", signingData)
            put("userSignature", signature) // Optional
            put("associationKey", keyri.getAssociationKey(email)) // Optional
        }.toString()

        // Public user ID (email) is optional
        keyriAuth(email, payload)
    }

    private fun keyriAuth(publicUserId: String?, payload: String) {
        easyKeyriAuth(
            this,
            easyKeyriAuthLauncher,
            "SQzJ5JLT4sEE1zWk1EJE1ZGNfwpvnaMP",
            payload,
            publicUserId
        )
    }

    companion object {
        private const val TAG = "Keyri Okta example"
    }
}
