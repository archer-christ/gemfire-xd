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

package com.gemstone.gemfire.cache.query.internal;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.gemstone.gemfire.cache.query.FunctionDomainException;
import com.gemstone.gemfire.cache.query.NameResolutionException;
import com.gemstone.gemfire.cache.query.QueryInvalidException;
import com.gemstone.gemfire.cache.query.QueryInvocationTargetException;
import com.gemstone.gemfire.cache.query.TypeMismatchException;
import com.gemstone.gemfire.cache.query.internal.parse.GemFireAST;
import com.gemstone.gemfire.cache.query.internal.parse.OQLLexer;
import com.gemstone.gemfire.cache.query.internal.parse.OQLLexerTokenTypes;
import com.gemstone.gemfire.cache.query.internal.parse.OQLParser;
import com.gemstone.gemfire.cache.query.internal.types.CollectionTypeImpl;
import com.gemstone.gemfire.cache.query.internal.types.MapTypeImpl;
import com.gemstone.gemfire.cache.query.internal.types.ObjectTypeImpl;
import com.gemstone.gemfire.cache.query.internal.types.TypeUtils;
import com.gemstone.gemfire.cache.query.types.CollectionType;
import com.gemstone.gemfire.cache.query.types.MapType;
import com.gemstone.gemfire.cache.query.types.ObjectType;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.Assert;
import com.gemstone.gemfire.internal.ClassPathLoader;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.LocalLogWriter;
import com.gemstone.gemfire.internal.LogWriterImpl;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

/**
 * Class Description
 *
 * @version     $Revision: 1.1 $s
 * @author      ericz
 * @author asif
 */
public class QCompiler implements OQLLexerTokenTypes {
  private Stack stack = new Stack ();
  private LogWriterI18n logger = new LocalLogWriter (LogWriterImpl.INFO_LEVEL);
  private Map imports = new HashMap ();
  final private boolean isForIndexCompilation;
  private boolean traceOn;

  public QCompiler(LogWriterI18n logger) {
    if (logger != null) this.logger = logger;
    this.isForIndexCompilation = false;
  }

  public QCompiler(LogWriterI18n logger, boolean isForIndexCompilation) {
    if (logger != null) this.logger = logger;
    this.isForIndexCompilation = isForIndexCompilation;
  }
    
  /* compile the string into a Query (returns the root CompiledValue)
   */
  public CompiledValue compileQuery(String oqlSource) {  
    try {
      OQLLexer lexer = new OQLLexer (new StringReader (oqlSource));
      OQLParser parser = new OQLParser (lexer);
      // by default use Unsupported AST class, overridden for supported
      // operators in the grammer proper
      parser.setASTNodeClass ("com.gemstone.gemfire.cache.query.internal.parse.ASTUnsupported");
      parser.queryProgram ();
      GemFireAST n = (GemFireAST)parser.getAST ();    
      n.compile(this);
    } catch (Exception ex){ // This is to make sure that we are wrapping any antlr exception with GemFire Exception. 
      throw new QueryInvalidException(LocalizedStrings.QCompiler_SYNTAX_ERROR_IN_QUERY_0.toLocalizedString(ex.getMessage()), ex);
    }
    Assert.assertTrue (stackSize () == 1, "stack size = " + stackSize ());
    return (CompiledValue)pop ();
  }
  
