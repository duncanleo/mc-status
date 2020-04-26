package me.duncanleo.mc_status.model.discord

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiscordWebhookPayload(
        val username: String,
        val content: String = "",
        val embeds: Array<Embed>
)