/*
 *
 * Derby - Class UpdatableResultSetTest
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific 
 * language governing permissions and limitations under the License.
 */
package org.apache.derbyTesting.functionTests.tests.jdbcapi;
import org.apache.derbyTesting.functionTests.util.TestUtil;
import org.apache.derbyTesting.junit.BaseJDBCTestCase;
import org.apache.derbyTesting.junit.CleanDatabaseTestSetup;
import org.apache.derbyTesting.junit.JDBC;
import org.apache.derbyTesting.junit.TestConfiguration;

import junit.framework.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.io.*;

/**
 * Tests updatable result sets.
 *
 * DERBY-1767 - Test that the deleteRow, insertRow and updateRow methods 
 * with column/table/schema/cursor names containing quotes.
 *
 */
public class UpdatableResultSetTest extends BaseJDBCTestCase {
    
    private static final byte[] BYTES1 = {
            0x65, 0x66, 0x67, 0x68, 0x69,
            0x69, 0x68, 0x67, 0x66, 0x65
        };

    private static final byte[] BYTES2 = {
            0x69, 0x68, 0x67, 0x66, 0x65,
            0x65, 0x66, 0x67, 0x68, 0x69
        };

    /**
     * Key used to identify inserted rows.
     * Use method <code>requestKey</code> to obtain it.
     **/
    private static int insertKey = 0;

    private int key = -1;

    /** Creates a new instance of UpdatableResultSetTest */
    public UpdatableResultSetTest(String name) {
        super(name);
    }
    
    /**
     * Create require objects and data. No tearDown
     * is needed because these are created in non-auto-commit
     * mode and no commit is ever issued. Thus on the super's
     * tearDown the rollback will revert everything.
     * // GemStone changes BEGIN
     * except in GemFireXD, DDL statements cannot be rolled back
     * when in isolation level NONE,
     * so added a tearDown
     * // GemStone changes END
     */
    protected void setUp() throws SQLException {
        Connection conn = getConnection();
        //conn.setAutoCommit(false);
        Statement stmt = conn.createStatement();
        
        stmt.execute("create table UpdateTestTableResultSet (" +
                            "sno int not null unique," +
                            "dBlob BLOB," +
                            "dClob CLOB," +
                            "dLongVarchar LONG VARCHAR," +
                            "dLongBit LONG VARCHAR FOR BIT DATA)");

        // Quoted table
        stmt.executeUpdate("create table \"my \"\"quoted\"\" table\" (x int)");
        stmt.executeUpdate("insert into \"my \"\"quoted\"\" table\" (x) " +
                "values (1), (2), (3)");
        
        // Quoted columns
        stmt.executeUpdate("create table \"my quoted columns\" " +
                "(\"my \"\"quoted\"\" column\" int)");
        stmt.executeUpdate("insert into \"my quoted columns\" " +
                "values (1), (2), (3) ");
        
        // Quoted schema
        stmt.executeUpdate("create table \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\" (x int)");
        stmt.executeUpdate("insert into \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\" values (1), (2), (3) ");
        
        // No quotes, use with quoted cursor
        stmt.executeUpdate("create table \"my table\" (x int)");
        stmt.executeUpdate("insert into \"my table\" values (1), (2), (3) ");
        
        stmt.close();
    }
     
  // GemStone changes BEGIN
  // we need to clean the database as part of tear down since
  // DDL is non-transactional in GemFireXD when in isolation level NONE
  protected void tearDown() throws java.lang.Exception {
    // !!ezoerner:20100127 This commented-out code is
    // throwing an authorization error when it tries to
    // execute system procedures for unknown reason.
    Connection conn = getConnection();
    // since DDL is currently non-transactional, we need
    // to explicitly clean the database of tables, etc.
    //if (conn.getTransactionIsolation() == Connection.TRANSACTION_NONE) {
      CleanDatabaseTestSetup.cleanDatabase(conn, false);
    //}
    super.tearDown();
    // also destroy persistent data dictionary files
    deleteDir(new File("datadictionary"));
    /*[sb] part of fix #41537 re-enabling above code.
    // instead just shutdown the distributed system destroying everything
    if (getConnection().getTransactionIsolation() == Connection.TRANSACTION_NONE) {
      super.tearDown(); // this closes all connections
      // destroy everything ...
      // unfortunately this won't work in distributed environment,
      // will need to get the above call to cleanDatabase working instead
      getTestConfiguration().shutdownEngine(); 
      // also destroy persistent data dictionary files
      deleteDir(new File("datadictionary"));
    }
    */
  }

