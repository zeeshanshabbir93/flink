/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.plan.rules.datastream

import org.apache.calcite.plan.{RelOptRule, RelOptRuleCall, RelTraitSet}
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.convert.ConverterRule
import org.apache.flink.table.api.TableException
import org.apache.flink.table.plan.nodes.FlinkConventions
import org.apache.flink.table.plan.nodes.datastream.DataStreamGroupWindowTableAggregate
import org.apache.flink.table.plan.nodes.logical.FlinkLogicalWindowTableAggregate
import org.apache.flink.table.plan.schema.RowSchema

import scala.collection.JavaConversions._

class DataStreamGroupWindowTableAggregateRule
  extends ConverterRule(
    classOf[FlinkLogicalWindowTableAggregate],
    FlinkConventions.LOGICAL,
    FlinkConventions.DATASTREAM,
    "DataStreamGroupWindowTableAggregateRule") {

  override def matches(call: RelOptRuleCall): Boolean = {
    val agg: FlinkLogicalWindowTableAggregate =
      call.rel(0).asInstanceOf[FlinkLogicalWindowTableAggregate]

    // check if we have grouping sets
    val groupSets = agg.getGroupSets.size() != 1 || agg.getGroupSets.get(0) != agg.getGroupSet
    if (groupSets || agg.getIndicator) {
      throw new TableException("GROUPING SETS are currently not supported.")
    }

    !groupSets && !agg.getIndicator
  }

  override def convert(rel: RelNode): RelNode = {
    val agg: FlinkLogicalWindowTableAggregate = rel.asInstanceOf[FlinkLogicalWindowTableAggregate]
    val traitSet: RelTraitSet = rel.getTraitSet.replace(FlinkConventions.DATASTREAM)
    val convInput: RelNode = RelOptRule.convert(agg.getInput, FlinkConventions.DATASTREAM)

    new DataStreamGroupWindowTableAggregate(
      agg.getWindow,
      agg.getNamedProperties,
      rel.getCluster,
      traitSet,
      convInput,
      agg.getNamedAggCalls,
      new RowSchema(rel.getRowType),
      new RowSchema(agg.getInput.getRowType),
      agg.getGroupSet.toArray)
  }
}

object DataStreamGroupWindowTableAggregateRule {
  val INSTANCE: RelOptRule = new DataStreamGroupWindowTableAggregateRule
}