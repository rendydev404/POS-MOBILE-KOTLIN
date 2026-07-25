package com.sukashawarma.pos.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

data class SignInPayload(
    val email: String,
    val password: String
)

data class RefreshTokenPayload(
    @com.google.gson.annotations.SerializedName("refresh_token") val refreshToken: String
)

data class AuthUserDto(
    val id: String,
    val email: String?
)

data class AuthTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val token_type: String,
    val user: AuthUserDto
)

interface AuthApi {
    // Standard Supabase GoTrue password sign-in, mirrors supabase.auth.signInWithPassword() on web.
    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body payload: SignInPayload
    ): Response<AuthTokenResponse>

    // Silent re-auth using a stored refresh_token — needed when the process was
    // killed (e.g. woken up by FCM) and SessionTokenHolder's in-memory token is gone.
    @POST("auth/v1/token")
    suspend fun refreshSession(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body payload: RefreshTokenPayload
    ): Response<AuthTokenResponse>
}
