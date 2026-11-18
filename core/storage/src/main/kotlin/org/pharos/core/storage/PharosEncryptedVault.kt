package org.pharos.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.pharos.core.crypto.Argon2idKdf
import org.pharos.core.crypto.HardwareIdentityManager
import org.pharos.core.crypto.NativeCryptoBridge
import org.pharos.core.crypto.XChaCha20Poly1305Engine
import org.pharos.core.protocol.TacticalCard
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PHAROS Encrypted Local Vault (SQLite WAL mode + FULL synchronous durability + XChaCha20-Poly1305
 * page/row encryption) & <100ms Duress PIN Cryptographic Erasure Engine.
 *
 * When the Duress PIN is entered:
 * 1. Deletes StrongBox/TEE Keystore entries via [HardwareIdentityManager.executeDuressCryptographicErase]
 * 2. Overwrites the 64-byte master key header with zeros (`NativeCryptoBridge.secureZeroize`)
 * 3. Wipes the 50MB Blind Mule Ring Buffer and RAM Media Vault
 * 4. Queues a signed `NODE_COMPROMISED` tombstone for next Firefly/Optical burst
 * 5. Transitions the UI seamlessly into a believable Decoy Offline First-Aid & Survival Guide state
 *    in under 100 milliseconds.
 */
