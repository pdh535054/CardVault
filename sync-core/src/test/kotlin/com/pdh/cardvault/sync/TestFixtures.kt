package com.pdh.cardvault.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows

internal const val DEVICE_A = "00000000-0000-4000-8000-000000000001"
internal const val DEVICE_B = "00000000-0000-4000-8000-000000000002"
internal const val DEVICE_C = "00000000-0000-4000-8000-000000000003"
internal const val VAULT_ID = "10000000-0000-4000-8000-000000000001"
internal const val PACKAGE_A = "20000000-0000-4000-8000-000000000001"
internal const val PACKAGE_B = "20000000-0000-4000-8000-000000000002"
internal const val RECORD_A = "30000000-0000-4000-8000-000000000001"
internal const val RECORD_B = "30000000-0000-4000-8000-000000000002"
internal const val ADDRESS_A = "40000000-0000-4000-8000-000000000001"
internal const val ADDRESS_B = "40000000-0000-4000-8000-000000000002"

internal fun fictionalPayload(
    nickname: String = "虚构测试卡",
    number: String = "9876543210987654321",
    cvv: String? = "987",
): CardSyncPayload = CardSyncPayload(
    nickname = nickname,
    issuerName = "虚构发行方",
    cardNumber = number,
    expiryMonth = 6,
    expiryYear = 2029,
    saveCvv = cvv != null,
    cvv = cvv,
    cardTemplateId = "custom:midnight:mesh:modern:white",
    notes = "仅用于自动化测试的虚构内容",
)

internal fun activeRecord(
    recordId: String = RECORD_A,
    deviceId: String = DEVICE_A,
    counter: Long = 1L,
    payload: CardSyncPayload = fictionalPayload(),
): SyncRecord = SyncRecord(
    recordId = recordId,
    version = VersionVector.of(mapOf(deviceId to counter)),
    value = SyncRecordValue.Active(
        payload = payload,
        createdAtEpochMillis = 1_700_000_000_000L,
        updatedAtEpochMillis = 1_700_000_100_000L,
    ),
)

internal fun fictionalAddressPayload(
    nickname: String = "虚构测试地址",
    detailedAddress: String = "虚构测试路 88 号",
): AddressSyncPayload = AddressSyncPayload(
    nickname = nickname,
    detailedAddress = detailedAddress,
    city = "示例市",
    other = "虚构园区",
    postalCode = "000000",
    country = "示例国",
    cardTemplateId = "custom:midnight:mesh:modern:white",
)

internal fun activeAddressRecord(
    recordId: String = ADDRESS_A,
    deviceId: String = DEVICE_A,
    counter: Long = 1L,
    payload: AddressSyncPayload = fictionalAddressPayload(),
): AddressSyncRecord = AddressSyncRecord(
    recordId = recordId,
    version = VersionVector.of(mapOf(deviceId to counter)),
    value = AddressSyncRecordValue.Active(
        payload = payload,
        createdAtEpochMillis = 1_700_000_000_000L,
        updatedAtEpochMillis = 1_700_000_100_000L,
    ),
)

internal fun addressSnapshotOf(
    records: List<AddressSyncRecord>,
    orderIds: List<String> = records
        .filter { it.value is AddressSyncRecordValue.Active }
        .map(AddressSyncRecord::recordId),
    orderVector: VersionVector = VersionVector.of(mapOf(DEVICE_A to 1L)),
): AddressSyncSnapshot = AddressSyncSnapshot(
    records = records,
    order = SyncOrder(
        version = orderVector,
        updatedAtEpochMillis = 1_700_000_200_000L,
        recordIds = orderIds,
    ),
)

internal fun snapshotOf(
    records: List<SyncRecord>,
    orderIds: List<String> = records.filter { it.value is SyncRecordValue.Active }.map(SyncRecord::recordId),
    orderVector: VersionVector = VersionVector.of(mapOf(DEVICE_A to 1L)),
): SyncSnapshot = SyncSnapshot(
    records = records,
    order = SyncOrder(
        version = orderVector,
        updatedAtEpochMillis = 1_700_000_200_000L,
        recordIds = orderIds,
    ),
)

internal fun String.hexBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

internal fun ByteArray.hex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
    assertArrayEquals(expected, actual)
}

internal inline fun <reified T : Throwable> assertFailsWith(noinline block: () -> Unit): T =
    assertThrows(T::class.java, block)