  /** Returns List<CompiledIteratorDef> */
  public List compileFromClause(String fromClause) {
    try {
      OQLLexer lexer = new OQLLexer (new StringReader (fromClause));
      OQLParser parser = new OQLParser (lexer);
      // by default use Unsupported AST class, overridden for supported
      // operators in the grammer proper
      parser.setASTNodeClass ("com.gemstone.gemfire.cache.query.internal.parse.ASTUnsupported");
      parser.loneFromClause ();
      GemFireAST n = (GemFireAST)parser.getAST ();
      n.compile(this);
    } catch (Exception ex){ // This is to make sure that we are wrapping any antlr exception with GemFire Exception. 
      throw new QueryInvalidException(LocalizedStrings.QCompiler_SYNTAX_ERROR_IN_QUERY_0.toLocalizedString(ex.getMessage()), ex);
    }
    Assert.assertTrue (stackSize () == 1, "stack size = " + stackSize ());
    return (List)pop ();
  }

  
  /** Returns List<CompiledIteratorDef> or null if projectionAttrs is '*' */
  public List compileProjectionAttributes(String projectionAttributes) {
    try {
      OQLLexer lexer = new OQLLexer (new StringReader (projectionAttributes));
      OQLParser parser = new OQLParser (lexer);
      // by default use Unsupported AST class, overridden for supported
      // operators in the grammer proper
      parser.setASTNodeClass ("com.gemstone.gemfire.cache.query.internal.parse.ASTUnsupported");
      parser.loneProjectionAttributes ();
      GemFireAST n = (GemFireAST)parser.getAST ();
      // don't compile TOK_STAR
      if (n.getType () == TOK_STAR) {
        return null;
      }
      n.compile(this);
    } catch (Exception ex){ // This is to make sure that we are wrapping any antlr exception with GemFire Exception. 
      throw new QueryInvalidException(LocalizedStrings.QCompiler_SYNTAX_ERROR_IN_QUERY_0.toLocalizedString(ex.getMessage()), ex);
    }
    Assert.assertTrue(stackSize () == 1,
        "stack size = " + stackSize () +
        ";stack=" + this.stack);
    return (List)pop ();
  }
  
  /**
   * Yogesh: compiles order by clause and push into the stack  
   * @param numOfChildren
   */
  public void compileOrederByClause(int numOfChildren) {
  	List list = new ArrayList(); 
  	for (int i = 0; i < numOfChildren; i++) {
  	  CompiledSortCriterion csc = (CompiledSortCriterion)this.stack.pop();
  	  list.add(0, csc);
  	}
  	push(list) ;
  }
  /**
   * Yogesh: compiles sort criteria present in order by clause and push into the stack
   * @param sortCriterion
   */
  public void compileSortCriteria(String sortCriterion) {
  	
  	CompiledValue obj = (CompiledValue)this.stack.pop();
  	boolean criterion = false;
  	if (sortCriterion.equals("desc")) 
  	  criterion = true; 	
  	CompiledSortCriterion csc = new CompiledSortCriterion(criterion, obj);
  	push(csc);
    
  }
  
  public void compileLimit(String limitNum) {  
    push(Integer.valueOf(limitNum));
  }
  
  /** Processes import statements only. This compiler instance remembers the imports
   *  and can be used to compile other strings with this context info
   */
  public void compileImports(String imports) {
    try {
      OQLLexer lexer = new OQLLexer (new StringReader (imports));
      OQLParser parser = new OQLParser (lexer);
      // by default use Unsupported AST class, overridden for supported
      // operators in the grammer proper
      parser.setASTNodeClass ("com.gemstone.gemfire.cache.query.internal.parse.ASTUnsupported");
      parser.loneImports();
      GemFireAST n = (GemFireAST)parser.getAST ();
      n.compile(this);
    } catch (Exception ex){ // This is to make sure that we are wrapping any antlr exception with GemFire Exception. 
      throw new QueryInvalidException(LocalizedStrings.QCompiler_SYNTAX_ERROR_IN_QUERY_0.toLocalizedString(ex.getMessage()), ex);
    }
    Assert.assertTrue(stackSize() == 0,
        "stack size = " + stackSize() +
        ";stack=" + this.stack);
  }
  
  public void select() {
    // List of orderBy sortCriteria
    Object limitObject = pop();
    CompiledValue limit;
    if (limitObject instanceof Integer) {
      limit = new CompiledLiteral(limitObject);
    }
    else {
      limit = (CompiledBindArgument) limitObject;
    }
    List orderByAttrs = (List)pop();
    // whereClause
    CompiledValue where = (CompiledValue)pop();
    // fromClause: list of CompiledIteratorDefs
    List iterators = (List)pop();
    // pop the projection attributes
    List projAttrs = (List)pop();
    // "COUNT" or null
    String aggrExpr = (String)pop();
    // "DISTINCT" or null
    String distinct = (String)pop();
    CompiledSelect select = new CompiledSelect(distinct != null, aggrExpr != null, where,
        iterators, projAttrs, orderByAttrs, limit);
    push(select);
  }
  