class PharosEncryptedVault(
    context: Context? = null,
    private val identityManager: HardwareIdentityManager,
    val ringBuffer: BlindMuleRingBuffer = BlindMuleRingBuffer(),
    normalPin: String = "2580",
    duressPin: String = "9911"
) {
    private val secureRandom = SecureRandom()
    private val masterHeader64 = ByteArray(64).also { secureRandom.nextBytes(it) }
    private var recordEncryptionKey32 = deriveVaultKey32(masterHeader64)

    private var normalPinHash32 = hashPin(normalPin)
    private var duressPinHash32 = hashPin(duressPin)

    val mediaVault = RamOnlyMediaVault(recordEncryptionKey32)

    @Volatile
    var isDecoyFirstAidModeActive: Boolean = false
        private set

    @Volatile
    var lastDuressEraseLatencyMs: Long = -1L
        private set

    @Volatile
    var pendingNodeCompromisedTombstone: ByteArray? = null
        private set

    private data class EncryptedCardRow(
        val cardId: Long,
        val isVerifiedWot: Boolean,
        val nonce24: ByteArray,
        val ciphertext200: ByteArray,
        val tag16: ByteArray
    )

    private val encryptedRows = CopyOnWriteArrayList<EncryptedCardRow>()
    private val sqliteHelper: WalSqliteHelper? = context?.let { WalSqliteHelper(it) }

    /**
     * Stores a [TacticalCard] encrypted with XChaCha20-Poly1305 in WAL storage.
     */
    fun saveTacticalCard(card: TacticalCard) {
        if (isDecoyFirstAidModeActive) return
        val nonce24 = ByteArray(24).also { secureRandom.nextBytes(it) }
        val payload200 = card.toPayload200()
        val aad = ByteArray(8) { i -> ((card.cardId ushr (56 - i * 8)) and 0xFF).toByte() }

        val (ciphertext, tag) = XChaCha20Poly1305Engine.encryptXChaCha20Poly1305(
            key32 = recordEncryptionKey32,
            nonce24 = nonce24,
            plaintext = payload200,
            associatedData = aad
        )

        val isVerified = card.verificationState == TacticalCard.VerificationState.VERIFIED_WOT
        encryptedRows.removeAll { it.cardId == card.cardId }
        encryptedRows.add(
            EncryptedCardRow(
                cardId = card.cardId,
                isVerifiedWot = isVerified,
                nonce24 = nonce24,
                ciphertext200 = ciphertext,
                tag16 = tag
            )
        )
        sqliteHelper?.insertEncryptedRow(card.cardId, isVerified, nonce24, ciphertext, tag)
    }

    /**
     * Loads and decrypts strictly isolated VERIFIED_WOT vs OPEN_QUARANTINE Tactical Cards.
     * If Duress PIN was triggered, returns benign Decoy First-Aid / Weather Check-in cards.
     */
    fun loadCardsByVerification(verifiedOnly: Boolean): List<TacticalCard> {
        if (isDecoyFirstAidModeActive) {
            return if (verifiedOnly) getDecoyFirstAidCards() else emptyList()
        }
        return encryptedRows
            .filter { it.isVerifiedWot == verifiedOnly }
            .mapNotNull { row ->
                val aad = ByteArray(8) { i -> ((row.cardId ushr (56 - i * 8)) and 0xFF).toByte() }
                val decrypted = XChaCha20Poly1305Engine.decryptXChaCha20Poly1305(
                    key32 = recordEncryptionKey32,
                    nonce24 = row.nonce24,
                    ciphertext = row.ciphertext200,
                    expectedTag16 = row.tag16,
                    associatedData = aad
                ) ?: return@mapNotNull null
                TacticalCard.fromPayload200(decrypted)
            }
            .sortedByDescending { it.timestampUtcSec }
    }

    enum class PinUnlockResult {
        UNLOCKED_TACTICAL_VAULT,
        TRIGGERED_DURESS_DECOY_MODE,
        INVALID_PIN
    }

    fun configurePins(newNormalPin: String, newDuressPin: String) {
        require(newNormalPin != newDuressPin) { "Normal PIN and Duress PIN must be distinct" }
        normalPinHash32 = hashPin(newNormalPin)
        duressPinHash32 = hashPin(newDuressPin)
    }

    /**
     * Evaluates an entered PIN in constant time. If [enteredPin] matches the Duress PIN,
     * executes full cryptographic erasure in <100ms and activates Decoy First-Aid state.
     */
    fun authenticatePin(enteredPin: String): PinUnlockResult {
        val candidateHash = hashPin(enteredPin)
        if (MessageDigest.isEqual(candidateHash, duressPinHash32)) {
            triggerDuressWipeNow()
            return PinUnlockResult.TRIGGERED_DURESS_DECOY_MODE
        }
        if (!isDecoyFirstAidModeActive && MessageDigest.isEqual(candidateHash, normalPinHash32)) {
            return PinUnlockResult.UNLOCKED_TACTICAL_VAULT
        }
        return PinUnlockResult.INVALID_PIN
    }

    /**
     * Executes emergency cryptographic erasure in <100ms.
     */
    fun triggerDuressWipeNow(): Long {
        val startNs = System.nanoTime()

        // 1. Generate signed NODE_COMPROMISED tombstone & erase StrongBox/TEE keys
        pendingNodeCompromisedTombstone = identityManager.executeDuressCryptographicErase()

        // 2. Overwrite 64-byte master header and 32-byte record encryption key in RAM
        NativeCryptoBridge.secureZeroize(masterHeader64, recordEncryptionKey32)

        // 3. Wipe 50MB Blind Mule Ring Buffer & RAM-only media vault
        ringBuffer.wipeAllOnDuress()
        mediaVault.executeDuressWipe()

        // 4. Zeroize all in-memory & SQLite WAL encrypted rows
        for (row in encryptedRows) {
            NativeCryptoBridge.secureZeroize(row.nonce24, row.ciphertext200, row.tag16)
        }
        encryptedRows.clear()
        sqliteHelper?.wipeAllTables()

        // 5. Activate believable Decoy First-Aid state
        isDecoyFirstAidModeActive = true

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        lastDuressEraseLatencyMs = elapsedMs
        return elapsedMs
    }

    private fun getDecoyFirstAidCards(): List<TacticalCard> {
        val nowSec = System.currentTimeMillis() / 1000L
        return listOf(
            TacticalCard(
                cardId = 101L,
                timestampUtcSec = nowSec - 3600,
                statusTier = TacticalCard.StatusTier.SAFE_CHECKIN,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.8182,
                longitudeDeg = 8.2275,
                altitudeMeters = 500,
                batteryPercent = 92,
                note = "FIRST AID GUIDE: Apply direct pressure to bleeding wounds. Keep patient warm."
            ),
            TacticalCard(
                cardId = 102L,
                timestampUtcSec = nowSec - 7200,
                statusTier = TacticalCard.StatusTier.RESOURCE_DROP,
                verificationState = TacticalCard.VerificationState.VERIFIED_WOT,
                latitudeDeg = 46.8200,
                longitudeDeg = 8.2300,
                altitudeMeters = 505,
                batteryPercent = 88,
                note = "HYDRATION NOTE: Boil stream water for at least 3 minutes at high altitude."
            )
        )
    }

    private fun deriveVaultKey32(header64: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("PHAROS_SQLCIPHER_WAL_KEY_V1".toByteArray(Charsets.UTF_8))
        return md.digest(header64)
    }

    private fun hashPin(pin: String): ByteArray {
        return Argon2idKdf.deriveKey32(
            passphrase = pin.trim().toByteArray(Charsets.UTF_8),
            salt16 = "PHAROS_PIN_SALT1".toByteArray(Charsets.UTF_8),
            passes = 2
        )
    }

    private class WalSqliteHelper(context: Context) :
        SQLiteOpenHelper(context, "pharos_encrypted_vault.db", null, 1) {

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.enableWriteAheadLogging()
            db.execSQL("PRAGMA synchronous=FULL;")
            db.execSQL("PRAGMA secure_delete=ON;")
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS encrypted_cards (
                    card_id INTEGER PRIMARY KEY,
                    is_verified INTEGER NOT NULL,
                    nonce24 BLOB NOT NULL,
                    ciphertext200 BLOB NOT NULL,
                    tag16 BLOB NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

        fun insertEncryptedRow(
            cardId: Long,
            isVerified: Boolean,
            nonce24: ByteArray,
            ciphertext200: ByteArray,
            tag16: ByteArray
        ) {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO encrypted_cards (card_id, is_verified, nonce24, ciphertext200, tag16) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(cardId, if (isVerified) 1 else 0, nonce24, ciphertext200, tag16)
            )
        }

        fun wipeAllTables() {
            writableDatabase.execSQL("DELETE FROM encrypted_cards;")
            writableDatabase.execSQL("VACUUM;")
        }
    }
}
