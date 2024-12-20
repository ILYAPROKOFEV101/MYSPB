

import android.util.Log

import kotlinx.serialization.json.Json
import okhttp3.FormBody

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.MediaType.Companion.toMediaType


suspend fun Postquashen(token: String, promt: String): String {
    // Создание кастомного TrustManager, который игнорирует ошибки сертификатов
    val trustAllCertificates: TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // Пропускаем проверку
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // Пропускаем проверку
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    // Создание SSLContext с кастомным TrustManager
    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustAllCertificates), java.security.SecureRandom())

    // Создание SSLSocketFactory для клиента
    val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

    // Создание OkHttpClient с игнорированием SSL-сертификатов
    val client = OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCertificates as X509TrustManager)
        .hostnameVerifier { _, _ -> true } // Отключаем проверку имени хоста
        .build()

    // Формирование тела запроса с параметром scope
    val jsonBody = """
{
  "model": "GigaChat",
  "messages": [
    {
      "role": "system",
      "content": "Ты — эксперт по Арктике, специализируешься на географии и истории региона. Ты рассказываешь о достопримечательностях Арктики, их географическом расположении, историческом значении и особенностях, которые делают их уникальными."
    },
    {
      "role": "user",
      "content": "$promt"
    }
  ],
  "stream": false,
  "update_interval": 0
}
    """.trimIndent()


    Log.d("Postquashen", "Запрос был body: ${jsonBody}")

    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = jsonBody.toRequestBody(mediaType)

    // Создание запроса
    val request = Request.Builder()
        .url("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") // URL для получения токена
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
        .addHeader("Accept", "application/json")
        .addHeader("Authorization", "Bearer $token") // Замените на ваш ключ авторизации
        .post(body)
        .build()

    // Логируем создание запроса
    Log.d("Postquashen", "Запрос был создан: ${request.url}")
    Log.d("Postquashen", "Запрос был headers: ${request.headers}")
    Log.d("Postquashen", "Запрос был body: ${request.body}")


    try {
        // Отправка запроса и получение ответа
        val response: Response = client.newCall(request).execute()

        // Логируем код ответа
        Log.d("Postquashen", "Получен ответ, код: ${response.code}")

        // Логируем тело ответа (даже если оно неуспешно)
        val responseBody = response.body?.string() ?: "Нет тела ответа"
        Log.d("Postquashen", "Тело ответа: $responseBody")

        // Если ответ успешен
        if (response.isSuccessful) {
            // Логирование успешного ответа
            Log.d("Postquashen", "Успех, тело ответа: $responseBody")
            return responseBody
        } else {
            // Обработка ошибки, если ответ не успешен
            Log.e("Postquashen", "Ошибка, код ответа: ${response.code}")
            throw Exception("Ошибка: ${response.code} - $responseBody")
        }
    } catch (e: Exception) {
        // Логирование ошибок
        Log.e("Postquashen", "Запрос не удался: ${e.message}")
        throw Exception("Запрос не удался: ${e.message}", e)
    }
}
