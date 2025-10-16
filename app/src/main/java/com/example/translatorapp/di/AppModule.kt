package com.example.translatorapp.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.translatorapp.BuildConfig
import com.example.translatorapp.data.datasource.HistoryDao
import com.example.translatorapp.data.datasource.HistoryDatabase
import com.example.translatorapp.data.datasource.UserPreferencesDataSource
import com.example.translatorapp.data.repository.TranslationRepositoryImpl
import com.example.translatorapp.domain.repository.TranslationRepository
import com.example.translatorapp.network.ApiConfig
import com.example.translatorapp.network.ApiRelayService
import com.example.translatorapp.network.RealtimeApi
import com.example.translatorapp.network.RealtimeEventStream
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.webrtc.WebRtcClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    fun provideApiConfig(): ApiConfig = ApiConfig(BuildConfig.REALTIME_BASE_URL)

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        apiConfig: ApiConfig
    ): Retrofit = Retrofit.Builder()
        .baseUrl(apiConfig.baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApiRelayService(retrofit: Retrofit): ApiRelayService = retrofit.create(ApiRelayService::class.java)

    @Provides
    @Singleton
    fun provideRealtimeApi(service: ApiRelayService): RealtimeApi = RealtimeApi(service)

    @Provides
    @Singleton
    fun provideRealtimeEventStream(
        okHttpClient: OkHttpClient,
        json: Json,
        apiConfig: ApiConfig
    ): RealtimeEventStream = RealtimeEventStream(okHttpClient, json, apiConfig)

    @Provides
    @Singleton
    fun provideHistoryDatabase(context: Context): HistoryDatabase = Room.databaseBuilder(
        context,
        HistoryDatabase::class.java,
        "translator-history.db"
    ).build()

    @Provides
    fun provideHistoryDao(db: HistoryDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }

    @Provides
    @Singleton
    fun providePeerConnectionFactory(context: Context): PeerConnectionFactory {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val eglBase = EglBase.create()
        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    @Provides
    @Singleton
    fun provideTranslationRepository(
        impl: TranslationRepositoryImpl
    ): TranslationRepository = impl
}
