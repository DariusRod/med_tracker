package app.doselog.domain.model

import app.doselog.domain.AmountFormat

/** What the Add/Edit Item screen submits. [id] is null for a new item. */
data class ItemDraft(
    val id: Long?,
    val name: String,
    val category: Category,
    val defaultUnit: String?,
    val options: List<OptionDraft>,
)

/** A preset being edited. An empty [label] means "use the automatic label". */
data class OptionDraft(
    val id: Long?,
    val amount: Double?,
    val unit: String,
    val label: String,
) {
    val effectiveLabel: String get() = label.trim().ifEmpty { AmountFormat.label(amount, unit) }
}

enum class NameError(val message: String) {
    REQUIRED("Name is required"),
    DUPLICATE("An item with this name already exists"),
}

enum class OptionError(val message: String) {
    NEEDS_AMOUNT_OR_LABEL("Enter an amount or a label"),
    AMOUNT_NOT_POSITIVE("Amount must be greater than 0"),
}

data class ItemErrors(
    val name: NameError? = null,
    val options: Map<Int, OptionError> = emptyMap(),
) {
    val isEmpty: Boolean get() = name == null && options.isEmpty()
}

object ItemValidator {

    fun validateOption(option: OptionDraft): OptionError? = when {
        option.amount == null && option.effectiveLabel.isBlank() -> OptionError.NEEDS_AMOUNT_OR_LABEL
        option.amount != null && option.amount <= 0.0 -> OptionError.AMOUNT_NOT_POSITIVE
        else -> null
    }

    /**
     * Names must be unique among non-archived items, ignoring case and surrounding spaces.
     * [activeItems] are (id, name) pairs of the current non-archived items.
     */
    fun validate(draft: ItemDraft, activeItems: List<Pair<Long, String>>, checkDuplicates: Boolean = true): ItemErrors {
        val name = draft.name.trim()
        val nameError = when {
            name.isEmpty() -> NameError.REQUIRED
            checkDuplicates && isDuplicate(name, draft.id, activeItems) -> NameError.DUPLICATE
            else -> null
        }
        val optionErrors = draft.options
            .mapIndexedNotNull { index, option -> validateOption(option)?.let { index to it } }
            .toMap()
        return ItemErrors(nameError, optionErrors)
    }

    fun isDuplicate(name: String, selfId: Long?, activeItems: List<Pair<Long, String>>): Boolean =
        activeItems.any { (id, other) -> id != selfId && other.trim().equals(name.trim(), ignoreCase = true) }
}
