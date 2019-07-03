package koma.matrix.user.auth

import koma.util.KResult as Result
import koma.matrix.json.MoshiInstance
import koma.util.coroutine.adapter.retrofit.HttpException
import koma.util.coroutine.adapter.retrofit.MatrixError
import koma.util.coroutine.adapter.retrofit.MatrixException
import koma.util.coroutine.adapter.retrofit.awaitMatrix
import koma.util.mapErr
import koma.util.onFailure
import retrofit2.Call


/**
 * the server may return instructions for further authentication
 */
suspend fun <T : Any> Call<T>.awaitMatrixAuth(): Result<T, Exception> {
    val result = this.awaitMatrix()
            .mapErr { error ->
                if (error is MatrixException) {
                    AuthException.MatrixFail(error.mxErr)
                } else if (error is HttpException) {
                    if (error.code == 401 && error.body != null) {
                        val unauth = Unauthorized.fromSource(error.body)
                        if (unauth != null) {
                            AuthException.AuthFail(unauth)
                        } else error
                    } else {
                        AuthException.HttpFail(error)
                    }
                } else {
                    error
                }
            }
    return result
}


data class Unauthorized(
        // Need more stages
        val completed: List<String>?,
        // Try again, for example, incorrect passwords
        val errcode: String?,
        val error: String?,
        val flows: List<AuthFlow<String>>,
        val params: Map<String, Any>,
        val session: String?
) {
    fun flows(): List<AuthFlow<AuthType>> {
        return flows.map { flow ->
            AuthFlow<AuthType>(flow.stages.map { stage -> AuthType.parse(stage) })
        }
    }
    companion object {
        private val moshi = MoshiInstance.moshi
        private val jsonAdapter = moshi.adapter(Unauthorized::class.java)

        fun fromSource(bs: String): Unauthorized? =  jsonAdapter.fromJson(bs)
    }
}

data class AuthFlow<T>(
        val stages: List<T>
)

sealed class AuthType(val type: String) {
    class Dummy(t: String): AuthType(type=t)
    class Email(t: String): AuthType(t)
    class Recaptcha(t: String): AuthType(t)
    class Other(type: String): AuthType(type)

    companion object {
        fun parse(s: String): AuthType {
            return when (s) {
                "m.login.dummy" -> Dummy(s)
                "m.login.recaptcha" -> Recaptcha(s)
                "m.login.email.identity" -> Email(s)
                else -> Other(s)
            }
        }
    }

    fun toDisplay(): String {
        return when (this) {
            is Dummy -> "Password"
            is Email -> "Email"
            is Recaptcha -> "Captcha"
            is Other -> this.type
        }
    }
}

sealed class AuthException(message: String): Exception(message) {
    class AuthFail(
            val status: Unauthorized): AuthException(status.toString())
    class MatrixFail(
            val error: MatrixError): AuthException(error.toString())
    // Other http errors
    class HttpFail(
            val error: HttpException): AuthException(error.message)
}
