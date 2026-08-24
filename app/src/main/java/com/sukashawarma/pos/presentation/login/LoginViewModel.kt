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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val LOGIN_EMAIL_DOMAIN = "@outlet.local"
internal const val AUTO_LOGIN_TIMEOUT_MS = 1_000L

/** Shared deadline for the complete silent-session flow, not each HTTP request. */
internal suspend fun <T> runAutoLoginWithinDeadline(block: suspend CoroutineScope.() -> T): T =
    withTimeout(AUTO_LOGIN_TIMEOUT_MS, block)

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
                // Jangan mengunci kasir pada layar loader ketika DNS/Wi-Fi/server
                // lambat. `withTimeout` membatalkan semua request Retrofit di
                // dalam blok ini sehingga hasil yang datang terlambat tidak dapat
                // menimpa login manual yang sudah dimulai kasir.
                runAutoLoginWithinDeadline {
                    val authRes = authApi.refreshSession(payload = com.sukashawarma.pos.data.remote.RefreshTokenPayload(refreshToken))
                    if (!authRes.isSuccessful || authRes.body() == null) {
                        if (authRes.code() in 400..499) {
                            SessionTokenHolder.clear()
                            AuthPrefs.clear()
                            isCheckingSession.value = false
                            return@runAutoLoginWithinDeadline
                        } else {
                            restoreOfflineSession()
                            return@runAutoLoginWithinDeadline
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
                        return@runAutoLoginWithinDeadline
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
                }
            } catch (e: TimeoutCancellationException) {
                // Timeout auto-login bukan kegagalan login manual. Buka form agar
                // kasir dapat langsung mencoba kembali tanpa menunggu jaringan.
                errorMessage.value = "Sesi otomatis tidak dapat dimuat. Silakan login."
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
            errorMessage.value = "Mohon lengkapi Email dan Password Anda."
            return
        }

        isLoading.value = true
        errorMessage.value = null

        val email = if (user.contains("@")) user else "$user$LOGIN_EMAIL_DOMAIN"

        viewModelScope.launch {
            try {
                val authRes = authApi.signInWithPassword(payload = SignInPayload(email, pass))
                if (!authRes.isSuccessful || authRes.body() == null) {
                    val errorBody = authRes.errorBody()?.string() ?: ""
                    if (errorBody.contains("Invalid login credentials", ignoreCase = true)) {
                        errorMessage.value = "Email atau Password yang Anda masukkan salah. Coba periksa kembali."
                    } else if (errorBody.contains("Email not confirmed", ignoreCase = true)) {
                        errorMessage.value = "Email Anda belum dikonfirmasi. Silakan periksa inbox email Anda."
                    } else {
                        errorMessage.value = "Login gagal. Silakan periksa kembali data Anda."
                    }
                    return@launch
                }
                
                val token = authRes.body()!!
                SessionTokenHolder.accessToken = token.access_token
                SessionTokenHolder.refreshToken = token.refresh_token
                
                AuthPrefs.setRefreshToken(token.refresh_token)

                val staffRes = api.getStaffById("eq.${token.user.id}")
                val staff = staffRes.body()?.firstOrNull()
                if (staff == null || staff.isActive == false || staff.outletId.isNullOrBlank()) {
                    errorMessage.value = "Akun Anda saat ini tidak aktif atau belum terdaftar di outlet. Hubungi Manajer Anda."
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

            } catch (e: java.io.IOException) {
                // Semua fungsi API di app ini mengembalikan Response<T> (isSuccessful
                // dicek manual di atas), jadi Retrofit TIDAK PERNAH melempar
                // HttpException untuk error HTTP — cabang itu kode mati. Kegagalan
                // jaringan sungguhan (WiFi putus, timeout, DNS gagal) selalu berupa
                // IOException, dan sebelum ini jatuh ke catch generik di bawah,
                // membuat masalah WiFi biasa tampil sebagai "Terjadi masalah sistem"
                // yang menakutkan alih-alih pesan yang actionable.
                errorMessage.value = "Koneksi internet terputus atau server tidak merespons. Periksa koneksi Anda."
            } catch (e: Exception) {
                errorMessage.value = "Terjadi masalah sistem. Silakan coba beberapa saat lagi."
            } finally {
                isLoading.value = false
            }
        }
    }
    
    fun logout() {
        activeSession.value = null
        viewModelScope.launch {
            // Harus sebelum token sesi dibersihkan: policy DELETE memakai auth.uid().
            com.sukashawarma.pos.data.notification.FcmTokenRegistrar.unregisterCurrentToken()
            SessionTokenHolder.clear()
            AuthPrefs.clear()
            SessionPrefs.clear()

            // Biarkan kredensial terakhir tetap terisi, tetapi login harus manual.
            AuthPrefs.getLastUsername()?.let { usernameInput.value = it }
            AuthPrefs.getLastPassword()?.let { passwordInput.value = it }
        }
    }
}
