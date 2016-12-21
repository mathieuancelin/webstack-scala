package org.reactivecouchbase.webstack.mvc

import org.reactivecouchbase.webstack.actions.Action
import org.reactivecouchbase.webstack.result.Results

trait Todo {
  val Todo = Action.sync { ctx =>
    Results.NotImplemented.text("Not Implemented Yet !!!")
  }
}

trait Controller extends Todo with Results {

}
