package dev.hydrogen.tieba.data

internal object GroupChatRules {
    fun canWithdraw(room: ChatRoom, message: ChatMessage, owner: String): Boolean =
        owner.toLongOrNull()?.let { it > 0 } == true && room.id.toLongOrNull()?.let { it > 0 } == true &&
            message.senderId == owner && !message.revoked && message.msgKey.isNotBlank() &&
            message.id.toLongOrNull()?.let { it > 0 } == true && room.forumId.toLongOrNull()?.let { it > 0 } == true

    fun restriction(room: ChatRoom): String? = if(room.talkBanned == true) "当前账号已被禁言，暂时不能在此群发言" else null

    // This is a history-derived directory, never a fabricated full member list.
    fun participants(messages: List<ChatMessage>): List<Person> = messages.asReversed()
        .filter { it.senderId.toLongOrNull()?.let { id -> id > 0 } == true }
        .distinctBy { it.senderId }.map { it.person.copy(id = it.senderId) }

    fun preview(message: ChatMessage): String = when {
        message.revoked -> "消息已撤回"
        message.text.isNotBlank() -> message.text
        message.image.isNotBlank() -> "[图片]"
        message.link.isNotBlank() -> "[帖子]"
        else -> "[消息类型 ${message.type}]"
    }

    fun payload(text: String, quote: ChatMessage?): String = quote?.takeUnless { it.revoked }?.let {
        "> ${it.person.name}：${preview(it).take(160).replace('\n',' ')}\n\n$text"
    } ?: text
}
