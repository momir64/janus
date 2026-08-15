package rs.moma.janus.kredenac.repository

import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class ChallengeRepository(private val redis: RedisCoroutinesCommands<String, String>) {
    suspend fun insert(challengeHash: String) {
        redis.setex(challengeHash, 5.minutes.inWholeSeconds, "1")
    }

    suspend fun consume(challengeHash: String): Boolean {
        val exists = redis.get(challengeHash) != null
        if (exists) redis.del(challengeHash)
        return exists
    }
}
