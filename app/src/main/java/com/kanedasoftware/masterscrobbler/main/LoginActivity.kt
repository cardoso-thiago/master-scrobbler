package com.kanedasoftware.masterscrobbler.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.view.View
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
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        var sessionKey = preferences.getString("sessionKey", "")

        if (sessionKey.isNullOrBlank()) {
            val params = mutableMapOf("password" to password, "username" to user)
            val sig = Utils.getSig(params, Constants.API_GET_MOBILE_SESSION)

            if (Utils.isConnected(this)) {
                doAsync {
                    val response = RetrofitInitializer(applicationContext).lastFmSecureService().getMobileSession(password, user,
                            Constants.API_KEY, sig, "auth.getMobileSession").execute()
                    if (response.isSuccessful) {
                        sessionKey = response.body()?.session?.key.toString()
                        //TODO verificar melhor maneira de armazenar a sessionkey e user
                        preferences.edit().putString("sessionKey", sessionKey).apply()
                        preferences.edit().putString("user", user).apply()
                        onLoginSuccess()
                    } else {
                        //TODO tratar todos os tipos de erro
                        Utils.logDebug("Logando o erro da obtenção do session key para tentar capturar situações: ${response.code()}", applicationContext)
                        val errorInfo = Gson().fromJson(response.errorBody()?.charStream(), ErrorInfo::class.java)
                        onLoginFailed(errorInfo.message)
                    }
                }
            } else {
                Utils.logError("Conexão necessária para obter o id da sessão do usuário.", applicationContext)
                onLoginFailed("Conexão necessária para fazer o login")
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
