/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources

import org.apache.spark.sql.catalyst.InternalRow


/**
 * To be implemented by classes that represent data statistics collected during a Write Task.
 * It is important that instances of this type are [[Serializable]], as they will be gathered
 * on the driver from all executors.
 */
trait WriteTaskStats
  extends Serializable

/**
 * A trait for classes that are capable of collecting statistics on data that's being processed by
 * a single write task in [[FileFormatWriter]] - i.e. there should be one instance per executor.
 */
trait WriteTaskStatsTracker {
  /**
   * Process the fact that a new file is about to be written.
   * @param filePath Path of the file into which future rows will be written.
   */
  def newFile(filePath: String): Unit

  /**
   * Use the given `row` to update the tracked statistics accordingly.
   * @note The row will be written to the file most recently specified via `setFile()`.
   * @param row Current data row to be processed.
   */
  def updateStats(row: InternalRow): Unit

  /**
   * Returns the final statistics computed so far.
   * @note This may only be called once. Further use of the object may lead to undefined behavior.
   * @return An object of subtype of [[WriteTaskStats]], to be sent to the driver.
   */
  def getFinalStats(): WriteTaskStats
}

/**
 * A class implementing this trait is basically a collection of parameters that are necessary
 * for instantiating a (derived type of) [[WriteTaskStatsTracker]] on all executors and then
 * process the statistics produced by them (e.g. save them to memory/disk, issue warnings, etc).
 * It is therefore important that such an objects is [[Serializable]], as it will be sent
 * from the driver to all executors.
 */
trait WriteJobStatsTracker
  extends Serializable {

  /**
   * Instantiates a [[WriteTaskStatsTracker]], based on (non-transient) members of this class.
   * To be called by executors.
   * @return A [[WriteTaskStatsTracker]] instance to be used for computing stats during a write task
   */
  def newTaskInstance(): WriteTaskStatsTracker

  /**
   * Process the given collection of stats computed during this job.
   * E.g. aggregate them, write them to memory / disk, issue warnings, whatever.
   * @param stats One [[WriteTaskStats]] object from each successful write task.
   *              @note The type here is too generic. These classes should probably be parametrized:
   *                WriteTaskStatsTracker[S <: WriteTaskStats]
   *                WriteJobStatsTracker[S <: WriteTaskStats, T <: WriteTaskStatsTracker[S]]
   *              and this would then be:
   *                def processStats(stats: Seq[S]): Unit
   *              but that leads to too much complication in the [[FileFormatWriter]].
   *              Instead, you may feel free to cast the `stats` to the expected derived type
   *              when implementing this method in a derived classes.
   *              The framework will make sure to call this with the right arguments.
   */
  def processStats(stats: Seq[WriteTaskStats]): Unit
}
