package com.xty.englishhelper.domain.article

import com.xty.englishhelper.domain.model.OnlineReadingSource
import java.net.URI

data class OnlineReadingSection(
    val key: String,
    val label: String,
    val group: String = ""
)

object OnlineReadingCatalog {
    val guardianSections = listOf(
        OnlineReadingSection("international", "首页", "Main"),
        OnlineReadingSection("world", "World", "Main"),
        OnlineReadingSection("us-news", "US News", "Main"),
        OnlineReadingSection("uk-news", "UK News", "Main"),
        OnlineReadingSection("science", "Science", "Topics"),
        OnlineReadingSection("uk/technology", "Tech", "Topics"),
        OnlineReadingSection("uk/environment", "Environment", "Topics"),
        OnlineReadingSection("global-development", "Development", "Topics"),
        OnlineReadingSection("uk/business", "Business", "Topics"),
        OnlineReadingSection("books", "Books", "Culture"),
        OnlineReadingSection("uk/culture", "Culture", "Culture"),
        OnlineReadingSection("uk/film", "Film", "Culture"),
        OnlineReadingSection("music", "Music", "Culture"),
        OnlineReadingSection("stage", "Stage", "Culture"),
        OnlineReadingSection("uk/tv-and-radio", "TV & Radio", "Culture"),
        OnlineReadingSection("artanddesign", "Art", "Culture"),
        OnlineReadingSection("games", "Games", "Culture"),
        OnlineReadingSection("food", "Food", "Lifestyle"),
        OnlineReadingSection("fashion", "Fashion", "Lifestyle"),
        OnlineReadingSection("uk/travel", "Travel", "Lifestyle"),
        OnlineReadingSection("uk/sport", "Sport", "Sport"),
        OnlineReadingSection("football", "Football", "Sport"),
        OnlineReadingSection("sport/cricket", "Cricket", "Sport"),
        OnlineReadingSection("sport/tennis", "Tennis", "Sport"),
        OnlineReadingSection("sport/formulaone", "F1", "Sport"),
        OnlineReadingSection("uk/commentisfree", "Opinion", "Opinion"),
        OnlineReadingSection("world/middleeast", "Middle East", "World"),
        OnlineReadingSection("world/ukraine", "Ukraine", "World"),
        OnlineReadingSection("us-news/us-politics", "US Politics", "World")
    )

    val csMonitorSections = listOf(
        OnlineReadingSection("", "首页", "Main"),
        OnlineReadingSection("World", "World", "Main"),
        OnlineReadingSection("USA", "USA", "Main"),
        OnlineReadingSection("Business", "Business", "Main"),
        OnlineReadingSection("Environment", "Environment", "Topics"),
        OnlineReadingSection("Editorials", "Editorials", "Opinion"),
        OnlineReadingSection("The-Culture", "Culture", "Culture"),
        OnlineReadingSection("The-Culture/Faith-Religion", "Faith & Religion", "Culture"),
        OnlineReadingSection("Podcasts", "Podcasts", "Media"),
        OnlineReadingSection("magazine", "Magazine", "Media")
    )

    val atlanticSections = listOf(
        OnlineReadingSection("", "首页", "Main"),
        OnlineReadingSection("latest", "Latest", "Main"),
        OnlineReadingSection("most-popular", "Popular", "Main"),
        OnlineReadingSection("politics", "Politics", "Topics"),
        OnlineReadingSection("ideas", "Ideas", "Topics"),
        OnlineReadingSection("technology", "Technology", "Topics"),
        OnlineReadingSection("science", "Science", "Topics"),
        OnlineReadingSection("health", "Health", "Topics"),
        OnlineReadingSection("education", "Education", "Topics"),
        OnlineReadingSection("economy", "Economy", "Topics"),
        OnlineReadingSection("culture", "Culture", "Culture"),
        OnlineReadingSection("books", "Books", "Culture"),
        OnlineReadingSection("family", "Family", "Culture"),
        OnlineReadingSection("international", "Global", "World"),
        OnlineReadingSection("national-security", "National Security", "World"),
        OnlineReadingSection("photo", "Photo", "Media"),
        OnlineReadingSection("projects", "Projects", "Media")
    )

    fun sectionsFor(source: OnlineReadingSource): List<OnlineReadingSection> {
        return when (source) {
            OnlineReadingSource.GUARDIAN -> guardianSections
            OnlineReadingSource.CSMONITOR -> csMonitorSections
            OnlineReadingSource.ATLANTIC -> atlanticSections
        }
    }

    fun defaultSectionFor(source: OnlineReadingSource): String {
        return sectionsFor(source).firstOrNull()?.key.orEmpty()
    }

    fun resolveSourceFromLabelOrUrl(sourceLabel: String?, sourceUrl: String?): OnlineReadingSource? {
        val label = sourceLabel.orEmpty()
        if (label.contains("Guardian", ignoreCase = true) || label.contains("卫报")) {
            return OnlineReadingSource.GUARDIAN
        }
        if (label.contains("Atlantic", ignoreCase = true)) {
            return OnlineReadingSource.ATLANTIC
        }
        if (label.contains("CSMonitor", ignoreCase = true) || label.contains("Christian Science Monitor", ignoreCase = true)) {
            return OnlineReadingSource.CSMONITOR
        }

        val host = runCatching { URI(sourceUrl.orEmpty().trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host.contains("theguardian.com") -> OnlineReadingSource.GUARDIAN
            host.contains("theatlantic.com") -> OnlineReadingSource.ATLANTIC
            host.contains("csmonitor.com") -> OnlineReadingSource.CSMONITOR
            else -> null
        }
    }
}

