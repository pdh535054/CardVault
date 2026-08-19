package com.pdh.cardvault.navigation

import java.util.UUID

enum class CardVaultDestination(
    val route: String,
) {
    SecurityNotice("security_notice"),
    Locked("locked"),
    Home("home"),
    Cards("cards"),
    AddCard("add_card"),
    CardDetail("card_detail/{recordId}"),
    EditCard("edit_card/{recordId}"),
    Settings("settings"),
    VaultTransfer("vault_transfer"),
    AboutPrivacy("about_privacy"),
    TemplateGallery("template_gallery"),
    Addresses("addresses"),
    AddAddress("add_address"),
    AddressDetail("address_detail/{recordId}"),
    EditAddress("edit_address/{recordId}"),

    ;

    companion object {
        const val RECORD_ID_ARGUMENT = "recordId"

        fun cardDetailRoute(recordId: UUID): String = "card_detail/$recordId"

        fun editCardRoute(recordId: UUID): String = "edit_card/$recordId"

        fun addressDetailRoute(recordId: UUID): String = "address_detail/$recordId"

        fun editAddressRoute(recordId: UUID): String = "edit_address/$recordId"
    }
}
