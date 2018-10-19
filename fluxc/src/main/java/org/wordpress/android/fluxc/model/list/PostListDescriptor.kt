package org.wordpress.android.fluxc.model.list

import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.fluxc.store.PostStore.DEFAULT_POST_STATUS_LIST

private const val PAGE_SIZE = 100

sealed class PostListDescriptor(
    val site: SiteModel,
    val orderBy: PostListOrderBy,
    val order: ListOrder,
    val pageSize: Int
) : ListDescriptor {
    override val uniqueIdentifier: ListDescriptorUniqueIdentifier by lazy {
        // TODO: need a better hashing algorithm, preferably a perfect hash
        when (this) {
            is PostListDescriptorForRestSite -> {
                val statusStr = statusList.asSequence().map { it.name }.joinToString(separator = ",")
                ListDescriptorUniqueIdentifier(
                        ("rest-site-post-list-${site.id}-st$statusStr-o${order.value}-ob${orderBy.value}" +
                                "-sq$searchQuery").hashCode()
                )
            }
            is PostListDescriptorForXmlRpcSite -> {
                ListDescriptorUniqueIdentifier(
                        "xml-rpc-site-post-list-${site.id}-o${order.value}-ob${orderBy.value}".hashCode()
                )
            }
        }
    }

    override val typeIdentifier: ListDescriptorTypeIdentifier by lazy {
        PostListDescriptor.calculateTypeIdentifier(site.id)
    }

    override fun hashCode(): Int {
        return uniqueIdentifier.value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as PostListDescriptor
        return uniqueIdentifier == that.uniqueIdentifier
    }

    companion object {
        @JvmStatic
        fun calculateTypeIdentifier(localSiteId: Int): ListDescriptorTypeIdentifier {
            // TODO: need a better hashing algorithm, preferably a perfect hash
            return ListDescriptorTypeIdentifier("site-post-list-$localSiteId".hashCode())
        }
    }

    class PostListDescriptorForRestSite(
        site: SiteModel,
        val statusList: List<PostStatus> = DEFAULT_POST_STATUS_LIST,
        order: ListOrder = ListOrder.DESC,
        orderBy: PostListOrderBy = PostListOrderBy.DATE,
        val searchQuery: String? = null,
        pageSize: Int = PAGE_SIZE
    ) : PostListDescriptor(site, orderBy, order, pageSize)

    class PostListDescriptorForXmlRpcSite(
        site: SiteModel,
        order: ListOrder = ListOrder.DESC,
        orderBy: PostListOrderBy = PostListOrderBy.DATE,
        pageSize: Int = PAGE_SIZE
    ) : PostListDescriptor(site, orderBy, order, pageSize)
}

enum class PostListOrderBy(val value: String) {
    DATE("date"),
    LAST_MODIFIED("modified"),
    TITLE("title"),
    COMMENT_COUNT("comment_count"),
    ID("ID");

    companion object {
        fun fromValue(value: String): PostListOrderBy? {
            return values().firstOrNull { it.value.toLowerCase() == value.toLowerCase() }
        }
    }
}