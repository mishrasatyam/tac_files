package com.tacplatform.transaction

import com.tacplatform.TransactionGen
import com.tacplatform.account.{Alias, KeyPair, PublicKey}
import com.tacplatform.common.state.ByteStr
import com.tacplatform.common.utils.EitherExt2
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import play.api.libs.json.Json

class CreateAliasTransactionSpecification extends PropSpec with PropertyChecks with Matchers with TransactionGen {

  property("CreateAliasTransaction serialization roundtrip") {
    forAll(createAliasGen) { tx: CreateAliasTransaction =>
      val recovered = tx.builder.parseBytes(tx.bytes()).get
      recovered shouldEqual tx
    }
  }

  property("CreateAliasTransaction serialization from TypedTransaction") {
    forAll(createAliasGen) { tx: CreateAliasTransaction =>
      val recovered = TransactionParsers.parseBytes(tx.bytes()).get
      recovered shouldEqual tx
    }
  }

  property("The same aliases from different senders have the same id") {
    forAll(accountGen, accountGen, aliasGen, timestampGen) {
      case (a1: KeyPair, a2: KeyPair, a: Alias, t: Long) =>
        val tx1 = CreateAliasTransaction.selfSigned(1.toByte, a1, a, MinIssueFee, t).explicitGet()
        val tx2 = CreateAliasTransaction.selfSigned(1.toByte, a2, a, MinIssueFee, t).explicitGet()
        tx1.id() shouldBe tx2.id()
    }
  }

  property("JSON format validation for CreateAliasTransactionV1") {
    val js = Json.parse("""{
                         "type": 10,
                         "id": "7acjQQWJAharrgzb4Z6jo3eeAKAGPmLkHTPtvBTKaiug",
                         "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
                         "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
                         "fee": 100000,
                         "feeAssetId": null,
                         "timestamp": 1526910778245,
                         "signature": "CC1jQ4qkuVfMvB2Kpg2Go6QKXJxUFC8UUswUxBsxwisrR8N5s3Yc8zA6dhjTwfWKfdouSTAnRXCxTXb3T6pJq3T",
                         "proofs": ["CC1jQ4qkuVfMvB2Kpg2Go6QKXJxUFC8UUswUxBsxwisrR8N5s3Yc8zA6dhjTwfWKfdouSTAnRXCxTXb3T6pJq3T"],
                         "version": 1,
                         "alias": "myalias"
                        }
    """)

    val tx = CreateAliasTransaction
      .create(Transaction.V1, PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(), "myalias", 100000, 1526910778245L, Proofs(ByteStr.decodeBase58("CC1jQ4qkuVfMvB2Kpg2Go6QKXJxUFC8UUswUxBsxwisrR8N5s3Yc8zA6dhjTwfWKfdouSTAnRXCxTXb3T6pJq3T").get))
      .explicitGet()

    js shouldEqual tx.json()
  }

  property("JSON format validation for CreateAliasTransactionV2") {
    val js = Json.parse("""{
                       "type": 10,
                       "id": "7acjQQWJAharrgzb4Z6jo3eeAKAGPmLkHTPtvBTKaiug",
                       "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
                       "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
                       "fee": 100000,
                       "feeAssetId": null,
                       "timestamp": 1526910778245,
                       "proofs": [
                       "26U7rQTwpdma5GYSZb5bNygVCtSuWL6DKet1Nauf5J57v19mmfnq434YrkKYJqvYt2ydQBUT3P7Xgj5ZVDVAcc5k"
                       ],
                       "version": 2,
                       "alias": "myalias"
                        }
    """)

    val tx = CreateAliasTransaction
      .create(Transaction.V2, PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(), "myalias", 100000, 1526910778245L, Proofs(Seq(ByteStr.decodeBase58("26U7rQTwpdma5GYSZb5bNygVCtSuWL6DKet1Nauf5J57v19mmfnq434YrkKYJqvYt2ydQBUT3P7Xgj5ZVDVAcc5k").get)))
      .explicitGet()

    js shouldEqual tx.json()
  }

}
