# Overview

This module contains example of implementation [Keyri](https://keyri.com) with Okta Authentication.

## Contents

* [Requirements](#Requirements)
* [Permissions](#Permissions)
* [Keyri Integration](#Keyri-Integration)
* [Okta Authentication Integration](#Okta-Authentication-Integration)
* [Authentication](#Authentication)

## Requirements

* Android API level 23 or higher
* AndroidX compatibility
* Kotlin coroutines compatibility

Note: Your app does not have to be written in kotlin to integrate this SDK, but must be able to
depend on kotlin functionality.

## Permissions

Open your app's `AndroidManifest.xml` file and add the following permission:

```xml

<uses-permission android:name="android.permission.INTERNET" />
```

## Keyri Integration

* Add the JitPack repository to your root build.gradle file:

```groovy
allprojects {
    repositories {
        // ...
        maven { url "https://jitpack.io" }
    }
}
```

* Add SDK dependency to your build.gradle file and sync project:

```kotlin
dependencies {
    // ...
    implementation("com.github.Keyri-Co:keyri-android-whitelabel-sdk:$latestKeyriVersion")
}
```

## Okta Authentication Integration

Check [Sign users in to your mobile app using the redirect model](https://developer.okta.com/docs/guides/sign-into-mobile-app-redirect/android/main/)
article to integrate Okta Android SDK into your app.

## Authentication

* Here is example of Firebase Authentication with google.com OAuthProvider:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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

    bOktaAuth.setOnClickListener {
        client.signIn(this@MainActivity, null)
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

// Create payload
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

    val keyri = Keyri()

    val signingData = JSONObject().apply {
        put("timestamp", System.currentTimeMillis()) // Optional
        put("email", email) // Optional
        put("uid", tokenResult.uid) // Optional
    }.toString()

    val signature = keyri.getUserSignature(email, signingData)

    val payload = JSONObject().apply {
        put("data", data)
        put("signingData", signingData)
        put("userSignature", signature) // Optional
        put("associationKey", keyri.getAssociationKey(email)) // Optional
    }.toString()

    // Public user ID (email) is optional
    keyriAuth(email, payload)
}
```

* Authenticate with Keyri. In the next showing `AuthWithScannerActivity` with providing
  `publicUserId` and `payload`.

```kotlin
private val easyKeyriAuthLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Process authentication result
    }

private fun keyriAuth(publicUserId: String?, payload: String) {
    val intent = Intent(this, AuthWithScannerActivity::class.java).apply {
        putExtra(AuthWithScannerActivity.APP_KEY, BuildConfig.APP_KEY)
        putExtra(AuthWithScannerActivity.PUBLIC_USER_ID, publicUserId)
        putExtra(AuthWithScannerActivity.PAYLOAD, payload)
    }

    easyKeyriAuthLauncher.launch(intent)
}
```
