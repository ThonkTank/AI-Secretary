package de.thonktank.autosecretary

import android.os.Bundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32

/** Versioned files containing explicit primitives. Draft field keys are shared with the legacy Bundle reader. */
internal object FlowSessionCodec {
    private const val HEAD = 0x464c5348
    private const val DRAFT = 0x464c4452
    private const val VERSION = 1

    fun encodeHead(generation: Long, state: FlowSessionSnapshot) = encode(HEAD) {
        writeLong(generation)
        ref(state.current)
        writeInt(state.history.size)
        state.history.forEach { ref(it) }
        writeInt(state.page)
        writeBoolean(state.form != null)
        state.form?.let { form ->
            string(form.kind); nullable(form.id); writeInt(form.fields.size)
            form.fields.forEach { (key, value) -> string(key); string(value) }
        }
        nullable(state.error); writeBoolean(state.loading); writeBoolean(state.loadFailed)
        nullable(state.loadId); nullable(state.pendingKey); state.pending?.let { ref(it) }; nullable(state.savedTaskId)
    }

    fun readHead(file: File, revision: (String, String) -> FlowSessionRevision) = decode(file, HEAD) {
        fun readRevision() = revision(string(), string())
        val generation = readLong()
        require(generation > 0)
        val current = readRevision()
        val historySize = readInt()
        require(historySize in 0..100)
        val history = List(historySize) { readRevision() }
        val page = readInt()
        require(page in 1..2)
        val form = if (readBoolean()) {
            val kind = string()
            val id = nullable()
            val fieldCount = count()
            val fields = buildMap { repeat(fieldCount) { put(string(), string()) } }
            FlowEditorForm(kind, id, fields)
        } else null
        val error = nullable()
        val loading = readBoolean()
        val loadFailed = readBoolean()
        val loadId = nullable()
        val key = nullable()
        val pending = key?.let { readRevision() }
        generation to FlowSessionSnapshot(current, history, page, form, error, loading, loadFailed,
            loadId, key, pending, nullable())
    }

    fun generation(file: File): Long = DataInputStream(file.inputStream().buffered()).use {
        verifyHeader(it, HEAD)
        it.readLong()
    }

    private fun DataOutputStream.ref(value: FlowSessionRevision) { string(value.id); string(value.draft.name) }

    fun encodeDraft(draft: FlowEditorDraft) = encode(DRAFT) {
        val fields = draft.toBundle()
        fields.remove("name") // The session head owns each revision's raw title, including after a rename.
        bundle(fields)
    }

    fun readDraft(file: File) = decode(file, DRAFT) { FlowEditorDraft.fromBundle(bundle()) }

    private fun encode(kind: Int, body: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.writeInt(kind); out.writeInt(VERSION); out.body()
        val checksum = CRC32()
        checksum.update(bytes.toByteArray())
        out.writeInt(checksum.value.toInt())
        return bytes.toByteArray()
    }

    private fun <T> decode(file: File, kind: Int, body: DataInputStream.() -> T): T {
        val bytes = file.readBytes()
        if (bytes.size < 12) throw IOException("Entwurfsdatei ist unvollständig")
        val length = bytes.size - 4
        val checksum = CRC32().apply { update(bytes, 0, length) }
        val expected = DataInputStream(ByteArrayInputStream(bytes, length, 4)).readInt()
        if (checksum.value.toInt() != expected) throw IOException("Prüfsumme des Ablaufentwurfs ist ungültig")
        val input = DataInputStream(ByteArrayInputStream(bytes, 0, length))
        verifyHeader(input, kind)
        return try {
            input.body().also { if (input.available() != 0) throw IOException("Unerwartete Daten im Ablaufentwurf") }
        } catch (e: RuntimeException) {
            throw IOException("Ungültige gespeicherte Entwurfsdaten", e)
        }
    }

    private fun verifyHeader(input: DataInputStream, kind: Int) {
        if (input.readInt() != kind || input.readInt() != VERSION) throw IOException("Unbekanntes Entwurfsformat")
    }

    // Preserve the exact UTF-16 input, including partial IME input; writeUTF would cap strings at 65,535 bytes.
    private fun DataOutputStream.string(value: String) { writeInt(value.length); writeChars(value) }
    private fun DataInputStream.string(): String {
        val length = readInt()
        require(length in 0..available() / 2)
        return String(CharArray(length) { readChar() })
    }
    private fun DataOutputStream.nullable(value: String?) { writeBoolean(value != null); value?.let { string(it) } }
    private fun DataInputStream.nullable() = if (readBoolean()) string() else null
    private fun DataInputStream.count() = readInt().also { require(it in 0..available()) }

    // Order is part of format version 1.
    private enum class Field { NULL, STRING, INT, LONG, BOOLEAN, MAP, STRINGS, MAPS }

    @Suppress("DEPRECATION")
    private fun DataOutputStream.bundle(fields: Bundle) {
        writeInt(fields.size())
        for (key in fields.keySet()) {
            string(key)
            when (val value = fields.get(key)) {
                null -> writeByte(Field.NULL.ordinal)
                is String -> { writeByte(Field.STRING.ordinal); string(value) }
                is Int -> { writeByte(Field.INT.ordinal); writeInt(value) }
                is Long -> { writeByte(Field.LONG.ordinal); writeLong(value) }
                is Boolean -> { writeByte(Field.BOOLEAN.ordinal); writeBoolean(value) }
                is Bundle -> { writeByte(Field.MAP.ordinal); bundle(value) }
                is List<*> -> when {
                    value.all { it is String } -> {
                        writeByte(Field.STRINGS.ordinal); writeInt(value.size); value.forEach { string(it as String) }
                    }
                    value.all { it is Bundle } -> {
                        writeByte(Field.MAPS.ordinal); writeInt(value.size); value.forEach { bundle(it as Bundle) }
                    }
                    else -> throw IOException("Unbekannte Liste im Ablaufentwurf")
                }
                else -> throw IOException("Unbekanntes Feld im Ablaufentwurf: $key")
            }
        }
    }

    private fun DataInputStream.bundle(): Bundle {
        val fields = Bundle()
        repeat(count()) {
            val key = string()
            when (Field.entries[readUnsignedByte()]) {
                Field.NULL -> fields.putString(key, null)
                Field.STRING -> fields.putString(key, string())
                Field.INT -> fields.putInt(key, readInt())
                Field.LONG -> fields.putLong(key, readLong())
                Field.BOOLEAN -> fields.putBoolean(key, readBoolean())
                Field.MAP -> fields.putBundle(key, bundle())
                Field.STRINGS -> fields.putStringArrayList(key, ArrayList(List(count()) { string() }))
                Field.MAPS -> fields.putParcelableArrayList(key, ArrayList(List(count()) { bundle() }))
            }
        }
        return fields
    }
}
