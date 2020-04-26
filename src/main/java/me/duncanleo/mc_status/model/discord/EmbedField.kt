package me.duncanleo.mc_status.model.discord

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EmbedField (
        val name: String,
        val value: String,
        val inline: Boolean = false
)