  public static void deleteDir(File dir) {
    if (!dir.exists()) return;
    for (File f : dir.listFiles()) {
      if (f.isFile()) {
        f.delete();
      }
      else if (f.isDirectory()) {
        deleteDir(f);
      }
    }
  }

  // GemStone changes END
    
    /** Create a test suite with all tests in this class. */
    public static Test suite() {

        // Test will fail with JCC.
        if (usingDB2Client()) {
            // empty suite
            return new TestSuite();
        }

// GemStone changes BEGIN
        // updatable resultsets not yet implemented for clients
        return TestConfiguration.embeddedSuite(UpdatableResultSetTest.class);
        /* (original code)
        return TestConfiguration.defaultSuite(UpdatableResultSetTest.class);
        */
// GemStone changes END
    }
    
    /**
     * Tests insertRow with table name containing quotes
     */
    public void testInsertRowOnQuotedTable() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\"");
        rs.next();
        rs.moveToInsertRow();
        rs.updateInt(1, 4);
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\" " +
                "order by x");
        for (int i=1; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertEquals("row contains correct value", i, rs.getInt(1));
        }
        rs.close();
        stmt.close();
    }

    /**
     * Tests updateRow with table name containing quotes
     */
    public void testUpdateRowOnQuotedTable() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which int was updated
        int updatedInt = rs.getInt(1);
        // GemStone changes END
        rs.updateInt(1, 4);
        rs.updateRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\" " +
                "order by x");
        
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)updatedInt);
        expected.add(4);
        
        for (int i=2; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
        		     expected.remove((Integer)rs.getInt(1)));
         }
         assertTrue("table correct size", expected.isEmpty());
         // GemStone changes END        
        rs.close();
        stmt.close();        
    }

    /**
     * Tests deleteRow with table name containing quotes
     */
    public void testDeleteRowOnQuotedTable() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which x was deleted
        int deletedX = rs.getInt(1);
        // GemStone changes END
        
        rs.deleteRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" table\" " +
                "order by x");
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)deletedX);
 
        for (int i=2; i<=3; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
            		     expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END
        rs.close();
        stmt.close();
    }

    /**
     * Tests insertRow with column name containing quotes
     */    
    public void testInsertRowOnQuotedColumn() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my quoted columns\"");
        rs.next();
        rs.moveToInsertRow();
        rs.updateInt(1, 4);
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my quoted columns\" " +
                "order by \"my \"\"quoted\"\" column\"");
        for (int i=1; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertEquals("row contains correct value", i, rs.getInt(1));
        }
        rs.close();
        stmt.close();
    }

    /**
     * Tests updateRow with column name containing quotes
     */    
    public void testUpdateRowOnQuotedColumn() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my quoted columns\"");
// GemStone changes BEGIN
        // don't assume any order so need to check all rows
        while (rs.next()) {
          if (rs.getInt(1) == 1) {
            rs.updateInt(1, 4);
            rs.updateRow();
            break;
          }
        }
        /* (original code)
        rs.next();
        rs.updateInt(1, 4);
        rs.updateRow();
        */
