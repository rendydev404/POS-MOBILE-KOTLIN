package com.sukashawarma.pos.presentation.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.local.AuthPrefs
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import com.sukashawarma.pos.data.remote.SignInPayload
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val LOGIN_EMAIL_DOMAIN = "@outlet.local"

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private val authApi = SupabaseClient.authApi

    val usernameInput = MutableStateFlow("")
    val passwordInput = MutableStateFlow("")

    val isLoading = MutableStateFlow(false)
    val isCheckingSession = MutableStateFlow(true)
    val errorMessage = MutableStateFlow<String?>(null)
    val activeSession = MutableStateFlow<UserSession?>(null)

    init {
        checkSession()
    }

    private fun checkSession() {
        val lastUser = AuthPrefs.getLastUsername()
        val lastPass = AuthPrefs.getLastPassword()
        if (lastUser != null) usernameInput.value = lastUser
        if (lastPass != null) passwordInput.value = lastPass

        val refreshToken = AuthPrefs.getRefreshToken()
        if (refreshToken == null) {
            isCheckingSession.value = false
            return
        }

        viewModelScope.launch {
            try {
                val authRes = authApi.refreshSession(payload = com.sukashawarma.pos.data.remote.RefreshTokenPayload(refreshToken))
                if (!authRes.isSuccessful || authRes.body() == null) {
                    if (authRes.code() in 400..499) {
                        SessionTokenHolder.clear()
                        AuthPrefs.clear()
                        isCheckingSession.value = false
                        return@launch
                    } else {
                        restoreOfflineSession()
                        return@launch
                    }
                }
                val token = authRes.body()!!

                SessionTokenHolder.accessToken = token.access_token
                SessionTokenHolder.refreshToken = token.refresh_token
                AuthPrefs.setRefreshToken(token.refresh_token)

                val staffRes = api.getStaffById("eq.${token.user.id}")
                val staff = staffRes.body()?.firstOrNull()
                
                if (staff == null || staff.isActive == false || staff.outletId.isNullOrBlank()) {
                    SessionTokenHolder.clear()
                    isCheckingSession.value = false
                    return@launch
                }

                val outletRes = api.getOutletById("eq.${staff.outletId}")
                val outlet = outletRes.body()?.firstOrNull()

                val session = UserSession(
                    staffId = staff.id,
                    username = staff.name ?: staff.username ?: "Staff",
                    role = staff.role ?: "kasir",
                    outletId = staff.outletId,
                    outletName = outlet?.name ?: "Unknown Outlet"
                )
                
                SessionPrefs.setSession(staff.id, staff.outletId, session.outletName, session.username, session.role)
                activeSession.value = session
                isCheckingSession.value = false
            } catch (e: Exception) {
                // Network error, restore offline session
                restoreOfflineSession()
            }
        }
    }
    
    private fun restoreOfflineSession() {
        val staffId = SessionPrefs.getStaffId()
        val outletId = SessionPrefs.getOutletId()
        val outletName = SessionPrefs.getOutletName() ?: "Offline Outlet"
        val username = SessionPrefs.getUsername() ?: "Staff"
        val role = SessionPrefs.getRole() ?: "kasir"
        
        if (staffId != null && outletId != null) {
            activeSession.value = UserSession(staffId, username, role, outletId, outletName)
        }
        isCheckingSession.value = false
    }

    fun login() {
        val user = usernameInput.value.trim()
        val pass = passwordInput.value

        if (user.isBlank() || pass.isBlank()) {
            errorMessage.value = "Username dan Password harus diisi"
            return
        }

        isLoading.value = true
        errorMessage.value = null

        val email = if (user.contains("@")) user else ""

        viewModelScope.launch {
            try {
                val authRes = authApi.signInWithPassword(payload = SignInPayload(email, pass))
                if (!authRes.isSuccessful || authRes.body() == null) {
                    errorMessage.value = "Login gagal: Kredensial tidak valid"
                    return@launch
                }
                
                val token = authRes.body()!!
                SessionTokenHolder.accessToken = token.access_token
                SessionTokenHolder.refreshToken = token.refresh_token
                
                AuthPrefs.setRefreshToken(token.refresh_token)

                val staffRes = api.getStaffById("eq.${token.user.id}")
                val staff = staffRes.body()?.firstOrNull()
                if (staff == null || staff.isActive == false || staff.outletId.isNullOrBlank()) {
                    errorMessage.value = "Akun staff tidak aktif atau tidak terkait ke outlet manapun"
                    return@launch
                }

                val outletRes = api.getOutletById("eq.${staff.outletId}")
                val outlet = outletRes.body()?.firstOrNull()

                val session = UserSession(
                    staffId = staff.id,
                    username = staff.name ?: staff.username ?: "Staff",
                    role = staff.role ?: "kasir",
                    outletId = staff.outletId,
                    outletName = outlet?.name ?: "Unknown Outlet"
                )

                AuthPrefs.setLastCredentials(user, pass)
                SessionPrefs.setSession(staff.id, staff.outletId, session.outletName, session.username, session.role)
                activeSession.value = session

            } catch (e: HttpException) {
                errorMessage.value = "Network error: ${e.message()}"
            } catch (e: Exception) {
                errorMessage.value = "Terjadi kesalahan: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
    
    fun logout() {
        activeSession.value = null
        SessionTokenHolder.clear()
        AuthPrefs.clear()
        SessionPrefs.clear()
        
        // After logout, keep the last credentials filled in, but they must manually click login.
        val lastUser = AuthPrefs.getLastUsername()
        val lastPass = AuthPrefs.getLastPassword()
        if (lastUser != null) usernameInput.value = lastUser
        if (lastPass != null) passwordInput.value = lastPass
    }
}
