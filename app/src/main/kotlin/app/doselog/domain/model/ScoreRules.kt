package app.doselog.domain.model

object ScoreRules {
    const val MIN = 1
    const val MAX = 20

    fun isValid(score: Int): Boolean = score in MIN..MAX

    fun requireValid(score: Int) {
        require(isValid(score)) { "Score must be between $MIN and $MAX, was $score" }
    }
}
