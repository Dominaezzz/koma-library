package koma.matrix.user.auth

import koma.AuthFailure
import koma.Failure
import koma.OtherFailure
import koma.matrix.UserId
import koma.matrix.json.MoshiInstance
import koma.network.client.okhttp.AppHttpClient
import koma.util.flatMap
import koma.util.onFailure
import koma.util.recover
import koma.util.KResult as Result
import okhttp3.HttpUrl
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


sealed class RegisterData() {
    // Use empty request to get auth types
    class Query(): RegisterData()
    class Password(
            val username: String,
            val password: String,
            val auth: Map<String, String> = mapOf(Pair("type", "m.login.dummy"))
    ): RegisterData()
}

data class RegisterdUser(
        val access_token: String,
        // Deprecated. Clients should extract the server_name from user_id (by splitting at the first colon)
        val home_server: String? = null,
        val user_id: UserId,
        val refresh_token: String? = null
)

interface MatrixRegisterApi {
    @POST("_matrix/client/r0/register")
    // moshi fails to encode sealed classes
    // "Any" works
    fun register(@Body data: Any): Call<RegisterdUser>
}

class Register(val server: HttpUrl, httpClient: AppHttpClient) {
    private val moshi = MoshiInstance.moshi
    private val client = httpClient.client
    private val retrofit = Retrofit.Builder()
            .baseUrl(server)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    private val service = retrofit.create(MatrixRegisterApi::class.java)
    private var session: String? = null

    suspend fun getFlows(): Result<Unauthorized, Failure> {
        val data = RegisterData.Query()
        val result = service.register(data).awaitMatrixAuth()
        result.onFailure {ex ->
            if (ex is AuthFailure) {
                session = ex.status.session // Save identifier for future requests
                return Result.of(ex.status)
            }
            return Result.error(ex)
        }
        return Result.failure(OtherFailure("Unexpected $result"))
    }

    suspend fun registerByPassword(username: String, password: String):
            Result<RegisterdUser, Failure> {
        val data = RegisterData.Password(username, password)
        println("register user $username on ${server}")
        return service.register(data).awaitMatrixAuth()
    }
}
