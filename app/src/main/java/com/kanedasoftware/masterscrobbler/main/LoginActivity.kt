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
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import butterknife.Optional
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.model.ErrorInfo
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.RetrofitInitializer
import de.adorsys.android.securestoragelibrary.SecurePreferences
import org.jetbrains.anko.doAsync


class LoginActivity : AppCompatActivity() {

    @JvmField
    @BindView(R.id.input_login)
    var loginText: EditText? = null
    @JvmField
    @BindView(R.id.input_password)
    var passwordText: EditText? = null
    @JvmField
    @BindView(R.id.btn_login)
    var loginButton: Button? = null
    @JvmField
    @BindView(R.id.horizontal_progress_toolbar)
    var toolbarHorizontalProgress: ProgressBar? = null

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
            imm?.let { it.hideSoftInputFromWindow(v.windowToken, 0) }
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
            loginText?.error = "enter a login user"
            valid = false
        } else {
            loginText?.error = null
        }

        if (password.isEmpty()) {
            passwordText?.error = "enter the password"
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
            val sig = Utils.getSig(params, Constants.API_GET_MOBILE_SESSION)

            if (Utils.isConnected(this)) {
                doAsync {
                    val response = RetrofitInitializer(applicationContext).lastFmSecureService().getMobileSession(password, user, Constants.API_KEY, sig, "auth.getMobileSession").execute()

                    if (response.isSuccessful) {
                        val sessionKey = response.body()?.session?.key.toString()
                        SecurePreferences.setValue(applicationContext, Constants.SECURE_SESSION_TAG, sessionKey)
                        SecurePreferences.setValue(applicationContext, Constants.SECURE_USER_TAG, user)
                        onLoginSuccess()
                    } else {
                        val errorInfo = Gson().fromJson(response.errorBody()?.charStream(), ErrorInfo::class.java)
                        when (errorInfo.error) {
                            4 -> onLoginFailed("Falha na autenticação, verifique o usuário e senha do Last.fm")
                            11 -> onLoginFailed("O serviço do Last.fm está temporariamente offline, tente novamente mais tarde")
                            16 -> onLoginFailed("Erro temporário processando o login, por favor, tente novamente")
                            29 -> onLoginFailed("O seu IP fez muitas requisições em um curto espaço de tempo")
                        }
                    }
                }
            } else {
                onLoginFailed("Erro ao fazer o login, por favor, verifique a sua conexão.")
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
