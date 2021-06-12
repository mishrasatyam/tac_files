package com.tacplatform.common
import java.util.concurrent.TimeUnit

import com.tacplatform.state.diffs.FeeValidation
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@Threads(4)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class SponsorshipMathBenchmark {
  @Benchmark
  def bigDecimal_test(bh: Blackhole): Unit = {
    def toTac(assetFee: Long, sponsorship: Long): Long = {
      val tac = (BigDecimal(assetFee) * BigDecimal(FeeValidation.FeeUnit)) / BigDecimal(sponsorship)
      if (tac > Long.MaxValue) {
        throw new java.lang.ArithmeticException("Overflow")
      }
      tac.toLong
    }

    bh.consume(toTac(100000, 100000000))
  }

  @Benchmark
  def bigInt_test(bh: Blackhole): Unit = {
    def toTac(assetFee: Long, sponsorship: Long): Long = {
      val tac = BigInt(assetFee) * FeeValidation.FeeUnit / sponsorship
      tac.bigInteger.longValueExact()
    }

    bh.consume(toTac(100000, 100000000))
  }
}
