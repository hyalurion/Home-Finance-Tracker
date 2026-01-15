package com.chronie.homemoney.domain.repository

import com.chronie.homemoney.domain.model.Member
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus

interface MemberRepository {
    suspend fun getOrCreateMember(username: String): Result<Member>
    suspend fun getMemberInfo(username: String): Result<Member>
    suspend fun updateAvatar(username: String, avatar: String): Result<Member>
    suspend fun getCurrentSubscription(username: String): Result<SubscriptionStatus>
    suspend fun getSubscriptionPlans(): Result<List<SubscriptionPlan>>
    suspend fun subscribePayment(username: String, planId: String): Result<String>
    suspend fun createSubscription(username: String, planId: String, paymentId: String): Result<SubscriptionStatus>
    suspend fun getSubscriptionHistory(username: String): Result<List<SubscriptionStatus>>
}
