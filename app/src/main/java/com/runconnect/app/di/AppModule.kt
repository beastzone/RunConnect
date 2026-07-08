package com.runconnect.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.runconnect.app.BuildConfig
import com.runconnect.app.data.remote.garmin.GarminApiService
import com.runconnect.app.data.remote.garmin.GarminAuthManager
import com.runconnect.app.data.remote.garmin.OAuth1Signer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "runconnect_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authManager: GarminAuthManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = kotlinx.coroutines.runBlocking { authManager.getAccessToken() }
                    ?: return@addInterceptor chain.proceed(original)

                val consumerKey = kotlinx.coroutines.runBlocking { authManager.getConsumerKey() }
                val consumerSecret = kotlinx.coroutines.runBlocking { authManager.getConsumerSecret() }
                val url = original.url.toString().substringBefore("?")

                val authHeader = OAuth1Signer.buildAuthorizationHeader(
                    method = original.method,
                    url = url,
                    consumerKey = consumerKey,
                    consumerSecret = consumerSecret,
                    accessToken = token.token,
                    tokenSecret = token.tokenSecret,
                )
                chain.proceed(
                    original.newBuilder()
                        .header("Authorization", authHeader)
                        .build()
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideGarminRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://connect.garmin.com/modern/proxy/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGarminApiService(retrofit: Retrofit): GarminApiService =
        retrofit.create(GarminApiService::class.java)
}
