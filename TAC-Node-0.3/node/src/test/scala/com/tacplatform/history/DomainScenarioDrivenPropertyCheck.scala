package com.tacplatform.history

import com.tacplatform.db.WithDomain
import com.tacplatform.settings.TacSettings
import org.scalacheck.Gen
import org.scalatest.Suite
import org.scalatestplus.scalacheck.{ScalaCheckDrivenPropertyChecks => GeneratorDrivenPropertyChecks}

trait DomainScenarioDrivenPropertyCheck extends WithDomain { _: Suite with GeneratorDrivenPropertyChecks =>
  def scenario[S](gen: Gen[S], bs: TacSettings = DefaultTacSettings)(assertion: (Domain, S) => Any): Any =
    forAll(gen) { s =>
      withDomain(bs) { domain =>
        assertion(domain, s)
      }
    }
}
