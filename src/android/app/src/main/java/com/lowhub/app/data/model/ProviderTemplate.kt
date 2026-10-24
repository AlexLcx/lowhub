package com.lowhub.app.data.model

/**
 * [T-provider-templates] Chat provider templates — the chat-side counterpart
 * of [VoiceProviderTemplate]. A template is NOT a distinct ProviderType case:
 * it rides on an existing protocol (usually OpenAI-compatible) and only
 *   1. Preseeds the Add-Provider flow (label + underlying type + base URL +
 *      appendV1), so the user just pastes an API key.
 *   2. Optionally carries default models that are seeded at add-time when
 *      the instance's base URL matches [baseURLMarkers] (same mechanism as
 *      the voice templates' mock models in ProviderRepository.addInstance);
 *      a later /models refresh reconciles them with the upstream list.
 *
 * `appendV1` matters: [baseURL] is the FINAL endpoint prefix. DeepSeek's
 * base already ends in /v1, so its template pins appendV1 = false and the
 * saved instance's effectiveBaseURL stays exactly https://api.deepseek.com/v1
 * (ProviderInstance.effectiveBaseURL also guards against doubling /v1).
 */
data class ProviderTemplate(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val baseURL: String,
    val appendV1: Boolean,
    val baseURLMarkers: List<String>,
    val defaultModels: List<LLMModel> = emptyList(),
    /** Optional caveat surfaced in the UI (extra credentials etc.). */
    val note: String? = null,
) {
    companion object {
        /** The template whose base-URL markers match [baseURL], if any. */
        fun template(forBaseURL: String?): ProviderTemplate? {
            val base = forBaseURL?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return all.firstOrNull { tpl -> tpl.baseURLMarkers.any { base.contains(it) } }
        }

        /** Build the seed ModelEntry list for an instance matching a template. */
        fun defaultEntries(instance: ProviderInstance): List<ModelEntry> {
            val tpl = template(instance.customBaseURL) ?: return emptyList()
            return tpl.defaultModels.map { ModelEntry(providerInstanceId = instance.id, baseModel = it) }
        }

        val all: List<ProviderTemplate> = listOf(
            // [T-provider-template-deepseek] DeepSeek via its OpenAI-
            // compatible API. Endpoint is fixed by the vendor; the user only
            // supplies an API key. deepseek-reasoner is the reasoning model.
            ProviderTemplate(
                id = "deepseek",
                name = "DeepSeek",
                providerType = ProviderType.openAI,
                baseURL = "https://api.deepseek.com/v1",
                appendV1 = false,
                baseURLMarkers = listOf("api.deepseek.com"),
                defaultModels = listOf(
                    LLMModel(id = "deepseek-chat", displayName = "DeepSeek Chat", provider = "deepseek"),
                    LLMModel(
                        id = "deepseek-reasoner",
                        displayName = "DeepSeek Reasoner",
                        provider = "deepseek",
                        supportsReasoning = true,
                    ),
                ),
            ),
        )
    }
}