// GemStone changes END
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my quoted columns\" " +
                "order by \"my \"\"quoted\"\" column\"");
        for (int i=2; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertEquals("row contains correct value", i, rs.getInt(1));
        }
        rs.close();
        stmt.close();        
    }

    /**
     * Tests deleteRow with column name containing quotes
     */    
    public void testDeleteRowOnQuotedColumn() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my quoted columns\"");
        rs.next();
        
        // GemStone changes BEGIN
        // remember which row was deleted
        int deletedInt = rs.getInt(1);
        // GemStone changes END
        
        rs.deleteRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my quoted columns\" " +
                "order by \"my \"\"quoted\"\" column\"");        
               
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)deletedInt);
 
        for (int i=2; i<=3; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
            		     expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END        
        
        rs.close();
        stmt.close();                
    }

    /**
     * Tests insertRow with schema name containing quotes
     */    
    public void testInsertRowOnQuotedSchema() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\"");
        rs.next();
        rs.moveToInsertRow();
        rs.updateInt(1, 4);
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\" order by x");
        for (int i=1; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertEquals("row contains correct value", i, rs.getInt(1));
        }
        rs.close();
        stmt.close();
    }

    /**
     * Tests updateRow with schema name containing quotes
     */    
    public void testUpdateRowOnQuotedSchema() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which int was updated
        int updatedInt = rs.getInt(1);
        // GemStone changes END
        rs.updateInt(1, 4);
        rs.updateRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\" order by x");
        
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)updatedInt);
        expected.add(4);
        
        for (int i=2; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
       		     expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END        
        
        rs.close();
        stmt.close();        
    }

    /**
     * Tests deleteRow with schema name containing quotes
     */    
    public void testDeleteRowOnQuotedSchema() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which row was deleted
        int deletedInt = rs.getInt(1);
        // GemStone changes END
        rs.deleteRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my \"\"quoted\"\" schema\"." +
                "\"my quoted schema\" order by x");
        
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)deletedInt);

        for (int i=2; i<=3; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
       		           expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END        

        rs.close();
        stmt.close();                
    }

    /**
     * Tests insertRow with cursor name containing quotes
     */    
    public void testInsertRowOnQuotedCursor() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        stmt.setCursorName("my \"\"\"\"quoted\"\"\"\" cursor\"\"");
        rs = stmt.executeQuery("select * from \"my table\"");
        rs.next();
        rs.moveToInsertRow();
        rs.updateInt(1, 4);
        rs.insertRow();
        rs.moveToCurrentRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my table\" order by x");
        for (int i=1; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertEquals("row contains correct value", i, rs.getInt(1));
        }
        rs.close();
        stmt.close();
    }

    /**
     * Tests updateRow with cursor name containing quotes
     */    
    public void testUpdateRowOnQuotedCursor() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        stmt.setCursorName("\"\"my quoted cursor");
        rs = stmt.executeQuery("select * from \"my table\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which int was updated
        int updatedInt = rs.getInt(1);
        // GemStone changes END
        rs.updateInt(1, 4);
        rs.updateRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my table\" order by x");
        
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was updated
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)updatedInt);
        expected.add(4);
        
        for (int i=2; i<=4; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
       		     expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END 
        
        rs.close();
        stmt.close();        
    }

    /**
     * Tests deleteRow with cursor name containing quotes
     */    
    public void testDeleteRowOnQuotedCursor() throws SQLException {
        ResultSet rs = null;
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY, 
                ResultSet.CONCUR_UPDATABLE);
        stmt.setCursorName("\"\"my quoted cursor\"\"");
        rs = stmt.executeQuery("select * from \"my table\"");
        rs.next();
        // GemStone changes BEGIN
        // remember which row was deleted
        int deletedInt = rs.getInt(1);
        // GemStone changes END
        rs.deleteRow();
        rs.close();
        
        rs = stmt.executeQuery("select * from \"my table\" order by x");
        // GemStone changes BEGIN
        // in GemFireXD, queries are not guaranteed to return results
        // in the same order they were inserted, so changing this test
        // to not assume which x was deleted
        List<Integer> expected = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        expected.remove((Integer)deletedInt);
        
        for (int i=2; i<=3; i++) {
            assertTrue("there is a row", rs.next());
            assertTrue("row contains correct value",
       		           expected.remove((Integer)rs.getInt(1)));
        }
        assertTrue("table correct size", expected.isEmpty());
        // GemStone changes END        

        rs.close();
        stmt.close();                
    }

    /**
     * This methods tests the ResultSet interface method
     * updateBlob
     *
     * @throws Exception
     */
    public void testUpdateBlob()
    throws Exception {
        //Byte array in which the returned bytes from
        //the Database after the update are stored. This
        //array is then checked to determine if it
        //has the same elements of the Byte array used for
        //the update operation

        byte[] bytes_ret = new byte[10];

        //1 Input Stream for insertion
        InputStream is1 = new java.io.ByteArrayInputStream(BYTES1);

        //2 Input Stream for insertion
        InputStream is2 = new java.io.ByteArrayInputStream(BYTES2);

        //Prepared Statement used to insert the data
        PreparedStatement ps_sb = prep("dBlob");

        //first insert
        ps_sb.setInt(1, key);
        ps_sb.setBinaryStream(2,is1,BYTES1.length);
        ps_sb.executeUpdate();

        //second insert
        int key2 = requestKey();
        ps_sb.setInt(1, key2);
        ps_sb.setBinaryStream(2,is2,BYTES2.length);
        ps_sb.executeUpdate();

        ps_sb.close();

        //Update operation
        //use a different ResultSet variable so that the
        //other tests can go on unimpacted
        //we do not have set methods on Clob and Blob implemented
        //So query the first Clob from the database
        //update the second result set with this
        //Clob value

        ResultSet rs1 = fetch("dBlob", key);
        rs1.next();
        Blob blob = rs1.getBlob(1);
        rs1.close();

        rs1 = fetchUpd("dBlob", key2);
        rs1.next();
        rs1.updateBlob(1,blob);
        rs1.updateRow();
        rs1.close();

        //Query to see whether the data that has been updated
        //using the updateBlob method is the same
        //data that we expected

        rs1 = fetch("dBlob", key2);
        rs1.next();
        assertEquals(blob, rs1.getBlob(1));
        rs1.close();
    }

    /**
     * This methods tests the ResultSet interface method
     * updateBlob
     *
     * @throws Exception
     */
    public void testUpdateBlobStringParameterName()
    throws Exception {
        //Byte array in which the returned bytes from
        //the Database after the update are stored. This
        //array is then checked to determine if it
        //has the same elements of the Byte array used for
        //the update operation

        byte[] bytes_ret = new byte[10];

        //1 Input Stream for insertion
        InputStream is1 = new java.io.ByteArrayInputStream(BYTES1);

        //2 Input Stream for insertion
        InputStream is2 = new java.io.ByteArrayInputStream(BYTES2);

        //Prepared Statement used to insert the data
        PreparedStatement ps_sb = prep("dBlob");

        //first insert
        ps_sb.setInt(1, key);
        ps_sb.setBinaryStream(2,is1,BYTES1.length);
        ps_sb.executeUpdate();

        //second insert
        int key2 = requestKey();
        ps_sb.setInt(1, key2);
        ps_sb.setBinaryStream(2,is2,BYTES2.length);
        ps_sb.executeUpdate();

        ps_sb.close();

        //Update operation
        //use a different ResultSet variable so that the
        //other tests can go on unimpacted
        //we do not have set methods on Clob and Blob implemented
        //So query the first Clob from the database
        //update the second result set with this
        //Clob value

        ResultSet rs1 = fetch("dBlob", key);
        rs1.next();
        Blob blob = rs1.getBlob(1);
        rs1.close();

        rs1 = fetchUpd("dBlob", key2);
        rs1.next();
        rs1.updateBlob("dBlob",blob);
        rs1.updateRow();
        rs1.close();

        //Query to see whether the data that has been updated
        //using the updateBlob method is the same
        //data that we expected

        rs1 = fetch("dBlob", key2);
        rs1.next();
        assertEquals(blob, rs1.getBlob(1));
        rs1.close();
    }

    /**
     * This methods tests the ResultSet interface method
     * updateClob
     *
     * @throws Exception
     */
    public void testUpdateClob()
    throws Exception {
        //Byte array in which the returned bytes from
        //the Database after the update are stored. This
        //array is then checked to determine if it
        //has the same elements of the Byte array used for
        //the update operation

        byte[] bytes_ret = new byte[10];

        //1 Input Stream for insertion
        InputStream is1 = new java.io.ByteArrayInputStream(BYTES1);

        //2 Input Stream for insertion
        InputStream is2 = new java.io.ByteArrayInputStream(BYTES2);

        //Prepared Statement used to insert the data
        PreparedStatement ps_sb = prep("dClob");

        //first insert
        ps_sb.setInt(1,key);
        ps_sb.setAsciiStream(2,is1,BYTES1.length);
        ps_sb.executeUpdate();

        //second insert
        int key2 = requestKey();
        ps_sb.setInt(1,key2);
        ps_sb.setAsciiStream(2,is2,BYTES2.length);
        ps_sb.executeUpdate();

        ps_sb.close();

        //Update operation
        //use a different ResultSet variable so that the
        //other tests can go on unimpacted
        //we do not have set methods on Clob and Blob implemented
        //So query the first Clob from the database
        //update the second result set with this
        //Clob value

        ResultSet rs1 = fetchUpd("dClob", key);
        rs1.next();
        Clob clob = rs1.getClob(1);
        rs1.close();

        rs1 = fetchUpd("dClob", key2);
        rs1.next();
        rs1.updateClob(1,clob);
        rs1.updateRow();
        rs1.close();

        //Query to see whether the data that has been updated
        //using the updateClob method is the same
        //data that we expected

        rs1 = fetch("dClob", key2);
        rs1.next();
        assertEquals(clob, rs1.getClob(1));
        rs1.close();
    }

    /**
     * This methods tests the ResultSet interface method
     * updateClob
     *
     * @throws Exception
     */
    public void testUpdateClobStringParameterName()
    throws Exception {
        //Byte array in which the returned bytes from
        //the Database after the update are stored. This
        //array is then checked to determine if it
        //has the same elements of the Byte array used for
        //the update operation

        byte[] bytes_ret = new byte[10];

        //1 Input Stream for insertion
        InputStream is1 = new java.io.ByteArrayInputStream(BYTES1);

        //2 Input Stream for insertion
        InputStream is2 = new java.io.ByteArrayInputStream(BYTES2);

        //Prepared Statement used to insert the data
        PreparedStatement ps_sb = prep("dClob");

        //first insert
        ps_sb.setInt(1, key);
        ps_sb.setAsciiStream(2,is1,BYTES1.length);
        ps_sb.executeUpdate();

        //second insert
        int key2 = requestKey();
        ps_sb.setInt(1, key2);
        ps_sb.setAsciiStream(2,is2,BYTES2.length);
        ps_sb.executeUpdate();

        ps_sb.close();

        //Update operation
        //use a different ResultSet variable so that the
        //other tests can go on unimpacted
        //we do not have set methods on Clob and Blob implemented
        //So query the first Clob from the database
        //update the second result set with this
        //Clob value

        ResultSet rs1 = fetch("dClob", key);
        rs1.next();
        Clob clob = rs1.getClob(1);
        rs1.close();

        rs1 = fetchUpd("dClob", key2);
        rs1.next();
        rs1.updateClob("dClob",clob);
        rs1.updateRow();
        rs1.close();

        //Query to see whether the data that has been updated
        //using the updateClob method is the same
        //data that we expected

        rs1 = fetch("dClob", key2);
        rs1.next();
        assertEquals(clob, rs1.getClob(1));
        rs1.close();
    }

    /**
     * Get a key that is used to identify an inserted row.
     * Introduced to avoid having to delete table contents after each test,
     * and because the order of the tests is not guaranteed.
     *
     * @return an integer in range [1, Integer.MAX_VALUE -1]
     */
    private static final int requestKey() {
        return ++insertKey;
    }

    /**
     * Prepare commonly used statement to insert a row.
     *
     * @param colName name of the column to insert into
     * @throws SQLException
     */
    private PreparedStatement prep(String colName)
            throws SQLException {
        return prepareStatement("insert into UpdateTestTableResultSet " +
                "(sno, " + colName + ") values (?,?)");
    }

    /**
     * Fetch the specified row for update.
     *
     * @param colName name of the column to fetch
     * @param key identifier for row to fetch
     * @return a <code>ResultSet</code> with zero or one row, depending on
     *      the key used
     * @throws SQLException
     */
    private ResultSet fetchUpd(String colName, int key)
            throws SQLException {
        Statement stmt = createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                             ResultSet.CONCUR_UPDATABLE);
        return stmt.executeQuery("select " + colName +
                " from UpdateTestTableResultSet where sno = " + key +
                " for update");
    }

    /**
     * Fetch the specified row.
     *
     * @param colName name of the column to fetch
     * @param key identifier for row to fetch
     * @return a <code>ResultSet</code> with zero or one row, depending on
     *      the key used
     * @throws SQLException
     */
    private ResultSet fetch(String colName, int key)
            throws SQLException {
        Statement stmt = createStatement();
        return stmt.executeQuery("select " + colName +
                " from UpdateTestTableResultSet where sno = " + key);
    }
}
