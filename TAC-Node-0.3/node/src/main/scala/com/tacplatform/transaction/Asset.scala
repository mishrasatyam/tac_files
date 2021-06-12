package com.tacplatform.transaction

import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.Base58
import com.tacplatform.transaction.assets.exchange.AssetPair
import net.ceedubs.ficus.readers.ValueReader
import play.api.libs.json._

import scala.util.Success

sealed trait Asset
object Asset {
  final case class IssuedAsset(id: ByteStr) extends Asset {
    override def toString: String = id.toString
  }
  case object Tac extends Asset

  implicit val assetReads: Reads[IssuedAsset] = Reads {
    case JsString(str) if str.length > AssetIdStringLength =>
      JsError(s"Too long assetId: length of $str exceeds $AssetIdStringLength")
    case JsString(str) =>
      Base58.tryDecodeWithLimit(str) match {
        case Success(arr) => JsSuccess(IssuedAsset(ByteStr(arr)))
        case _            => JsError("Expected base58-encoded assetId")
      }
    case _ => JsError("Expected base58-encoded assetId")
  }
  implicit val assetWrites: Writes[IssuedAsset] = Writes { asset =>
    JsString(asset.id.toString)
  }

  implicit val assetIdReads: Reads[Asset] = Reads {
    case json: JsString => if (json.value.isEmpty) JsSuccess(Tac) else assetReads.reads(json)
    case JsNull         => JsSuccess(Tac)
    case _              => JsError("Expected base58-encoded assetId or null")
  }
  implicit val assetIdWrites: Writes[Asset] = Writes {
    case Tac           => JsNull
    case IssuedAsset(id) => JsString(id.toString)
  }

  object Formats {
    implicit val assetJsonFormat: Format[IssuedAsset] = Format(assetReads, assetWrites)
    implicit val assetIdJsonFormat: Format[Asset]     = Format(assetIdReads, assetIdWrites)
  }

  implicit val assetReader: ValueReader[Asset] = { (cfg, path) =>
    AssetPair.extractAssetId(cfg getString path).fold(ex => throw new Exception(ex.getMessage), identity)
  }

  def fromString(maybeStr: Option[String]): Asset = {
    maybeStr.map(x => IssuedAsset(ByteStr.decodeBase58(x).get)).getOrElse(Tac)
  }

  def fromCompatId(maybeBStr: Option[ByteStr]): Asset = {
    maybeBStr.fold[Asset](Tac)(IssuedAsset)
  }

  implicit class AssetIdOps(private val ai: Asset) extends AnyVal {
    def byteRepr: Array[Byte] = ai match {
      case Tac           => Array(0: Byte)
      case IssuedAsset(id) => (1: Byte) +: id.arr
    }

    def compatId: Option[ByteStr] = ai match {
      case Tac           => None
      case IssuedAsset(id) => Some(id)
    }

    def maybeBase58Repr: Option[String] = ai match {
      case Tac           => None
      case IssuedAsset(id) => Some(id.toString)
    }

    def fold[A](onTac: => A)(onAsset: IssuedAsset => A): A = ai match {
      case Tac                  => onTac
      case asset @ IssuedAsset(_) => onAsset(asset)
    }
  }
}
