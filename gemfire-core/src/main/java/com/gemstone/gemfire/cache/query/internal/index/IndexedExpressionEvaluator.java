/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
/*
 * PathExpressionEvaluator.java
 *
 * Created on February 11, 2005, 4:25 PM
 */
package com.gemstone.gemfire.cache.query.internal.index;

import java.util.List;

import com.gemstone.gemfire.cache.query.internal.ExecutionContext;
import com.gemstone.gemfire.cache.query.types.ObjectType;
import com.gemstone.gemfire.internal.cache.RegionEntry;

/**
 * 
 * @author vaibhav
 */
public interface IndexedExpressionEvaluator {

  public String getIndexedExpression();

  public String getFromClause();

  public String getProjectionAttributes();

  /** @param add true if adding to index, false if removing */ 
  public void evaluate(RegionEntry target, boolean add) throws IMQException;

  public void initializeIndex() throws IMQException;

  public ObjectType getIndexResultSetType();
  
  public void expansion(List expandedResults, Object lowerBoundKey, Object upperBoundKey, int lowerBoundOperator, int upperBoundOperator, Object value) throws IMQException;

  public List getAllDependentIterators();
}
