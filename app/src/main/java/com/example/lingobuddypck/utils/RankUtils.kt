package com.example.lingobuddypck.utils

object RankUtils {
    enum class ProficiencyRank(val displayName: String) {
        NOVICE("Sơ Cấp"),           // 0-20
        LEARNER("Cơ Bản"),         // 21-40
        COMPETENT("Trung Cấp"),     // 41-60
        PROFICIENT("Thành Thạo"),   // 61-80
        EXPERT("Chuyên Gia")        // 81-100
    }

    fun getRankFromScore(score: Int?): ProficiencyRank? {
        return when (score) {
            null -> null
            in 0..20 -> ProficiencyRank.NOVICE
            in 21..40 -> ProficiencyRank.LEARNER
            in 41..60 -> ProficiencyRank.COMPETENT
            in 61..80 -> ProficiencyRank.PROFICIENT
            in 81..100 -> ProficiencyRank.EXPERT
            else -> null // For scores outside 0-100, or handle as needed
        }
    }
}