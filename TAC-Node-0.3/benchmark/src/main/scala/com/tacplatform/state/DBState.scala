package com.tacplatform.state

import java.io.File

import com.tacplatform.Application
import com.tacplatform.account.AddressScheme
import com.tacplatform.common.state.ByteStr
import com.tacplatform.database.{LevelDBWriter, openDB}
import com.tacplatform.lang.directives.DirectiveSet
import com.tacplatform.settings.TacSettings
import com.tacplatform.transaction.smart.TacEnvironment
import com.tacplatform.utils.ScorexLogging
import monix.eval.Coeval
import org.iq80.leveldb.DB
import org.openjdk.jmh.annotations.{Param, Scope, State, TearDown}

@State(Scope.Benchmark)
abstract class DBState extends ScorexLogging {
  @Param(Array("tac.conf"))
  var configFile = ""

  lazy val settings: TacSettings = Application.loadApplicationConfig(Some(new File(configFile)).filter(_.exists()))

  lazy val db: DB = openDB(settings.dbSettings.directory)

  lazy val levelDBWriter: LevelDBWriter =
    LevelDBWriter.readOnly(
      db,
      settings.copy(dbSettings = settings.dbSettings.copy(maxCacheSize = 1))
    )

  AddressScheme.current = new AddressScheme { override val chainId: Byte = 'W' }

  lazy val environment = new TacEnvironment(
    AddressScheme.current.chainId,
    Coeval.raiseError(new NotImplementedError("`tx` is not implemented")),
    Coeval(levelDBWriter.height),
    levelDBWriter,
    null,
    DirectiveSet.contractDirectiveSet,
    ByteStr.empty
  )

  @TearDown
  def close(): Unit = {
    db.close()
  }
}
