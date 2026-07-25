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

// Same pseudo-email convention the web portal uses to sign staff into Supabase Auth
// with a plain username (see packages/auth/src/access.ts: normalizeLoginIdentifier).
private const val LOGIN_EMAIL_DOMAIN = "@outlet.local"

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private val authApi = SupabaseClient.authApi

    val usernameInput = MutableStateFlow("")
    val passwordInput = MutableStateFlow("")

    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val activeSession = MutableStateFlow<UserSession?>(null)

    fun login(onSuccess: (UserSession) -> Unit) {
        val username = usernameInput.value.trim()
        val password = passwordInput.value

        if (username.isBlank() || password.isBlank()) {
            errorMessage.value = "Username dan password wajib diisi."
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                val email = if (username.contains("@")) username else "$username$LOGIN_EMAIL_DOMAIN"

                // Real Supabase Auth sign-in — identical mechanism to the web portal.
                val authRes = authApi.signInWithPassword(payload = SignInPayload(email, password))
                if (!authRes.isSuccessful || authRes.body() == null) {
                    errorMessage.value = "Login gagal: username atau password salah."
                    return@launch
                }
                val token = authRes.body()!!

                // Carry the user's access token for every request from here on so
                // RLS (auth.uid()-based policies) scopes data to this staff member.
                SessionTokenHolder.accessToken = token.access_token
                SessionTokenHolder.refreshToken = token.refresh_token
                AuthPrefs.setRefreshToken(token.refresh_token)

                val staffRes = api.getStaffById("eq.${token.user.id}")
                val staff = staffRes.body()?.firstOrNull()
                if (!staffRes.isSuccessful || staff == null) {
                    errorMessage.value = "Login gagal: akun ini tidak terdaftar sebagai staff outlet."
                    SessionTokenHolder.clear()
                    return@launch
                }
                if (staff.isActive == false) {
                    errorMessage.value = "Akun '$username' sedang dinonaktifkan. Hubungi admin."
                    SessionTokenHolder.clear()
                    return@launch
                }
                if (staff.outletId.isNullOrBlank()) {
                    errorMessage.value = "Akun ini tidak terikat ke outlet manapun."
                    SessionTokenHolder.clear()
                    return@launch
                }

                val outletRes = api.getOutletById("eq.${staff.outletId}")
                val outlet = outletRes.body()?.firstOrNull()

                val session = UserSession(
                    staffId = staff.id,
                    username = staff.name ?: staff.username ?: username,
                    role = staff.role ?: "crew",
                    outletId = staff.outletId,
                    outletName = outlet?.name ?: "Outlet"
                )

                SessionPrefs.setSession(staffId = staff.id, outletId = staff.outletId)
                activeSession.value = session
                onSuccess(session)

                com.sukashawarma.pos.data.notification.FcmTokenRegistrar.registerCurrentToken(staff.id, staff.outletId)
            } catch (e: HttpException) {
                errorMessage.value = "Login gagal: username atau password salah."
                SessionTokenHolder.clear()
            } catch (e: Exception) {
                errorMessage.value = "Koneksi ke server gagal: ${e.localizedMessage}"
                SessionTokenHolder.clear()
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
    }
}