  public void projection () {
    // find an id or null on the stack, then an expr CompiledValue
    // push an Object[2] on the stack. First element is id, second is CompiledValue
    CompiledID id = (CompiledID)pop ();
    CompiledValue expr = (CompiledValue)pop ();
    push (new Object[] {id == null ? null : id.getId (), expr});
  }
  
  public void iteratorDef () {
    // find type id  and colln on the stack
    
    ObjectType type = assembleType(); // can be null    
    CompiledID id = (CompiledID)TypeUtils.checkCast(pop(), CompiledID.class); // can be null
    CompiledValue colln = (CompiledValue)TypeUtils.checkCast(pop(), CompiledValue.class);
    
    if (type == null) {
      type = TypeUtils.OBJECT_TYPE;
    }
    
    push (new CompiledIteratorDef (id == null ? null : id.getId (),
                                   type, colln));
  }
  
  
  public void undefinedExpr (boolean is_defined) {
    CompiledValue value = (CompiledValue)pop ();
    push (new CompiledUndefined (value, is_defined));
  }
  
  public void function (int function, int numOfChildren) {
    CompiledValue [] cvArr = new CompiledValue[numOfChildren];
    for(int i = numOfChildren - 1; i >= 0; i-- ) {
      cvArr[i] = (CompiledValue) pop(); 
    }
    push(new CompiledFunction(cvArr, function));
  }
  
  public void inExpr () {
    CompiledValue collnExpr = (CompiledValue)TypeUtils.checkCast (pop (), CompiledValue.class);
    CompiledValue elm = (CompiledValue)TypeUtils.checkCast (pop (), CompiledValue.class);
    push (new CompiledIn (elm, collnExpr));
  }
  
  public void constructObject (Class clazz) {
    // find argList on stack
    // only support SET for now
    Assert.assertTrue (clazz == ResultsSet.class);
    List argList = (List)TypeUtils.checkCast (pop (), List.class);
    push (new CompiledConstruction (clazz, argList));
  }
  
  public void pushId (String id) {
    push (new CompiledID (id));
  }
  
  public void pushRegion (String regionPath) {
    push (new CompiledRegion (regionPath));
  }
  
  public void appendPathComponent (String id) {
    CompiledValue rcvr = (CompiledValue)pop ();
    push (new CompiledPath (rcvr, id));
  }
  
  
  public void pushBindArgument (int i) {
    push (new CompiledBindArgument (i));
  }
  
  
  public void pushLiteral (Object obj) {
    push (new CompiledLiteral (obj));
  }
  
  public void pushNull () // used as a placeholder for a missing clause
  {
    push (null);
  }
  
  
  public void combine (int num) {
    List list = new ArrayList ();
    for (int i =0; i < num; i++) {
      list.add (0,pop ());
    }
    push (list);
  }
  
  
  public void methodInvocation () {
    // find on stack:
    // argList, methodName, receiver (which may be null if receiver is implicit)
    List argList = (List)TypeUtils.checkCast (pop (), List.class);
    CompiledID methodName = (CompiledID)TypeUtils.checkCast (pop (), CompiledID.class);
    CompiledValue rcvr = (CompiledValue)TypeUtils.checkCast (pop (), CompiledValue.class);
    push (new CompiledOperation (rcvr, methodName.getId (), argList));
  }
  
