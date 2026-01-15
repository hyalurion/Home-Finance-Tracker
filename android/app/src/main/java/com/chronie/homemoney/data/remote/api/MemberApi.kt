package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.ApiResponse
import com.chronie.homemoney.data.remote.dto.HealthDto
import com.chronie.homemoney.data.remote.dto.MemberDto
import com.chronie.homemoney.data.remote.dto.MemberRequest
import com.chronie.homemoney.data.remote.dto.SubscriptionPlanDto
import com.chronie.homemoney.data.remote.dto.UserSubscriptionDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface MemberApi {
    @GET("api/health/lite")
    suspend fun checkHealth(): HealthDto
    @POST("api/members/members")
    suspend fun getOrCreateMember(@Body request: MemberRequest): ApiResponse<MemberDto>

    @GET("api/members/members/{username}")
    suspend fun getMemberInfo(@Path("username") username: String): ApiResponse<MemberDto>
    
    @PUT("api/members/members/{username}/avatar")
    suspend fun updateAvatar(@Path("username") username: String, @Body request: AvatarUpdateRequest): ApiResponse<MemberDto>
    
    @GET("api/members/members/{username}/current-subscription")
    suspend fun getCurrentSubscription(@Path("username") username: String): ApiResponse<UserSubscriptionDto>
    
    @GET("api/members/subscription-plans")
    suspend fun getSubscriptionPlans(): ApiResponse<List<SubscriptionPlanDto>>
    
    @POST("api/payments/subscribe")
    suspend fun subscribePayment(@Body request: SubscribePaymentRequest): ApiResponse<SubscribePaymentResponse>
    
    @POST("api/members/subscriptions")
    suspend fun createSubscription(@Body request: CreateSubscriptionRequest): ApiResponse<UserSubscriptionDto>
    
    @GET("api/members/members/{username}/subscriptions")
    suspend fun getSubscriptionHistory(@Path("username") username: String): ApiResponse<List<UserSubscriptionDto>>
}

data class SubscribePaymentRequest(
    val username: String,
    val planId: String
)

data class SubscribePaymentResponse(
    val orderId: String
)

data class CreateSubscriptionRequest(
    val username: String,
    val planId: String,
    val paymentId: String,
    val autoRenew: Boolean = false
)

data class AvatarUpdateRequest(
    val avatar: String
)
