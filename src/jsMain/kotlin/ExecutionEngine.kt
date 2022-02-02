/*
 *    Copyright 2022 Plank
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.plank.llvm4k

import org.plank.llvm4k.ir.Function

public actual class ExecutionEngine : Disposable {
  public actual fun runFunction(callee: Function, vararg args: GenericValue<*>): GenericValue<*> {
    TODO("Not yet implemented")
  }

  public actual fun runFunctionAsMain(callee: Function, args: Array<String>): Int {
    TODO("Not yet implemented")
  }

  override fun close() {
    TODO("Not yet implemented")
  }
}
