package com.tacplatform.generator.utils

import com.tacplatform.generator.Preconditions.CreatedAccount
import com.tacplatform.transaction.assets.IssueTransaction
import com.tacplatform.transaction.lease.LeaseTransaction

object Universe {
  @volatile var Accounts: List[CreatedAccount]       = Nil
  @volatile var IssuedAssets: List[IssueTransaction] = Nil
  @volatile var Leases: List[LeaseTransaction]       = Nil
}
