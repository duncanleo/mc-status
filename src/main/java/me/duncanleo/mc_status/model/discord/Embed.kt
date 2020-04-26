package me.duncanleo.mc_status.model.discord

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Embed(
        val title: String,
        val description: String,
        val fields: Array<EmbedField>
)