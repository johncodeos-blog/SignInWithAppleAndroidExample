package com.example.signinwithappleexample

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {

    lateinit var appleAuthURLFull: String
    lateinit var appledialog: Dialog
    lateinit var appleAuthCode: String
    lateinit var appleClientSecret: String

    var appleId = ""
    var appleFirstName = ""
    var appleMiddleName = ""
    var appleLastName = ""
    var appleEmail = ""
    var appleAccesToken = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val state = UUID.randomUUID().toString()

        appleAuthURLFull =
            AppleConstants.AUTHURL + "?response_type=code&v=1.1.6&response_mode=form_post&client_id=" + AppleConstants.CLIENT_ID + "&scope=" + AppleConstants.SCOPE + "&state=" + state + "&redirect_uri=" + AppleConstants.REDIRECT_URI

        apple_login_btn.setOnClickListener {
            setupAppleWebviewDialog(appleAuthURLFull)
        }

    }

    // Show 'Sign in with Apple' login page in a dialog
    @SuppressLint("SetJavaScriptEnabled")
    fun setupAppleWebviewDialog(url: String) {
        appledialog = Dialog(this)
        val webView = WebView(this)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.webViewClient = AppleWebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(url)
        appledialog.setContentView(webView)
        appledialog.show()
    }

    // A client to know about WebView navigations
    // For API 21 and above
    @Suppress("OverridingDeprecatedMember")
    inner class AppleWebViewClient : WebViewClient() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            if (request!!.url.toString().startsWith(AppleConstants.REDIRECT_URI)) {
                handleUrl(request.url.toString())

                // Close the dialog after getting the authorization code
                if (request.url.toString().contains("success=")) {
                    appledialog.dismiss()
                }
                return true
            }
            return true
        }

        // For API 19 and below
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith(AppleConstants.REDIRECT_URI)) {
                handleUrl(url)
                // Close the dialog after getting the authorization code
                if (url.contains("success=")) {
                    appledialog.dismiss()
                }
                return true
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // retrieve display dimensions
            val displayRectangle = Rect()
            val window = this@MainActivity.window
            window.decorView.getWindowVisibleDisplayFrame(displayRectangle)

            // Set height of the Dialog to 90% of the screen
            val layoutparms = view?.layoutParams
            layoutparms?.height = (displayRectangle.height() * 0.9f).toInt()
            view?.layoutParams = layoutparms
        }

        // Check webview url for access token code or error
        @SuppressLint("LongLogTag")
        private fun handleUrl(url: String) {
            val uri =
                Uri.parse(url)

            val success = uri.getQueryParameter("success")
            if (success == "true") {

                // Get the Authorization Code from the URL
                appleAuthCode = uri.getQueryParameter("code") ?: ""
                Log.i("Apple Code: ", appleAuthCode)

                // Get the Client Secret from the URL
                appleClientSecret = uri.getQueryParameter("client_secret") ?: ""
                Log.i("Apple Client Secret: ", appleClientSecret ?: "")

                //Check if user gave access to the app for the first time by checking if the url contains their email
                if (url.contains("email")) {
                    //Get user's First Name
                    val firstName = uri.getQueryParameter("first_name")
                    Log.i("Apple User First Name: ", firstName ?: "")
                    appleFirstName = firstName ?: ""

                    //Get user's Middle Name
                    val middleName = uri.getQueryParameter("middle_name")
                    Log.i("Apple User Middle Name: ", middleName ?: "")
                    appleMiddleName = middleName ?: ""

                    //Get user's Last Name
                    val lastName = uri.getQueryParameter("last_name")
                    Log.i("Apple User Last Name: ", lastName ?: "")
                    appleLastName = lastName ?: ""

                    //Get user's email
                    val email = uri.getQueryParameter("email")
                    Log.i("Apple User Email: ", email ?: "")
                    appleEmail = email ?: ""
                }

                // Exchange the Auth Code for Access Token
                AppleRequestForAccessToken(
                    this@MainActivity,
                    appleAuthCode,
                    appleClientSecret
                ).execute()
            } else if (success == "false") {
                Log.e("Error", "We couldn't get the Auth Code")
            }
        }
    }

    private class AppleRequestForAccessToken
    internal constructor(context: MainActivity, authCode: String, clientSecret: String) :
        AsyncTask<Void, Void, Void>() {

        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        var code = ""
        var clientsecret = ""
        val grantType = "authorization_code"

        init {
            this.code = authCode
            this.clientsecret = clientSecret
        }

        val postParamsForAuth =
            "grant_type=" + grantType + "&code=" + code + "&redirect_uri=" + AppleConstants.REDIRECT_URI + "&client_id=" + AppleConstants.CLIENT_ID + "&client_secret=" + clientsecret

        //val postParamsForRefreshToken = "grant_type=" + grantType + "&client_id=" + AppleConstants.CLIENT_ID + "&client_secret=" + clientsecret + "&refresh_token" + "REFRESH_TOKEN_FROM_THE_AUTH"

        override fun doInBackground(vararg params: Void): Void? {
            try {
                val url = URL(AppleConstants.TOKENURL)
                val httpsURLConnection = url.openConnection() as HttpsURLConnection
                httpsURLConnection.requestMethod = "POST"
                httpsURLConnection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                httpsURLConnection.doInput = true
                httpsURLConnection.doOutput = true
                val outputStreamWriter = OutputStreamWriter(httpsURLConnection.outputStream)
                outputStreamWriter.write(postParamsForAuth)
                outputStreamWriter.flush()
                val response = httpsURLConnection.inputStream.bufferedReader()
                    .use { it.readText() }  // defaults to UTF-8
                val jsonObject = JSONTokener(response).nextValue() as JSONObject

                val activity = activityReference.get()

                val accessToken = jsonObject.getString("access_token") //Here is the access token
                Log.i("Apple Access Token is: ", accessToken)
                activity?.appleAccesToken = accessToken

                val expiresIn = jsonObject.getInt("expires_in") //When the access token expires
                Log.i("expires in: ", expiresIn.toString())

                val refreshToken =
                    jsonObject.getString("refresh_token") // The refresh token used to regenerate new access tokens. Store this token securely on your server.
                Log.i("refresh token: ", refreshToken)

                val idToken =
                    jsonObject.getString("id_token") // A JSON Web Token that contains the user’s identity information.
                Log.i("ID Token: ", idToken)

                // Get encoded user id by spliting idToken and taking the 2nd piece
                val encodedUserID = idToken.split(".")[1]

                //Decode encodedUserID to JSON
                val decodedUserData = String(Base64.decode(encodedUserID, Base64.DEFAULT))
                val userDataJsonObject = JSONObject(decodedUserData)
                // Get User's ID
                val userId = userDataJsonObject.getString("iat")
                Log.i("Apple User ID :", userId)
                activity?.appleId = userId
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            // get a reference to the activity if it is still there
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing) return
            val myIntent = Intent(activity, DetailsActivity::class.java)
            myIntent.putExtra("apple_id", activity.appleId)
            myIntent.putExtra("apple_first_name", activity.appleFirstName)
            myIntent.putExtra("apple_middle_name", activity.appleMiddleName)
            myIntent.putExtra("apple_last_name", activity.appleLastName)
            myIntent.putExtra("apple_email", activity.appleEmail)
            myIntent.putExtra("apple_access_token", activity.appleAccesToken)
            activity.startActivity(myIntent)
        }
    }
}