  public void indexOp()
  {
    // find the List of index expressions and the receiver on the stack
    Object indexParams = pop();
    final CompiledValue rcvr = (CompiledValue)TypeUtils.checkCast(pop(),
        CompiledValue.class);
    CompiledValue indexExpr = CompiledValue.MAP_INDEX_ALL_KEYS;

    if (indexParams != null) {
      final List indexList = (List)TypeUtils.checkCast(indexParams, List.class);
      if (!isForIndexCompilation && indexList.size() != 1) {
        throw new UnsupportedOperationException(
            LocalizedStrings.QCompiler_ONLY_ONE_INDEX_EXPRESSION_SUPPORTED
                .toLocalizedString());
      }
      if (indexList.size() == 1) {
        indexExpr = (CompiledValue)TypeUtils.checkCast(indexList.get(0),
            CompiledValue.class);

        if (indexExpr.getType() == TOK_COLON) {
          throw new UnsupportedOperationException(
              LocalizedStrings.QCompiler_RANGES_NOT_SUPPORTED_IN_INDEX_OPERATORS
                  .toLocalizedString());
        }
        indexExpr = (CompiledValue)TypeUtils.checkCast(indexList.get(0),
            CompiledValue.class);
        push(new CompiledIndexOperation(rcvr, indexExpr));
      }
      else {
        assert this.isForIndexCompilation;
        
        MapIndexable mi = new MapIndexOperation(rcvr, indexList);
        push(mi);
      }    
    }else {
      if(!this.isForIndexCompilation) {
        throw new QueryInvalidException(
            LocalizedStrings.QCompiler_SYNTAX_ERROR_IN_QUERY_0.toLocalizedString("* use incorrect")); 
      }
      push(new CompiledIndexOperation(rcvr, indexExpr));
    }

  }
  
 

  /**
   * Creates appropriate CompiledValue for the like predicate based on the
   * sargability of the String or otherwise. It also works on the last character
   * to see if the sargable like predicate results in a CompiledJunction or a
   * Comparison. Currently we are supporting only the '%' terminated "like"
   * predicate.
   * 
   * @param var
   *                The CompiledValue representing the variable
   * @param patternOrBindParam
   *                The CompiledLiteral reprsenting the pattern of the like
   *                predicate
   * @return CompiledValue representing the "like" predicate
   *
   */
  CompiledValue createCompiledValueForLikePredicate(CompiledValue var,
      CompiledValue patternOrBindParam) {
    if(!(patternOrBindParam.getType() == CompiledBindArgument.QUERY_PARAM)) {
      CompiledLiteral pattern = (CompiledLiteral)patternOrBindParam;
      if (pattern._obj == null) {
        throw new UnsupportedOperationException("Null values are not supported with LIKE predicate.");
      }
    }
    // From 6.6 Like is enhanced to support special character (% and _) at any 
    // position of the string.
    return new CompiledLike(var,patternOrBindParam);
  }  
  

  public void like() {
    CompiledValue v2 = (CompiledValue)pop();
    CompiledValue v1 = (CompiledValue)pop();
    CompiledValue cv = createCompiledValueForLikePredicate(v1, v2);
    push(cv);
  }
  
  
  
  public void compare (int opKind) {
    CompiledValue v2 = (CompiledValue)pop ();
    CompiledValue v1 = (CompiledValue)pop ();
    push (new CompiledComparison (v1, v2, opKind));
  }
  
  public void or (int numTerms) {
    junction (numTerms, LITERAL_or);
  }
  
  public void and (int numTerms) {
    junction (numTerms, LITERAL_and);
  }
  
  private void junction (int numTerms, int operator) {
            /* if any of the operands are junctions with same operator as this one
             * then flatten */
    List operands = new ArrayList (numTerms);
    for (int i = 0; i < numTerms; i++) {
      CompiledValue operand = (CompiledValue)pop ();
      // flatten if we can
      if (operand instanceof CompiledJunction &&
              ((CompiledJunction)operand).getOperator () == operator) {
        CompiledJunction junction = (CompiledJunction)operand;
        List jOperands = junction.getOperands ();
        for (int j = 0; j < jOperands.size (); j++)
          operands.add (jOperands.get (j));
      } else
        operands.add (operand);
    }
    
    push (new CompiledJunction (
            (CompiledValue[])operands.toArray (new CompiledValue[operands.size ()]),
            operator));
  }
  
  
  
  public void not () {
    Object obj = this.stack.peek ();
    Assert.assertTrue (obj instanceof CompiledValue);
    
    if (obj instanceof Negatable)
      ((Negatable)obj).negate ();
    else
      push(new CompiledNegation ((CompiledValue)pop ()));
  }
  
  public void unaryMinus() {
    Object obj = this.stack.peek ();
    Assert.assertTrue (obj instanceof CompiledValue);
    push(new CompiledUnaryMinus ((CompiledValue)pop ()));

  } 
  
