package com.kanedasoftware.masterscrobbler.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import butterknife.Optional
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.models.ErrorInfo
import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import de.adorsys.android.securestoragelibrary.SecurePreferences
import org.jetbrains.anko.doAsync
import org.koin.android.ext.android.inject

class LoginActivity : CyaneaAppCompatActivity() {

    //ButterKnife
    @BindView(R.id.input_login)
    lateinit var loginText: EditText

    @BindView(R.id.input_password)
    lateinit var passwordText: EditText

    @BindView(R.id.btn_login)
    lateinit var loginButton: Button

    @BindView(R.id.horizontal_progress_toolbar)
    lateinit var toolbarHorizontalProgress: ProgressBar

    private val utils: Utils by inject()
    private val lastFmSecureService: LastFmSecureService by inject()

    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ButterKnife.bind(this)
        toolbarHorizontalProgress?.visibility = View.INVISIBLE
    }

    @Optional
    @OnClick(R.id.btn_login)
    fun onClick() {
        val view = this.currentFocus
        view?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        }
        login()
    }

    private fun login() {
        if (!validate()) {
            return
        }
        loginButton?.isEnabled = false
        toolbarHorizontalProgress?.visibility = View.VISIBLE
        getSessionKey(loginText?.text.toString(), passwordText?.text.toString())

    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun validate(): Boolean {
        var valid = true

        val login = loginText?.text.toString()
        val password = passwordText?.text.toString()

        if (login.isEmpty()) {
            loginText?.error = getString(R.string.error_username)
            valid = false
        } else {
            loginText?.error = null
        }

        if (password.isEmpty()) {
            passwordText?.error = getString(R.string.error_password)
            valid = false
        } else {
            passwordText?.error = null
        }
        return valid
    }

    private fun onLoginSuccess() {
        val runnable = Runnable {
            handler.post {
                loginButton?.isEnabled = true
            }
        }
        Thread(runnable).start()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun onLoginFailed(message: String) {

        val runnable = Runnable {
            handler.post {
                loginButton?.isEnabled = true
                toolbarHorizontalProgress?.visibility = View.INVISIBLE
            }
        }
        Thread(runnable).start()
        showErrorMessage(message)
    }

    private fun getSessionKey(user: String, password: String) {
        if (SecurePreferences.getStringValue(applicationContext, Constants.SECURE_SESSION_TAG, "").isNullOrBlank()) {
            val params = mutableMapOf("password" to password, "username" to user)
            val sig = utils.getSig(params, Constants.API_GET_MOBILE_SESSION)

            if (utils.isConnected()) {
                doAsync {
                    val response = lastFmSecureService.getMobileSession(password, user, Constants.API_KEY, sig, "auth.getMobileSession").execute()

                    if (response.isSuccessful) {
                        val sessionKey = response.body()?.session?.key.toString()
                        SecurePreferences.setValue(applicationContext, Constants.SECURE_SESSION_TAG, sessionKey)
                        SecurePreferences.setValue(applicationContext, Constants.SECURE_USER_TAG, user)
                        onLoginSuccess()
                    } else {
                        val errorInfo = Gson().fromJson(response.errorBody()?.charStream(), ErrorInfo::class.java)
                        when (errorInfo.error) {
                            4 -> onLoginFailed(getString(R.string.authentication_failed))
                            11 -> onLoginFailed(getString(R.string.service_offline))
                            16 -> onLoginFailed(getString(R.string.try_again))
                            29 -> onLoginFailed(getString(R.string.too_much_requisitions))
                        }
                    }
                }
            } else {
                onLoginFailed(getString(R.string.connection_error))
            }
        } else {
            onLoginSuccess()
        }
    }

    private fun showErrorMessage(message: String) {
        loginButton?.rootView?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }
}
