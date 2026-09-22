package com.pdh.cardvault.presentation

import com.pdh.cardvault.data.room.PersistentAddressDetail
import com.pdh.cardvault.data.room.PersistentAddressListItem
import com.pdh.cardvault.data.room.PersistentAddressRepository
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.validation.AddressValidator
import com.pdh.cardvault.security.clipboard.SensitiveClipboardController
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressViewModelTest {
    @Test
    fun formStartsEmptyAndInvalidValuesDoNotSave() {
        val fixture = fixture()

        assertTrue(fixture.viewModel.uiState.value.form.nickname.isEmpty())
        assertFalse(fixture.viewModel.submitAddress())
        assertTrue(fixture.repository.addresses.isEmpty())
    }

    @Test
    fun validFormCreatesFrontSafeListItemAndClearsForm() {
        val fixture = fixture()
        fixture.fillValidForm()

        assertTrue(fixture.viewModel.submitAddress())

        val state = fixture.viewModel.uiState.value
        assertEquals(1, state.addresses.size)
        assertTrue(state.form.nickname.isEmpty())
        assertEquals(AddressNavigationEvent.AddSaved, state.navigationEvent)
        assertFalse(state.addresses.single().toString().contains("虚构道路"))
    }

    @Test
    fun detailCopiesCompleteAddressAndRedactsUiToString() {
        val fixture = fixture()
        val id = fixture.repository.seed(fixture.validInput())
        fixture.reload()

        fixture.viewModel.loadDetail(id.toString())
        val loaded = fixture.viewModel.uiState.value.detail as AddressDetailUiState.Loaded

        assertTrue(fixture.viewModel.copyAddress(id))
        assertTrue(requireNotNull(fixture.clipboard.value).contains("虚构道路"))
        assertTrue(requireNotNull(fixture.clipboard.value).contains("TEST-100"))
        assertTrue(requireNotNull(fixture.clipboard.value).contains("示例国家"))
        assertFalse(loaded.toString().contains("虚构道路"))
    }

    @Test
    fun detailCopiesEveryAddressPartIndependently() {
        val fixture = fixture()
        val id = fixture.repository.seed(fixture.validInput())
        fixture.reload()
        fixture.viewModel.loadDetail(id.toString())

        val expected = mapOf(
            AddressCopyPart.DetailedAddress to "虚构道路 100 号",
            AddressCopyPart.City to "示例城市",
            AddressCopyPart.Other to "虚构楼层",
            AddressCopyPart.PostalCode to "TEST-100",
            AddressCopyPart.Country to "示例国家",
        )
        expected.forEach { (part, value) ->
            assertTrue(fixture.viewModel.copyAddress(id, part))
            assertEquals(value, fixture.clipboard.value)
        }
    }

    @Test
    fun backgroundClearsAddressStateAndOwnedClipboard() {
        val fixture = fixture()
        val id = fixture.repository.seed(fixture.validInput())
        fixture.reload()
        fixture.viewModel.loadDetail(id.toString())
        fixture.viewModel.copyAddress(id)

        fixture.viewModel.onVaultAccessRevoked()

        assertEquals(VaultContentState.Locked, fixture.viewModel.uiState.value.vaultContentState)
        assertTrue(fixture.viewModel.uiState.value.addresses.isEmpty())
        assertEquals(null, fixture.clipboard.value)
    }

    @Test
    fun editLoadsAllFieldsAndSavesUpdatedEncryptedRecordProjection() {
        val fixture = fixture()
        val id = fixture.repository.seed(fixture.validInput())
        fixture.reload()

        fixture.viewModel.loadEdit(id.toString())
        val loaded = fixture.viewModel.uiState.value.edit as AddressEditUiState.Ready
        assertEquals("示例国家", loaded.form.country)
        assertEquals("虚构道路 100 号", loaded.form.detailedAddress)

        fixture.viewModel.updateEditField(AddressFormField.Nickname, "修改后的地址")
        fixture.viewModel.updateEditField(AddressFormField.Country, "修改后的国家")
        assertTrue(fixture.viewModel.submitEdit())

        val state = fixture.viewModel.uiState.value
        assertEquals(AddressNavigationEvent.EditSaved, state.navigationEvent)
        assertEquals(AddressEditUiState.Hidden, state.edit)
        assertEquals("修改后的地址", fixture.repository.addresses.single().input.nickname)
        assertEquals("修改后的国家", fixture.repository.addresses.single().input.country)
        assertEquals("修改后的地址", state.addresses.single().nickname)
    }

    @Test
    fun invalidEditDoesNotOverwriteStoredAddress() {
        val fixture = fixture()
        val original = fixture.validInput()
        val id = fixture.repository.seed(original)
        fixture.reload()

        fixture.viewModel.loadEdit(id.toString())
        fixture.viewModel.updateEditField(AddressFormField.Country, "")

        assertFalse(fixture.viewModel.submitEdit())
        assertEquals(original, fixture.repository.addresses.single().input)
        assertTrue(
            (fixture.viewModel.uiState.value.edit as AddressEditUiState.Ready)
                .form.validationErrors.isNotEmpty(),
        )
    }

    @Test
    fun dragOrderIsOptimisticThenPersistsOneCompletePermutation() {
        val fixture = fixture()
        repeat(3) { index ->
            fixture.repository.seed(fixture.validInput().copy(nickname = "Synthetic address $index"))
        }
        fixture.reload()
        val originalIds = fixture.viewModel.uiState.value.addresses.map(AddressListItemUiModel::id)
        val requested = listOf(originalIds[2], originalIds[0], originalIds[1])
        val gate = CompletableDeferred<Unit>()
        fixture.repository.reorderGate = gate

        assertTrue(fixture.viewModel.reorderAddresses(requested))
        assertEquals(requested, fixture.viewModel.uiState.value.addresses.map { it.id })
        assertTrue(fixture.viewModel.uiState.value.sortingInProgress)

        gate.complete(Unit)

        assertEquals(requested, fixture.repository.addresses.map { it.id })
        assertEquals(requested, fixture.viewModel.uiState.value.addresses.map { it.id })
        assertFalse(fixture.viewModel.uiState.value.sortingInProgress)
        assertEquals(1, fixture.repository.reorderCount)
    }

    @Test
    fun failedDragOrderRollsBackAndInvalidPermutationNeverWrites() {
        val fixture = fixture()
        repeat(3) { index ->
            fixture.repository.seed(fixture.validInput().copy(nickname = "Synthetic address $index"))
        }
        fixture.reload()
        val original = fixture.viewModel.uiState.value.addresses
        val reorderedIds = original.reversed().map(AddressListItemUiModel::id)
        fixture.repository.failReorder = true

        assertTrue(fixture.viewModel.reorderAddresses(reorderedIds))
        assertEquals(original, fixture.viewModel.uiState.value.addresses)
        assertFalse(fixture.viewModel.uiState.value.sortingInProgress)
        assertEquals(AddressOperationMessage.StorageFailed, fixture.viewModel.uiState.value.operationMessage)
        assertEquals(1, fixture.repository.reorderCount)

        assertFalse(fixture.viewModel.reorderAddresses(listOf(original.first().id, UUID.randomUUID())))
        assertEquals(1, fixture.repository.reorderCount)
    }

    @Test
    fun revokingVaultAccessCancelsPendingAddressSortAndClearsProgress() {
        val fixture = fixture()
        repeat(2) { index ->
            fixture.repository.seed(fixture.validInput().copy(nickname = "Synthetic address $index"))
        }
        fixture.reload()
        val originalIds = fixture.repository.addresses.map { it.id }
        fixture.repository.reorderGate = CompletableDeferred()

        assertTrue(fixture.viewModel.reorderAddresses(originalIds.reversed()))
        assertTrue(fixture.viewModel.uiState.value.sortingInProgress)

        fixture.viewModel.onVaultAccessRevoked()

        assertEquals(VaultContentState.Locked, fixture.viewModel.uiState.value.vaultContentState)
        assertFalse(fixture.viewModel.uiState.value.sortingInProgress)
        assertTrue(fixture.viewModel.uiState.value.addresses.isEmpty())
        assertEquals(originalIds, fixture.repository.addresses.map { it.id })
    }

    private fun fixture(): Fixture {
        val repository = FakeAddressRepository()
        val clipboard = FakeAddressClipboard()
        val viewModel = AddressViewModel(
            repository = repository,
            validator = AddressValidator { CardTemplateRegistry.findById(it) != null },
            defaultTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
            sensitiveClipboardController = clipboard,
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        viewModel.onVaultAccessAllowed()
        return Fixture(viewModel, repository, clipboard)
    }

    private data class Fixture(
        val viewModel: AddressViewModel,
        val repository: FakeAddressRepository,
        val clipboard: FakeAddressClipboard,
    ) {
        fun validInput(): AddressInput = AddressInput(
            nickname = "常用地址",
            detailedAddress = "虚构道路 100 号",
            city = "示例城市",
            other = "虚构楼层",
            postalCode = "TEST-100",
            country = "示例国家",
            cardTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        )

        fun fillValidForm() {
            val input = validInput()
            viewModel.updateField(AddressFormField.Nickname, input.nickname)
            viewModel.updateField(AddressFormField.DetailedAddress, input.detailedAddress)
            viewModel.updateField(AddressFormField.City, input.city)
            viewModel.updateField(AddressFormField.Other, input.other)
            viewModel.updateField(AddressFormField.PostalCode, input.postalCode)
            viewModel.updateField(AddressFormField.Country, input.country)
        }

        fun reload() {
            viewModel.onVaultAccessRevoked()
            repository.unlocked = true
            viewModel.onVaultAccessAllowed()
        }
    }
}

private class FakeAddressRepository : PersistentAddressRepository {
    data class Stored(val id: UUID, val input: AddressInput)

    val addresses = mutableListOf<Stored>()
    var unlocked: Boolean = true
    var reorderCount: Int = 0
    var failReorder: Boolean = false
    var reorderGate: CompletableDeferred<Unit>? = null

    override fun isUnlocked(): Boolean = unlocked

    override suspend fun addAddress(input: AddressInput): UUID = seed(input)

    fun seed(input: AddressInput): UUID = UUID.randomUUID().also { id ->
        addresses.add(0, Stored(id, input))
    }

    override suspend fun getAddressList(): List<PersistentAddressListItem> = addresses.map {
        PersistentAddressListItem(it.id, it.input.nickname, it.input.cardTemplateId)
    }

    override suspend fun getAddressDetail(id: UUID): PersistentAddressDetail? = addresses
        .firstOrNull { it.id == id }
        ?.input
        ?.let {
            PersistentAddressDetail(
                it.nickname,
                it.detailedAddress,
                it.city,
                it.other,
                it.postalCode,
                it.country,
                it.cardTemplateId,
            )
        }

    override suspend fun getAddressEditInput(id: UUID): AddressInput? =
        addresses.firstOrNull { it.id == id }?.input

    override suspend fun updateAddress(id: UUID, input: AddressInput): Boolean {
        val index = addresses.indexOfFirst { it.id == id }
        if (index < 0) return false
        addresses[index] = Stored(id, input)
        return true
    }

    override suspend fun deleteAddress(id: UUID): Boolean = addresses.removeAll { it.id == id }

    override suspend fun reorderAddresses(orderedIds: List<UUID>) {
        reorderCount += 1
        reorderGate?.await()
        if (failReorder) throw IllegalStateException()
        val byId = addresses.associateBy(Stored::id)
        if (
            orderedIds.size != addresses.size ||
            orderedIds.distinct().size != orderedIds.size ||
            orderedIds.toSet() != byId.keys
        ) {
            throw IllegalStateException()
        }
        addresses.clear()
        addresses += orderedIds.map(byId::getValue)
    }
}

private class FakeAddressClipboard : SensitiveClipboardController {
    var value: String? = null

    override fun copyCardNumber(normalizedCardNumber: String): Boolean = false

    override fun copySensitiveText(value: String): Boolean {
        this.value = value
        return true
    }

    override fun clearCardNumberIfOwned() {
        value = null
    }
}
