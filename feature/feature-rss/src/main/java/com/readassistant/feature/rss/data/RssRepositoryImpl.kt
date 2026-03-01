package com.readassistant.feature.rss.data

import com.readassistant.core.data.db.dao.ArticleDao
import com.readassistant.core.data.db.dao.FeedDao
import com.readassistant.core.data.db.entity.FeedEntity
import com.readassistant.feature.rss.domain.Feed
import com.readassistant.feature.rss.domain.FeedArticle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val fetcher: RssFeedFetcher
) {
    fun getAllFeeds(): Flow<List<Feed>> = feedDao.getAllFeeds().map { list -> list.map { it.toDomain() } }
    fun getArticlesByFeed(feedId: Long): Flow<List<FeedArticle>> = articleDao.getArticlesByFeed(feedId).map { list -> list.map { it.toDomain() } }
    fun getStarredArticles(): Flow<List<FeedArticle>> = articleDao.getStarredArticles().map { list -> list.map { it.toDomain() } }

    suspend fun addFeed(url: String): Feed {
        val result = fetcher.fetchFeed(url)
        val entity = FeedEntity(url = url, title = result.title, description = result.description, imageUrl = result.imageUrl, lastFetchedAt = System.currentTimeMillis())
        val feedId = feedDao.insert(entity)
        val articles = result.articles.map { it.copy(feedId = feedId) }
        articleDao.insertAll(articles)
        feedDao.updateUnreadCount(feedId, articles.size)
        return entity.copy(id = feedId, unreadCount = articles.size).toDomain()
    }

    suspend fun refreshFeed(feedId: Long) {
        val feed = feedDao.getFeedById(feedId) ?: return
        val result = fetcher.fetchFeed(feed.url)
        val newArticles = result.articles.map { it.copy(feedId = feedId) }
            .filter { it.link.isNotBlank() }
            .filter { articleDao.getArticleByLink(it.link) == null }
        if (newArticles.isNotEmpty()) articleDao.insertAll(newArticles)
        feedDao.updateUnreadCount(feedId, articleDao.getUnreadCount(feedId))
        feedDao.updateLastFetched(feedId, System.currentTimeMillis())
    }

    suspend fun refreshAllFeeds() {
        val feeds = feedDao.getAllFeedsSync()
        feeds.forEach { try { refreshFeed(it.id) } catch (_: Exception) {} }
    }

    suspend fun deleteFeed(feedId: Long) { feedDao.getFeedById(feedId)?.let { feedDao.delete(it) } }
    suspend fun markArticleRead(articleId: Long) {
        val article = articleDao.getArticleById(articleId) ?: return
        if (!article.isRead) {
            articleDao.updateReadStatus(articleId, true)
            val unreadCount = articleDao.getUnreadCount(article.feedId)
            feedDao.updateUnreadCount(article.feedId, unreadCount)
        }
    }
    suspend fun markAllArticlesRead(feedId: Long) {
        val changed = articleDao.markAllReadByFeed(feedId)
        if (changed > 0) feedDao.updateUnreadCount(feedId, 0)
    }
    suspend fun toggleArticleStar(articleId: Long) { articleDao.getArticleById(articleId)?.let { articleDao.updateStarredStatus(articleId, !it.isStarred) } }
    suspend fun getArticleById(articleId: Long): FeedArticle? = articleDao.getArticleById(articleId)?.toDomain()
}
