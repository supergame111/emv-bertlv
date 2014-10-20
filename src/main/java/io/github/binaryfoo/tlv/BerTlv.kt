package io.github.binaryfoo.tlv

import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Arrays
import kotlin.platform.platformStatic

public abstract class BerTlv(public val tag: Tag) {

    public fun toBinary(): ByteArray {
        val value = getValue()
        val encodedTag = tag.bytes
        val encodedLength = getLength(value)

        val b = ByteBuffer.allocate(encodedTag.size + encodedLength.size + value.size)
        b.put(encodedTag)
        b.put(encodedLength)
        b.put(value)
        b.flip()
        return b.array()
    }

    public fun toHexString(): String {
        return ISOUtil.hexString(toBinary())
    }

    public fun getValueAsHexString(): String {
        return ISOUtil.hexString(getValue())
    }

    public fun getLengthInBytesOfEncodedLength(): Int {
        return getLength(getValue()).size
    }

    public abstract fun findTlv(tag: Tag): BerTlv?

    public abstract fun findTlvs(tag: Tag): List<BerTlv>

    public abstract fun getValue(): ByteArray

    public abstract fun getChildren(): List<BerTlv>

    private fun getLength(value: ByteArray?): ByteArray {
        val length: ByteArray
        if (value == null) {
            return byteArray(0.toByte())
        }
        if (value.size <= 0x7F) {
            length = byteArray(value.size.toByte())
        } else {
            val wanted = value.size
            var expected = 256
            var needed = 1
            while (wanted >= expected) {
                needed++
                expected = expected shl 8
                if (expected == 0) {
                    // just to be sure
                    throw IllegalArgumentException()
                }
            }
            length = ByteArray(needed + 1)
            length[0] = (0x80 or needed).toByte()
            for (i in 1..length.size - 1) {
                length[length.size - i] = ((wanted shr (8 * (i - 1))) and 255).toByte()
            }

        }
        return length
    }

    class object {

        platformStatic public fun newInstance(tag: Tag, value: ByteArray): BerTlv {
            return PrimitiveBerTlv(tag, value)
        }

        platformStatic public fun newInstance(tag: Tag, hexString: String): BerTlv {
            return PrimitiveBerTlv(tag, ISOUtil.hex2byte(hexString))
        }

        platformStatic public fun newInstance(tag: Tag, value: Int): BerTlv {
            if (value > 255) {
                throw IllegalArgumentException("Value greater than 255 must be encoded in a byte array")
            }
            return PrimitiveBerTlv(tag, byteArray(value.toByte()))
        }

        platformStatic public fun newInstance(tag: Tag, value: List<BerTlv>): BerTlv {
            return ConstructedBerTlv(tag, value)
        }

        platformStatic public fun newInstance(tag: Tag, tlv1: BerTlv, tlv2: BerTlv): BerTlv {
            return ConstructedBerTlv(tag, Arrays.asList<BerTlv>(tlv1, tlv2))
        }

        platformStatic public fun parse(data: ByteArray): BerTlv {
            val tlvs = parseList(ByteBuffer.wrap(data), true)
            return tlvs.get(0)
        }

        platformStatic public fun parseAsPrimitiveTag(data: ByteArray): BerTlv {
            val tlvs = parseList(ByteBuffer.wrap(data), false)
            return tlvs.get(0)
        }

        platformStatic public fun parseList(data: ByteArray, parseConstructedTags: Boolean): List<BerTlv> {
            return parseList(ByteBuffer.wrap(data), parseConstructedTags)
        }

        private fun parseList(data: ByteBuffer, parseConstructedTags: Boolean): List<BerTlv> {
            val tlvs = ArrayList<BerTlv>()

            while (data.hasRemaining()) {
                val tag = Tag.parse(data)
                if (isPaddingByte(tag)) {
                    continue
                }
                try {
                    val length = parseLength(data)
                    val value = readUpToLength(data, length)
                    if (tag.isConstructed() && parseConstructedTags) {
                        try {
                            tlvs.add(newInstance(tag, parseList(value, true)))
                        } catch (e: Exception) {
                            tlvs.add(newInstance(tag, value))
                        }

                    } else {
                        tlvs.add(newInstance(tag, value))
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Failed parsing " + tag + "," + (if (e.getMessage() == null) e.javaClass.getSimpleName() else e.getMessage()), e)
                }

            }
            return tlvs
        }

        private fun readUpToLength(data: ByteBuffer, length: Int): ByteArray {
            val value = ByteArray(if (length > data.remaining()) data.remaining() else length)
            data.get(value)
            return value
        }

        // Specification Update No. 69, 2009, Padding of BER-TLV Encoded Constructed Data Objects
        private fun isPaddingByte(tag: Tag): Boolean {
            return tag.bytes.size == 1 && tag.bytes[0] == 0.toByte()
        }

        private fun parseLength(data: ByteBuffer): Int {
            val lengthByte = data.get().toInt()
            var dataLength = 0
            if ((lengthByte and 0x80) == 0x80) {
                var numberOfBytesToEncodeLength = (lengthByte and 0x7F)
                while (numberOfBytesToEncodeLength > 0) {
                    dataLength += (data.get().toInt() and 0xFF)

                    if (numberOfBytesToEncodeLength > 1) {
                        dataLength *= 256
                    }
                    numberOfBytesToEncodeLength--
                }
            } else {
                dataLength = lengthByte
            }
            return dataLength
        }

        platformStatic public fun findTlv(tlvs: List<BerTlv>, tag: Tag): BerTlv? {
            for (tlv in tlvs) {
                if (tlv.tag == tag) {
                    return tlv
                }
            }
            return null
        }
    }

}
