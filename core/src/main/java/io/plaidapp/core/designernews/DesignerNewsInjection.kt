/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("Injection")

package io.plaidapp.core.designernews

import android.content.Context
import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import io.plaidapp.core.BuildConfig
import io.plaidapp.core.data.api.DenvelopingConverter
import io.plaidapp.core.designernews.data.api.ClientAuthInterceptor
import io.plaidapp.core.designernews.data.api.DesignerNewsService
import io.plaidapp.core.designernews.data.comments.CommentsRemoteDataSource
import io.plaidapp.core.designernews.data.comments.CommentsRepository
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource
import io.plaidapp.core.designernews.data.login.LoginRemoteDataSource
import io.plaidapp.core.designernews.data.login.LoginRepository
import io.plaidapp.core.designernews.data.stories.StoriesRemoteDataSource
import io.plaidapp.core.designernews.data.stories.StoriesRepository
import io.plaidapp.core.designernews.domain.LoadStoriesUseCase
import io.plaidapp.core.designernews.domain.SearchStoriesUseCase
import io.plaidapp.core.loggingInterceptor
import io.plaidapp.core.provideCoroutinesContextProvider
import io.plaidapp.core.provideSharedPreferences
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * File providing different dependencies.
 *
 * Once we have a dependency injection framework or a service locator, this should be removed.
 */

fun provideLoginLocalDataSource(context: Context): LoginLocalDataSource {
    val preferences = provideSharedPreferences(
        context,
        LoginLocalDataSource.DESIGNER_NEWS_PREF
    )
    return LoginLocalDataSource(preferences)
}

fun provideLoginRepository(context: Context): LoginRepository {
    return LoginRepository.getInstance(
        provideLoginLocalDataSource(context),
        provideLoginRemoteDataSource(context)
    )
}

fun provideLoginRemoteDataSource(context: Context): LoginRemoteDataSource {
    val tokenHolder = provideAuthTokenLocalDataSource(context)
    return LoginRemoteDataSource(tokenHolder, provideDesignerNewsService(tokenHolder))
}

private fun provideAuthTokenLocalDataSource(context: Context): AuthTokenLocalDataSource {
    return AuthTokenLocalDataSource.getInstance(
        provideSharedPreferences(
            context,
            AuthTokenLocalDataSource.DESIGNER_NEWS_AUTH_PREF
        )
    )
}

fun provideDesignerNewsService(context: Context): DesignerNewsService {
    val tokenHolder = provideAuthTokenLocalDataSource(context)
    return provideDesignerNewsService(tokenHolder)
}

private fun provideDesignerNewsService(
    authTokenDataSource: AuthTokenLocalDataSource
): DesignerNewsService {
    val client = OkHttpClient.Builder()
        .addInterceptor(
            ClientAuthInterceptor(authTokenDataSource, BuildConfig.DESIGNER_NEWS_CLIENT_ID)
        )
        .addInterceptor(loggingInterceptor)
        .build()
    val gson = Gson()
    return Retrofit.Builder()
        .baseUrl(DesignerNewsService.ENDPOINT)
        .client(client)
        .addConverterFactory(DenvelopingConverter(gson))
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()
        .create(DesignerNewsService::class.java)
}

fun provideStoriesRepository(context: Context): StoriesRepository {
    return provideStoriesRepository(
        provideStoriesRemoteDataSource(
            DesignerNewsPrefs.get(context).api
        )
    )
}

private fun provideStoriesRepository(remoteDataSource: StoriesRemoteDataSource) =
    StoriesRepository.getInstance(remoteDataSource)

private fun provideStoriesRemoteDataSource(service: DesignerNewsService): StoriesRemoteDataSource {
    return StoriesRemoteDataSource.getInstance(service)
}

fun provideLoadStoriesUseCase(context: Context): LoadStoriesUseCase {
    return LoadStoriesUseCase(provideStoriesRepository(context), provideCoroutinesContextProvider())
}

fun provideSearchStoriesUseCase(context: Context): SearchStoriesUseCase {
    return SearchStoriesUseCase(
        provideStoriesRepository(context),
        provideCoroutinesContextProvider()
    )
}

fun provideCommentsRepository(dataSource: CommentsRemoteDataSource) =
    CommentsRepository.getInstance(dataSource)
