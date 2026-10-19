import com.stitchdb.auth.Auth
import com.stitchdb.auth.status.SessionStatus

fun Auth.sessionSource() = (sessionStatus.value as SessionStatus.Authenticated).source

inline fun <reified T> Auth.sessionSourceAs() = sessionSource() as T