package com.example.lingobuddypck.utils

object RankUtils {
    enum class ProficiencyRank(val displayName: String) {
        NOVICE("🧸 "+"Sơ Cấp"),
        LEARNER( "📘 "+"Cơ Bản"),
        COMPETENT("🛠️ "+"Trung Cấp" ),
        PROFICIENT( "🧠 "+"Thành Thạo"),
        EXPERT("🏆 "+"Chuyên Gia")
    }

    fun getRankFromScore(score: Int?): ProficiencyRank? {
        return when (score) {
            null -> null
            in 0..20 -> ProficiencyRank.NOVICE
            in 21..40 -> ProficiencyRank.LEARNER
            in 41..60 -> ProficiencyRank.COMPETENT
            in 61..80 -> ProficiencyRank.PROFICIENT
            in 81..100 -> ProficiencyRank.EXPERT
            else -> null
        }
    }
}