package de.thatscalaguy.skunkcrypt

import skunk.Codec
import io.circe.{ Json => CJson, Encoder => CEncoder, Decoder => CDecoder }

private[skunkcrypt] trait Json {
    self: CryptCodecs =>
    def json[A: CEncoder: CDecoder]: Codec[A] = ???
    
    def json: Codec[CJson] = json[CJson]
}
