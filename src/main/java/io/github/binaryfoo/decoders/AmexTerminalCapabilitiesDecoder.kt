package io.github.binaryfoo.decoders

// bit masked with 9F35 in the GPO R-APDU
class AmexTerminalCapabilitiesDecoder : EmvBitStringDecoder("fields/amex-terminal-capabilities.txt", false)
