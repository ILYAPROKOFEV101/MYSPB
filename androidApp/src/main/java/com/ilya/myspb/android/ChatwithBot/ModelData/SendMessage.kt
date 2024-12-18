package com.ilya.myspb.android.ChatwithBot.ModelData

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class MessageRequest(
    val username: String,
    val message: String,
    val gptResponse: String,
    val timestamp: String,
    val character: String,
    val model: String
)


interface ApiServicePOST {
    @POST("create/messages/{uid}/{chatId}")
    fun sendMessage(
        @Path("uid") uid: String, // Передаем UID в URL
        @Path("chatId") chatId: String, // Передаем chatId в URL
        @Body messageRequest: MessageRequest // Данные для тела запроса
    ): Call<Void>
}
object RetrofitClientPOST {
    private const val BASE_URL = "https://meetmap.up.railway.app/"

    // Создание клиента OkHttp с настройками логирования и таймаутами
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Время на подключение
        .readTimeout(30, TimeUnit.SECONDS)    // Время на чтение ответа
        .writeTimeout(30, TimeUnit.SECONDS)   // Время на отправку данных
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Логировать тело запросов и ответов
        })
        .build()

    // Создание экземпляра Retrofit с добавлением клиента и конвертера Gson
    val apiService: ApiServicePOST by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // Использование настроенного клиента OkHttp
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiServicePOST::class.java)
    }
}

fun sendMessage(uid: String, chatId: String, message: String) {
    // Логирование входных параметров
    Log.d("sendMessage", "Отправка сообщения от пользователя: $uid, чат: $chatId")
    Log.d("sendMessage", "Сообщение: $message")

    // Создание объекта запроса
    val messageRequest = MessageRequest(
        username = "$uid",
        message = message,
        gptResponse = "I'm good, thank you!",
        timestamp = "2024-11-29T14:00:00",
        character = "Я — помощник, который всегда готов найти для тебя нужную информацию! Я стараюсь быть максимально точным и вежливым, помогая находить ресурсы, адреса и контактные данные, включая номера телефонов, исходя из твоих запросов. Я быстро реагирую и всегда предоставляю полную информацию, которая может быть полезна, в том числе уточняю, как найти ближайшие к тебе места. Если ты ищешь конкретные адреса или телефоны, просто скажи, и я постараюсь дать все необходимые данные, учитывая твоё местоположение. Моя цель — предоставить тебе точную информацию, будь то адрес или номер телефона, чтобы ты мог легко найти нужное.",
        model = "GigaChat"
    )

    // Логирование отправляемого сообщения
    Log.d("sendMessage", "Отправляемое сообщение: $messageRequest")

    // Вызов API для отправки сообщения
    RetrofitClientPOST.apiService.sendMessage(uid, chatId, messageRequest).enqueue(object : Callback<Void> {
        override fun onResponse(call: Call<Void>, response: Response<Void>) {
            if (response.isSuccessful) {
                // Логирование успешного ответа
                Log.d("sendMessage", "Сообщение успешно отправлено. Ответ от сервера: ${response.code()}")
            } else {
                // Логирование ошибки на сервере
                Log.e("sendMessage", "Ошибка при отправке сообщения: ${response.message()}, Код: ${response.code()}")
            }
        }

        override fun onFailure(call: Call<Void>, t: Throwable) {
            // Логирование ошибки сети или других ошибок
            Log.e("sendMessage", "Ошибка при отправке сообщения: ${t.localizedMessage}")
        }
    })
}