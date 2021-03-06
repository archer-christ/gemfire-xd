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
package com.pivotal.gemfirexd.derbylang;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.derbyTesting.junit.JDBC;

import com.pivotal.gemfirexd.DistributedSQLTestBase;
import com.pivotal.gemfirexd.TestUtil;

public class LangScripts_IntersectDUnit extends DistributedSQLTestBase {

	public LangScripts_IntersectDUnit(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}
	
	  public void testLangScript_IntersectTest() throws Exception
	  {
	    // This is a JUnit conversion of the Derby Lang Intersect.sql script
	    // without any GemFireXD extensions
		  
	    // Although this tests uses ORDER BY, the unit test does not take order into account
	    //   when checking results...

	    // Catch exceptions from illegal syntax
	    // Tests still not fixed marked FIXME
		  
	    // Array of SQL text to execute and sqlstates to expect
	    // The first object is a String, the second is either 
	    // 1) null - this means query returns no rows and throws no exceptions
	    // 2) a string - this means query returns no rows and throws expected SQLSTATE
	    // 3) a String array - this means query returns rows which must match (unordered) given resultset
	    //       - for an empty result set, an uninitialized size [0][0] array is used
	    Object[][] Script_IntersectUT = {
		{ "create table t1( id integer not null primary key, i1 integer, i2 integer, c10 char(10), c30 char(30), tm time)", null },
		{ "create table t2( id integer not null primary key, i1 integer, i2 integer, vc20 varchar(20), d double, dt date)", null },
		{ "insert into t1(id,i1,i2,c10,c30) values  (1,1,1,'a','123456789012345678901234567890'),  (2,1,2,'a','bb'),  (3,1,3,'b','bb'),  (4,1,3,'zz','5'),  (5,null,null,null,'1.0'),  (6,null,null,null,'a')", null },
		{ "insert into t2(id,i1,i2,vc20,d) values (1,1,1,'a',1.0),  (2,1,2,'a',1.1), (5,null,null,'12345678901234567890',3), (100,1,3,'zz',3),  (101,1,2,'bb',null),  (102,5,5,'',null),  (103,1,3,' a',null),  (104,1,3,'null',7.4)", null },
		// FIXME
		// WIth the ORDER by DESC in these queries, an assertion 'results are in out of order' thrown by Gemfire layer
		// Remove the DESC for now, replace when assertion is fixed
		//{ "select id,i1,i2 from t1 intersect select id,i1,i2 from t2 order by id DESC,i1,i2", new String[][] {
		{ "select id,i1,i2 from t1 intersect select id,i1,i2 from t2 order by id,i1,i2", new String[][] {
			{"5",null,null},
			{"2","1","2"},
			{"1","1","1"} } },
		{ "select id,i1,i2 from t1 intersect distinct select id,i1,i2 from t2 order by id,i1,i2", new String[][] {
			{"5",null,null},
			{"2","1","2"},
			{"1","1","1"} } },
		{ "select id,i1,i2 from t1 intersect all select id,i1,i2 from t2 order by 1,2,3", new String[][] {
			{"1","1","1"},
			{"2","1","2"},
			{"5",null,null} } },
		{ "select id,i1,i2 from t1 intersect select id,i1,i2 from t2 order by i2, id", new String[][] {
			{"1","1","1"},
			{"2","1","2"},
			{"5",null,null} } },
		{ "select id,i1,i2 from t1 intersect all select id,i1,i2 from t2 order by 3, 1", new String[][] {
			{"5",null,null},
			{"2","1","2"},
			{"1","1","1"} } },
		{ "select i1,i2 from t1 intersect select i1,i2 from t2 order by 1,2", new String[][] {
			{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		{ "select i1,i2 from t1 intersect distinct select i1,i2 from t2 order by 1,2", new String[][] {
			{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		{ "select i1,i2 from t1 intersect all select i1,i2 from t2 order by 1,2", new String[][] {
			{"1","1"},{"1","2"},{"1","3"},{"1","3"},{null,null} } },
		{ "select i1,i2 from t1 intersect select i1,i2 from t2 where id = -1", new String[0][0] },
		{ "select i1,i2 from t1 intersect all select i1,i2 from t2 where id = -1", new String[0][0] },
		{ "select i1,i2 from t1 where id = -1 intersect all select i1,i2 from t2", new String[0][0] },
		// FIXME
		// Nested set operations over partitioned tables not supported for SQLfire now
		//{ "select i1,i2 from t1 intersect all select i1,i2 from t2 intersect values(5,5),(1,3) order by 1,2", new String[][] { {"1","3"} } },
		//{ "(select i1,i2 from t1 intersect all select i1,i2 from t2) intersect values(5,5),(1,3) order by 1,2", new String[][] { {"1","3"} } },
		//{ "values(-1,-1,-1) union select id,i1,i2 from t1 intersect select id,i1,i2 from t2 order by 1,2,3", new String[][] {
		//	{"-1","-1","-1"},
		//	{"1","1","1"},
		//	{"2","1","2"},
		//	{"5",null,null} } },
		//{ "select id,i1,i2 from t1 intersect select id,i1,i2 from t2 union values(-1,-1,-1) order by 1,2,3", new String[][] {
		//	{"-1","-1","-1"},
		//	{"1","1","1"},
		//	{"2","1","2"},
		//	{"5",null,null} } },
		{ "select c10 from t1 intersect select vc20 from t2 order by 1", new String[][] { {"a"},{"zz"} } },
		{ "select c30 from t1 intersect select vc20 from t2", new String[][] { {"a"},{"bb"} } },
		{ "select c30 from t1 intersect all select vc20 from t2", new String[][] { {"a"},{"bb"} } },
		{ "create table r( i1 integer, i2 integer)", null },
		// FIXME
		// This inserts the wrong number of rows in partitioned tables
		//{ "insert into r select i1,i2 from t1 intersect select i1,i2 from t2", null },
		//{ "select i1,i2 from r order by 1,2", new String[][] {
		//	{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		//{ "delete from r", null },
		//{ "insert into r select i1,i2 from t1 intersect all select i1,i2 from t2", null },
		//{ "select i1,i2 from r order by 1,2", new String[][] {
		//	{"1","1"},{"1","2"},{"1","3"},{"1","3"},{null,null} } },
		{ "delete from r", null },
		{ "create table t3( i1 integer, cl clob(64), bl blob(1M))", null },
		{ "insert into t3 values  (1, cast( 'aa' as clob(64)), cast(X'01' as blob(1M)))", null },
		{ "create table t4( i1 integer, cl clob(64), bl blob(1M))", null },
		{ "insert into t4 values  (1, cast( 'aa' as clob(64)), cast(X'01' as blob(1M)))", null },
		{ "select cl from t3 intersect select cl from t4 order by 1", "X0X67" },
		{ "select bl from t3 intersect select bl from t4 order by 1", "X0X67" },
		{ "select tm from t1 intersect select dt from t2", "42X61" },
		{ "select c30 from t1 intersect select d from t2", "42X61" },
		{ "select i1 from t1 intersect select i1,i2 from t2", "42X58" },
		//original
		//{ "select ? from t1 intersect select i1 from t2", "42X34" },
		{ "select ? from t1 intersect select i1 from t2", "07000" },
		//original
		//{ "select i1 from t1 intersect select ? from t2", "42X34" },
		{ "select i1 from t1 intersect select ? from t2", "07000" },
		{ "select id,i1,i2 from t1 except select id,i1,i2 from t2 order by id,i1,i2", new String[][] {
			{"3","1","3"},{"4","1","3"},{"6",null,null} } },
		{ "select id,i1,i2 from t1 except distinct select id,i1,i2 from t2 order by id,i1,i2", new String[][] {
			{"3","1","3"},{"4","1","3"},{"6",null,null} } },
		{ "select id,i1,i2 from t1 except all select id,i1,i2 from t2 order by 1,2,3", new String[][] {
			{"6",null,null},{"4","1","3"},{"3","1","3"}, } },
		{ "select id,i1,i2 from t2 except select id,i1,i2 from t1 order by 1,2,3", new String [][] {
			{"100","1","3"}, {"101","1","2"},{"102","5","5"},{"103","1","3"}, {"104","1","3"} } },
		{ "select id,i1,i2 from t2 except all select id,i1,i2 from t1 order by 1,2,3", new String[][] {
			{"100","1","3"}, {"101","1","2"},{"102","5","5"},{"103","1","3"}, {"104","1","3"} } },
		{ "select i1,i2 from t1 except select i1,i2 from t2 order by 1,2", new String[0][0] },
		{ "select i1,i2 from t1 except distinct select i1,i2 from t2 order by 1,2", new String[0][0] },
		{ "select i1,i2 from t1 except all select i1,i2 from t2 order by 1,2", new String[][] { {null,null} } },
		{ "select i1,i2 from t2 except select i1,i2 from t1 order by 1,2", new String[][] { {"5","5"} } },
		{ "select i1,i2 from t2 except all select i1,i2 from t1 order by 1,2", new String[][] {
			{"1","2"},{"1","3"},{"5","5"} } },
		{ "select i1,i2 from t1 except select i1,i2 from t2 where id = -1 order by 1,2", new String[][] {
			{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		{ "select i1,i2 from t1 except all select i1,i2 from t2 where id = -1  order by 1,2", new String[][] {
			{"1","1"},{"1","2"},{"1","3"},{"1","3"},{null,null},{null,null} } },
		{ "select i1,i2 from t1 where id = -1 except select i1,i2 from t2 order by 1,2", new String[0][0] },
		{ "select i1,i2 from t1 where id = -1 except all select i1,i2 from t2 order by 1,2", new String[0][0] },
		// FIXME
		// Nested set operations on partitioned tables not supported in GemFireXD
		//{ "select i1,i2 from t1 except select i1,i2 from t2 intersect values(-1,-1) order by 1,2", new String[][] {
		//	{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		//{ "select i1,i2 from t1 except (select i1,i2 from t2 intersect values(-1,-1)) order by 1,2", new String[][] {
		//	{"1","1"},{"1","2"},{"1","3"},{null,null} } },
		//{ "select i1,i2 from t2 except select i1,i2 from t1 union values(5,5) order by 1,2", new String[][] { {"5","5"} } },
		//{ "(select i1,i2 from t2 except select i1,i2 from t1) union values(5,5) order by 1,2", new String[][] { {"5","5"} } },
		//{ "select i1,i2 from t2 except all select i1,i2 from t1 except select i1,i2 from t1 where id = 3 order by 1,2", new String[][] {
		//	{"1","2"},{"5","5"} } },
		//{ "(select i1,i2 from t2 except all select i1,i2 from t1) except select i1,i2 from t1 where id = 3 order by 1,2", new String[][] {
		//	{"1","2"},{"5","5"} } },
		{ "select c10 from t1 except select vc20 from t2 order by 1", new String[][] { {"b"},{null} } },
		{ "select c30 from t1 except select vc20 from t2 order by 1", new String[][] {
			{"1.0"},{"123456789012345678901234567890"},{"5"} } },
		{ "select c30 from t1 except all select vc20 from t2", new String[][] {
			{"1.0"},{"123456789012345678901234567890"},{"5"},{"bb"} } },
		// FIXME
		// This does not insert the correct number of rows in a partitioned region
		//{ "insert into r select i1,i2 from t2 except select i1,i2 from t1", null },
		//{ "select i1,i2 from r order by 1,2", new String[][] { {"5","5"} } },
		{ "delete from r", null },
		//{ "insert into r select i1,i2 from t2 except all select i1,i2 from t1", null },
		//{ "select i1,i2 from r order by 1,2", new String[][] { {"1","2"},{"1","3"},{"5","5"} } },
		{ "delete from r", null },
		// TODO : GemFireXD allows LONG VARCHAR as order by, try this in EXCEPT like LOBs below
		{ "select cl from t3 except select cl from t4 order by 1", "X0X67" },
		{ "select bl from t3 except select bl from t4 order by 1", "X0X67" },
		{ "select tm from t1 except select dt from t2", "42X61" },
		{ "select c30 from t1 except select d from t2", "42X61" },
		{ "select i1 from t1 except select i1,i2 from t2", "42X58" },
		{ "select id,i1,i2 from t1 intersect select id,i1,i2 from t2 order by t1.i1", "42877" },
		{ "select id,i1,i2 from t1 except select id,i1,i2 from t2 order by t1.i1", "42877" },
		{ "create view view_intr_uniq as select id,i1,i2 from t1 intersect select id,i1,i2 from t2", null },
		{ "select * from view_intr_uniq order by 1,2,3", new String[][] {
			{"5",null,null},{"2","1","2"},{"1","1","1"} } },
		{ "create view view_intr_all as select id,i1,i2 from t1 intersect all select id,i1,i2 from t2", null },
		{ "select * from  view_intr_all order by 1,2,3", new String[][] {
			{"1","1","1"},{"2","1","2"},{"5",null,null} } },
		{ "create view view_ex_uniq as select id,i1,i2 from t1 except select id,i1,i2 from t2", null },
		{ "select * from view_ex_uniq order by 1,2,3", new String[][] {
			{"3","1","3"},{"4","1","3"},{"6",null,null} } },
		{ "create view view_ex_all as select id,i1,i2 from t1 except all select id,i1,i2 from t2", null },
		{ "select * from view_ex_all order by 1,2,3", new String[][] {
			{"6",null,null},{"4","1","3"},{"3","1","3"} } },
		// FIXME
		// This throws 42X77 column position 2 invalid, this is an ORDER BY failure but there is no ORDER BY clause here
		// Investigate
		//{ "select t1.id,t1.i1,t2.i1 from t1 join t2 on t1.id = t2.id intersect select t1.id,t1.i2,t2.i2 from t1 join t2 on t1.id = t2.id", new String[][] {
		//	{"1","1","1"},{"5",null,null} } },
		{ "drop view view_intr_uniq", null },
		{ "drop view view_intr_all", null },
		{ "drop view view_ex_uniq", null },
		{ "drop view view_ex_all", null },
		{ "drop table t2", null },
		{ "drop table t1", null }
	    };

	    // Start 1 client and 3 servers, use default partitioning
	    startVMs(1, 3);


	    Connection conn = TestUtil.getConnection();
	    Statement stmt = conn.createStatement();
	    // Go through the array, execute each string[0], check sqlstate [1]
	    // This will fail on the first one that succeeds where it shouldn't
	    // or throws unknown exception
	    JDBC.SQLUnitTestHelper(stmt,Script_IntersectUT);
	  }

}