  public void typecast() {
    // pop expr and type, apply type, then push result
    AbstractCompiledValue cmpVal = (AbstractCompiledValue)
        TypeUtils.checkCast(pop(), AbstractCompiledValue.class);
    ObjectType objType = assembleType();
    cmpVal.setTypecast(objType);
    push(cmpVal);
  }
  
  
  // returns null if null is on the stack
  public ObjectType assembleType() {
    ObjectType objType = (ObjectType)TypeUtils.checkCast(pop(), ObjectType.class);
    if (objType instanceof CollectionType) {
      // pop the elementType
      ObjectType elementType = assembleType();
      
      if (objType instanceof MapType) {
        //pop the key type
        ObjectType keyType = assembleType();
        return new MapTypeImpl(objType.resolveClass(), keyType, elementType);
      }
      return new CollectionTypeImpl(objType.resolveClass(), elementType);
    }
    return objType;
  }
  
  public void traceRequest() {
    this.traceOn = true;
  }

  
  public boolean isTraceRequested() {
    return traceOn;
  }

  public void importName (String qualifiedName, String asName) {
    if (asName == null) {
      // if no AS, then use the short name from qualifiedName
      // as the AS
      int idx = qualifiedName.lastIndexOf ('.');
      if (idx >= 0) {
        asName = qualifiedName.substring (idx + 1);
      } else {
        asName = qualifiedName;
      }
    }
    if (this.logger.finerEnabled ()) {
      this.logger.finer ("QCompiler.importName: " + asName + "," + qualifiedName);
    }
    this.imports.put(asName, qualifiedName);
  }
  
  
  public Object pop () {
    Object obj = this.stack.pop ();
    if (this.logger.finerEnabled ()) {
      this.logger.finer ("QCompiler.pop: " + obj);
    }
    return obj;
  }
  
  public void push (Object obj) {
    if (this.logger.finerEnabled ()) {
      this.logger.finer ("QCompiler.push: " + obj);
    }
    this.stack.push (obj);
  }
  
  public int stackSize () {
    return this.stack.size ();
  }
  
  public LogWriterI18n getLogger () {
    return this.logger;
  }
  
  public ObjectType resolveType (String typeName) {
    if (typeName == null) {
      if (this.logger.finerEnabled ()) {
        this.logger.finer ("QCompiler.resolveType= " + Object.class.getName ());
      }
      return TypeUtils.OBJECT_TYPE;
    }
    // resolve with imports
    String as = null;
    if (this.imports != null) {
      as = (String)this.imports.get (typeName);
    }
    if (as != null) typeName = as;
    
    Class resultClass;
    try {
      resultClass = InternalDataSerializer.getCachedClass(typeName);
    } catch (ClassNotFoundException e) {
      throw new QueryInvalidException(LocalizedStrings.QCompiler_TYPE_NOT_FOUND_0.toLocalizedString(typeName), e);
    }
    if (this.logger.finerEnabled ()) {
      this.logger.finer ("QCompiler.resolveType= " + resultClass.getName ());
    }
    return new ObjectTypeImpl(resultClass);
  }
  private static class MapIndexOperation extends AbstractCompiledValue implements MapIndexable {
    private final CompiledValue rcvr;
    private final List<CompiledValue> indexList;
    public MapIndexOperation (CompiledValue rcvr, List<CompiledValue> indexList ) {
     this.rcvr = rcvr;
     this.indexList = indexList;
    }
    public CompiledValue getRecieverSansIndexArgs()
    {
      return rcvr; 
    }         
    
    public CompiledValue getMapLookupKey()
    {
       throw new UnsupportedOperationException("Function invocation not expected");
    }
              
    public List<CompiledValue> getIndexingKeys()
    {            
      return (List<CompiledValue>)indexList;
    }
    
    public Object evaluate(ExecutionContext context)
        throws FunctionDomainException, TypeMismatchException,
        NameResolutionException, QueryInvocationTargetException
    {
      throw new UnsupportedOperationException("Method execution not expected");
    }
    
    public int getType()
    {
      throw new UnsupportedOperationException("Method execution not expected");
    }
  }
  
}

