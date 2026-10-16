package com.biko.client.data

import com.biko.client.data.model.MessageItem
import com.biko.client.data.model.PrivateSession

/** 进入某位 UP 主主页时暂存的显示信息（昵称与头像先渲染，详情再异步补齐）。 */
data class SpaceUser(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

object SpaceSelection {
    var current: SpaceUser? = null
}

/** 进入某个私信会话时暂存的信息（昵称 / 头像用于聊天页顶栏先渲染）。 */
object ChatSelection {
    var current: PrivateSession? = null
}

/** 进入「点赞详情」页时暂存的来源消息（id / counts / uri 用于请求与跳转）。 */
object LikeDetailSelection {
    var current: MessageItem? = null
}

/** 进入某合集 / 系列详情页时暂存的信息（顶栏标题先渲染，内容再异步加载）。 */
data class CollectionSelectionData(
    val mid: Long = 0,
    val id: Long = 0,
    val isSeason: Boolean = true,
    val title: String = "",
)

object CollectionSelection {
    var current: CollectionSelectionData? = null
}

/** 进入话题页时暂存的信息（评论里的「#话题#」只有名字，id 由页面内搜索解析）。 */
data class TopicSelectionData(
    val id: Long = 0,
    val name: String = "",
)

object TopicSelection {
    var current: TopicSelectionData? = null
}
