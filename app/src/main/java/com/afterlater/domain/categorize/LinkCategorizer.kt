package com.afterlater.domain.categorize

/**
 * Suggests one of the four beta categories — CODING, DESIGN, OFFER, AI — for
 * a saved link, from its domain and whatever text is available (the URL at
 * capture time; the fetched page title and description once metadata
 * arrives).
 *
 * Deliberately rule-based, not learned: a domain match is decisive, keyword
 * hits are counted per category and the strongest wins, ties break in
 * [CATEGORY_ORDER]. Ambiguous links suggest nothing — a wrong guess is worse
 * than none.
 */
object LinkCategorizer {

    val CATEGORY_ORDER = listOf("DESIGN", "CODING", "OFFER", "AI")

    /** Substrings of the domain that pin the category outright (score 3). */
    private val DOMAIN_HINTS = mapOf(
        "CODING" to listOf(
            "github.", "gitlab.", "bitbucket.", "stackoverflow.", "stackexchange.",
            "npmjs.", "dev.to", "news.ycombinator.", "codepen.", "leetcode.",
            "hackerrank.", "kotlinlang.", "developer.android.", "freecodecamp.",
            "codewars.", "jetbrains.", "docs.", "developer.",
        ),
        "DESIGN" to listOf(
            "dribbble.", "behance.", "figma.", "canva.", "awwwards.",
            "designboom.", "99designs.", "unsplash.", "pexels.", "flaticon.",
            "iconfinder.", "coolors.", "mobbin.", "pttrns.",
        ),
        "OFFER" to listOf(
            "amazon.", "aliexpress.", "ebay.", "etsy.", "shein.", "temu.",
            "noon.", "walmart.", "flipkart.", "myntra.", "souq.",
        ),
        "AI" to listOf(
            "openai.", "chatgpt.", "anthropic.", "claude.", "huggingface.",
            "midjourney.", "perplexity.", "deepmind.", "elevenlabs.",
            "civitai.", "runwayml.",
        ),
    )

    /**
     * Keyword hints on the page/url text (score 1 each). Multi-word keywords
     * and tokens like " ui " rely on the haystack being space-padded.
     */
    private val KEYWORD_HINTS = mapOf(
        "CODING" to listOf(
            " programming", "coding", " developer", "tutorial", "javascript",
            "typescript", " python ", " kotlin ", "flutter", "react", "open source",
            " api ", "software engineer", "code review", " vscode", "algorithm",
            "data structure", "learn to code", "framework",
        ),
        "DESIGN" to listOf(
            " design ", "figma", "typography", " logo ", "mockup", "wireframe",
            "wallpaper", "icon set", "dribbble", "behance", " ui kit", " ux ",
            "inspiration", "canva", " ui ", "font",
        ),
        "OFFER" to listOf(
            " sale ", "discount", " deal ", "deals ", " offer ", "coupon",
            " promo ", "cashback", "% off", "buy now", " free shipping",
            "limited time", "price drop",
        ),
        "AI" to listOf(
            "chatgpt", "gpt-4", "gpt-5", " openai", " llm ", "midjourney",
            "stable diffusion", "machine learning", "deep learning",
            "neural network", "artificial intelligence", " copilot",
            "perplexity", "generative ai", " ai tool", " ai app", "prompt engineering",
        ),
    )

    fun suggest(
        domain: String,
        canonicalUrl: String,
        title: String?,
        description: String?,
    ): String? {
        val haystack = " $domain ${canonicalUrl.lowercase()} ${title.orEmpty()} ${description.orEmpty()} "
            .replace(Regex("\\s+"), " ")
            .lowercase()
        val normalizedDomain = domain.lowercase()

        var best: Pair<String, Int>? = null
        for (category in CATEGORY_ORDER) {
            val domainScore = if (DOMAIN_HINTS[category].orEmpty().any(normalizedDomain::contains)) 3 else 0
            val keywordScore = KEYWORD_HINTS[category].orEmpty().count(haystack::contains)
            val score = domainScore + keywordScore
            if (score > 0 && (best == null || score > best.second)) {
                best = category to score
            }
        }
        return best?.first
    }
}
